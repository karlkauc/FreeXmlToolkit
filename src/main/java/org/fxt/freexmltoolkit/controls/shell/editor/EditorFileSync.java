package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.scene.control.Tab;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.BoundSchemaWatch;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.ExitChoice;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * Files on disk behind an {@link EditorHost}'s tabs: reading a document into its tab (large-file
 * guard, encoding, schema auto-detection on open), saving (Save, Save As, Save All, the
 * close/exit prompts for unsaved documents, the UTF-8 fallback) and the external-change
 * handling of {@link ExternalChangeMonitor} (reload, deleted files, changed schema
 * dependencies). Every editor write goes through {@link #write(EditorTab, Path)} so the disk
 * stamp follows. File I/O runs off the FX thread; results are applied on it.
 */
final class EditorFileSync {

    private final EditorHost host;

    /** Watches the open documents' files for changes made by other programs. */
    final ExternalChangeMonitor externalChangeMonitor = new ExternalChangeMonitor(
            this::watchedDocuments, this::handleExternalChanges,
            task -> org.fxt.freexmltoolkit.FxtGui.executorService.execute(task));

    /** Asks what to do about an externally changed/deleted file; replaceable in tests. */
    private ExternalChangePrompt externalChangePrompt;

    EditorFileSync(EditorHost host) {
        this.host = host;
        this.externalChangePrompt =
                ExternalChangePrompt.dialogs(() -> host.getScene() != null ? host.getScene().getWindow() : null);
    }

    /**
     * Saves the active document to its current path (must be titled). The file is written on a worker
     * thread (a large file on a network drive blocked the UI for seconds).
     *
     * @return completes on the FX thread with whether the file was written
     */
    public CompletableFuture<Boolean> saveActive() {
        return host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                ? saveTab(et) : CompletableFuture.completedFuture(false);
    }

    /** Saves {@code et} to its current path (must be titled), written on a worker thread. */
    CompletableFuture<Boolean> saveTab(EditorTab et) {
        if (et.document.isUntitled()) {
            return CompletableFuture.completedFuture(false);
        }
        return writeAsync(et, et.document.getPath()).thenApply(saved -> {
            if (saved) {
                UsageEvents.fileSaved(et.document.getFileType().docKind(), 1);
            }
            return saved;
        });
    }

    /**
     * Saves the active document to {@code target} (Save As), written on a worker thread.
     *
     * @return completes on the FX thread with whether the file was written
     */
    public CompletableFuture<Boolean> saveActiveAs(Path target) {
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return CompletableFuture.completedFuture(false);
        }
        Path normalized = withTypeExtension(et, target);
        if (normalized == null) {
            return CompletableFuture.completedFuture(false);
        }
        return writeAsync(et, relocateGeneratedSchemaReference(et, normalized)).thenApply(saved -> {
            if (saved) {
                et.document.setPath(normalized);
                et.refreshIcon();
                host.debugSupport.installBreakpointGutter(et);
                EditorHost.rememberRecentFile(normalized);
                UsageEvents.fileSaved(et.document.getFileType().docKind(), 1);
            }
            return saved;
        });
    }

    /**
     * Saves all titled, dirty documents, written on worker threads.
     *
     * @return completes on the FX thread with the number of files written
     */
    public CompletableFuture<Integer> saveAll() {
        java.util.List<CompletableFuture<Boolean>> writes = new java.util.ArrayList<>();
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && !et.document.isUntitled() && et.document.isDirty()) {
                writes.add(writeAsync(et, et.document.getPath()));
            }
        }
        CompletableFuture<Integer> result = new CompletableFuture<>();
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            int saved = (int) writes.stream().filter(w -> Boolean.TRUE.equals(w.getNow(false))).count();
            UsageEvents.fileSaved(null, saved);
            result.complete(saved);
        });
        return result;
    }

    void loadAsync(EditorTab tab, Path path) {
        long openStart = System.nanoTime();
        tab.loading = true;
        tab.beginLoading();
        tab.schemaBindingGen.incrementAndGet(); // supersede queued schema reconciles
        if (tab.view.supportsSchema()) {
            host.publishSchemaStatus(tab, SchemaStatus.LOADING);
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long size = sizeOrUnknown(path); // a stat can stall: here, not on the FX thread
            long maxHeap = Runtime.getRuntime().maxMemory();
            switch (LargeFileGuard.check(size, maxHeap)) {
                case OK -> readAndShow(tab, path, openStart);
                case WARN -> Platform.runLater(() -> {
                    if (EditorTab.isAbandoned(tab)) {
                        return;
                    }
                    switch (askLargeFile(path, size, maxHeap, false)) {
                        case EDITOR -> org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> readAndShow(tab, path, openStart));
                        case VIEWER -> {
                            host.closeTabNow(tab);
                            host.openLargeFileViewer(path);
                        }
                        default -> {
                            UsageEvents.fileOpenDeclined(tab.document.getFileType().docKind(), size, false);
                            host.closeTabNow(tab);
                        }
                    }
                });
                case REFUSE -> Platform.runLater(() -> {
                    host.closeTabNow(tab);
                    if (askLargeFile(path, size, maxHeap, true) == LargeFileChoice.VIEWER) {
                        host.openLargeFileViewer(path);
                    } else {
                        UsageEvents.fileOpenDeclined(tab.document.getFileType().docKind(), size, true);
                    }
                });
                default -> { }
            }
        });
    }

    /** How to open a large file. */
    enum LargeFileChoice { EDITOR, VIEWER, CANCEL }

    /** Test hook: answers the large-file question (path, too large for the editor) instead of a dialog. */
    static java.util.function.BiFunction<Path, Boolean, LargeFileChoice> largeFileAnswer;

    /**
     * Asks how to open a large file: in the editor (only when it fits the memory), in the read-only
     * Large File Viewer, or not at all. Runs on the FX thread.
     */
    private LargeFileChoice askLargeFile(Path path, long size, long maxHeap, boolean tooLarge) {
        if (largeFileAnswer != null) {
            return largeFileAnswer.apply(path, tooLarge);
        }
        String header = path.getFileName() + " is " + LargeFileGuard.formatSize(size);
        String content = tooLarge
                ? "With the current memory limit of " + LargeFileGuard.formatSize(maxHeap) + ", the editor opens files "
                        + "up to about " + LargeFileGuard.formatSize(LargeFileGuard.largestOpenable(maxHeap))
                        + " (raise it under Settings > Memory).\n\nThe Large File Viewer shows files of any size, "
                        + "read-only: scroll, go to line, find, copy."
                : "In the editor, large files open slowly and make editing sluggish; syntax highlighting, validation "
                        + "and the structured views may take a long time.\n\nThe Large File Viewer shows the file at once, "
                        + "read-only: scroll, go to line, find, copy.";
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Open Large File", header, content);
        javafx.scene.control.ButtonType viewer = new javafx.scene.control.ButtonType("Open in Viewer",
                javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType editor = new javafx.scene.control.ButtonType("Open in Editor",
                javafx.scene.control.ButtonBar.ButtonData.OTHER);
        javafx.scene.control.ButtonType cancel = javafx.scene.control.ButtonType.CANCEL;
        if (tooLarge) {
            alert.getButtonTypes().setAll(viewer, cancel);
        } else {
            alert.getButtonTypes().setAll(viewer, editor, cancel);
        }
        var answer = alert.showAndWait().orElse(cancel);
        return answer == viewer ? LargeFileChoice.VIEWER : answer == editor ? LargeFileChoice.EDITOR : LargeFileChoice.CANCEL;
    }

    /** Reads {@code path} on the calling worker thread and shows it in {@code tab}. */
    private void readAndShow(EditorTab tab, Path path, long openStart) {
        // Stamp taken BEFORE the read: a change landing mid-read is then caught by the
        // next external-change check instead of being silently missed.
        DiskStamp stamp = DiskStamp.of(path);
        String content;
        TextEncoding encoding;
        try {
            TextEncoding.Read read = TextEncoding.read(path);
            content = read.text();
            encoding = read.encoding();
        } catch (IOException | RuntimeException e) {
            // Counted (file_open with status + code: a missing / unreadable file is invalid_input,
            // anything else an app failure) and told to the user; the tab shows the reason.
            UsageEvents.fileOpenFailed(tab.document.getFileType().docKind(), sizeOrUnknown(path),
                    UsageEvents.SOURCE_FILE, org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e));
            Platform.runLater(() -> {
                tab.view.setText(I18n.t("EditorFileSync.could-not-read", path, e.getMessage()));
                tab.endLoading();
                tab.document.setDiskStamp(stamp);
                tab.loading = false;
                host.publishSchemaStatus(tab, SchemaStatus.NONE);
                org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorFileSync.open-failed"),
                        I18n.t("EditorFileSync.could-not-read-2", path.getFileName()),
                        I18n.t("EditorFileSync.check-that-the-file-exists"), e, false);
            });
            return;
        }

        long fileSize = sizeOrUnknown(path); // here, not in the FX pulse below: a stat can stall
        // Also here: a linear scan of the text (XML family only, -1 otherwise)
        int elementCount = UsageEvents.elementCount(tab.document.getFileType().docKind(), content);
        // And the editor document of a large file (paragraph split + tree), seconds on the FX thread
        var prepared = tab.view.prepareText(content);
        // Show the document as soon as it has been read. Schema detection below can take
        // seconds - a remote xsi:schemaLocation (or a namespace the Schema Library resolves
        // remotely) is downloaded - and the editor must not sit empty for that long. The
        // schema has its own status, already published as LOADING above, so the pending
        // binding stays visible to the user.
        Platform.runLater(() -> {
            if (EditorTab.isAbandoned(tab)) {
                return;
            }
            if (prepared != null) {
                tab.view.setText(prepared);
            } else {
                tab.view.setText(content);
                // Loading is no edit: Ctrl+Z right after opening emptied the editor
                tab.view.getCodeArea().getUndoManager().forgetHistory();
            }
            tab.refreshPreviewIfActive();
            tab.endLoading();
            tab.document.setEncoding(encoding);
            tab.document.setDirty(false);
            tab.document.setDiskStamp(stamp);
            tab.loading = false;
            tab.attachDirtyTracking();
            UsageEvents.fileOpened(tab.document.getFileType().docKind(), fileSize,
                    UsageEvents.SOURCE_FILE, openStart, elementCount, encoding.charset().name());
            if (tab.document.getFileType().isXmlFamily()) {
                GridWarmup.start(); // the first switch to the Graphic view then skips class loading
            }
            host.documentLoadedHandler.accept(tab.document, content);
            Runnable after = tab.afterLoad;
            tab.afterLoad = null;
            if (after != null) {
                after.run();
            }
        });

        if (EditorTab.isAbandoned(tab)) {
            return; // closed (or shutting down) while the file was being read
        }

        EditorSchemaBinding.SchemaDetection detected = null;
        // Same per-tab guard as redetectSchemaForActiveDocument: never run two
        // schema detections for one tab concurrently (Xerces DOM is not thread-safe).
        if (tab.schemaDetecting.compareAndSet(false, true)) {
            try {
                detected = host.schemaBinding.detectSchemaFor(tab, content, path);
            } finally {
                tab.schemaDetecting.set(false);
            }
        }
        if (EditorTab.isAbandoned(tab)) {
            return; // closed while detecting - binding it would parse a schema for nothing
        }
        EditorSchemaBinding.SchemaDetection detection = detected;
        Platform.runLater(() -> {
            if (EditorTab.isAbandoned(tab)) {
                return; // closed between the check above and this pulse
            }
            // Binds the detected schema and always publishes a terminal status so LOADING can
            // never stick (a lost CAS - a concurrent detection ran - shows the current binding).
            host.schemaBinding.applyDetection(tab, detection);
            if (detection != null && tab.view.supportsSchema()) {
                UsageEvents.schemaBound(tab.document.getFileType().docKind(),
                        detection.source().name().toLowerCase(java.util.Locale.ROOT),
                        detection.status() != SchemaStatus.ERROR, detection.failure(), detection.refKind(),
                        openStart);
            }
        });
    }

    /** @return the file size in bytes, or {@code -1} if it cannot be determined (call off the FX thread). */
    private static long sizeOrUnknown(Path path) {
        try {
            return Files.size(path);
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /**
     * {@link #write(EditorTab, Path)} off the FX thread: the text is taken now, written on a worker, and the
     * document marked clean afterwards only if nothing was typed meanwhile. Writes of one tab run in order.
     * While a write runs the tab is not checked for external changes, so the save itself is never
     * reported as one.
     *
     * @return completes on the FX thread with whether the file was written
     */
    private CompletableFuture<Boolean> writeAsync(EditorTab tab, Path target) {
        if (tab.isXmlTextBehindModel()) {
            // Grid edits of a large document are not in the text yet: write them back off the FX
            // thread first (doing it inside text() blocked the UI for most of a minute at 150 MB).
            return tab.xmlTextCurrentAsync().thenCompose(ignored -> writeAsync(tab, target));
        }
        markEdited(tab);
        // Immutable document state: the text is built on the worker (copying 150 MB took seconds on the FX thread)
        var snapshot = tab.view.getCodeArea().getContent().snapshot();
        TextEncoding encoding = tab.document.getEncoding();
        boolean xml = tab.document.getFileType().isXmlFamily();
        long revision = tab.textRevision;
        tab.saving++;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        CompletableFuture<?> previous = tab.pendingWrite;
        tab.pendingWrite = result;
        previous.handle((ignored, error) -> null).thenRunAsync(() -> {
            DiskStamp stamp = null;
            TextEncoding saved = null;
            Throwable failure = null;
            try {
                saved = encoding.write(target, xml ? editedWith(snapshot.getText()) : snapshot.getText(), xml);
                stamp = DiskStamp.of(target);
            } catch (IOException | RuntimeException | StackOverflowError | OutOfMemoryError e) {
                failure = e;
            }
            DiskStamp written = stamp;
            TextEncoding writtenEncoding = saved;
            Throwable error = failure;
            Platform.runLater(() -> {
                tab.saving--;
                if (written != null) {
                    if (tab.textRevision == revision) {
                        tab.document.setDirty(false);
                    }
                    tab.document.setDiskStamp(written);
                    tab.document.setEncoding(writtenEncoding);
                    if (tab.document.getFileType() == EditorFileType.XSD) {
                        host.schemaBinding.reloadXmlSchemasBoundTo(target);
                    }
                } else {
                    reportSaveFailure(tab, target, error);
                }
                result.complete(written != null);
            });
        }, org.fxt.freexmltoolkit.FxtGui.executorService).whenComplete((ignored, error) -> {
            if (error != null) {
                // The worker itself failed (rejected, or an Error escaped): never leave the tab "saving".
                Platform.runLater(() -> {
                    if (!result.isDone()) {
                        tab.saving--;
                        reportSaveFailure(tab, target, error);
                        result.complete(false);
                    }
                });
            }
        });
        return result;
    }

    /**
     * A write failed: counted as {@code file_save} with status and error code, logged, and told
     * to the user (the previous behaviour — nothing at all — lost documents silently).
     */
    private void reportSaveFailure(EditorTab tab, Path target, Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        UsageEvents.fileSaveFailed(tab.document.getFileType().docKind(),
                org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(cause),
                tab.document.getEncoding().charset().name());
        EditorHost.logger.warn("Could not save {}: {}", target, String.valueOf(cause));
        if (cause instanceof java.nio.charset.CharacterCodingException) {
            offerUtf8Save(tab, target);
            return;
        }
        // file_save already carries the failure; the dialog must not report it a second time.
        org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorFileSync.save-failed"),
                I18n.t("EditorFileSync.could-not-save", target.getFileName()),
                I18n.t("EditorFileSync.check-that-the-file-is"), cause, false);
    }

    /**
     * Asked when the text holds characters the document's charset cannot store (only outside XML, where
     * they would otherwise become character references): {@code true} saves it as UTF-8 instead.
     * Replaceable by tests.
     */
    static java.util.function.Predicate<String> utf8SaveConfirmer = charset ->
            org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("EditorFileSync.save-failed"),
                    I18n.t("EditorFileSync.the-text-contains-characters-that", charset),
                    I18n.t("EditorFileSync.save-the-file-as-utf"),
                    I18n.t("EditorFileSync.save-as-utf-8"), I18n.t("EditorFileSync.cancel"));

    /** Offers to save {@code tab} as UTF-8 after its own charset could not encode the text. FX thread. */
    private void offerUtf8Save(EditorTab tab, Path target) {
        TextEncoding current = tab.document.getEncoding();
        if (org.fxt.freexmltoolkit.controls.shell.editor.search.EncodingSniffer.declaredEncoding(
                tab.text()) != null) {
            // A declaration would pick the charset again on the next write: the user has to edit it.
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorFileSync.save-failed"),
                    I18n.t("EditorFileSync.could-not-save-the-text", target.getFileName(), current.charset().name()),
                    I18n.t("EditorFileSync.change-the-encoding-in-the"),
                    (String) null, false);
            return;
        }
        if (utf8SaveConfirmer.test(current.charset().name())) {
            tab.document.setEncoding(TextEncoding.DEFAULT);
            writeAsync(tab, target);
        }
    }

    boolean write(EditorTab tab, Path target) {
        try {
            TextEncoding encoding = tab.document.getEncoding();
            boolean xml = tab.document.getFileType().isXmlFamily();
            tab.text(); // deferred model edits into the text first
            markEdited(tab);
            tab.document.setEncoding(encoding.write(target, xml ? editedWith(tab.text()) : tab.text(), xml));
            tab.document.setDirty(false);
            // Our own write must not be reported as an external change.
            tab.document.setDiskStamp(DiskStamp.of(target));
            if (tab.document.getFileType() == EditorFileType.XSD) {
                // XML documents bound to this schema pick up the saved declarations.
                host.schemaBinding.reloadXmlSchemasBoundTo(target);
            }
            if (org.fxt.freexmltoolkit.service.project.FxtProject.isProjectFile(target)) {
                // the open project's file was edited as JSON: use the saved settings right away
                org.fxt.freexmltoolkit.service.project.ProjectService.getInstance().reloadIfChanged();
            }
            return true;
        } catch (IOException | RuntimeException | StackOverflowError | OutOfMemoryError e) {
            reportSaveFailure(tab, target, e);
            return false;
        }
    }

    /** Characters at the start of a document the editor looks at for the prolog (the comment goes there). */
    static final int EDITED_WITH_HEAD = 64 * 1024;

    /**
     * Puts the "Edited with FreeXmlToolkit" comment into the editor's text before a save, so the editor shows what
     * the file gets: only the changed span at the start is replaced (one edit, caret and the rest untouched), and
     * nothing happens when the comment is already up to date, the setting is off or the prolog is not within the
     * first {@value #EDITED_WITH_HEAD} characters (then only the file gets it, see {@link #editedWith}). FX thread.
     */
    private static void markEdited(EditorTab tab) {
        if (!tab.document.getFileType().isXmlFamily()) {
            return;
        }
        var area = tab.view.getCodeArea();
        String head = area.getText(0, Math.min(area.getLength(), EDITED_WITH_HEAD));
        String updated = editedWith(head);
        if (updated.equals(head)) {
            return;
        }
        int prefix = 0;
        int max = Math.min(head.length(), updated.length());
        while (prefix < max && head.charAt(prefix) == updated.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < head.length() - prefix && suffix < updated.length() - prefix
                && head.charAt(head.length() - 1 - suffix) == updated.charAt(updated.length() - 1 - suffix)) {
            suffix++;
        }
        area.replaceText(prefix, head.length() - suffix, updated.substring(prefix, updated.length() - suffix));
    }

    /**
     * The saved text of an XML-family document: with the "Edited with FreeXmlToolkit" comment after the XML
     * declaration, unless switched off in the settings ({@link org.fxt.freexmltoolkit.service.EditedWithComment}).
     */
    private static String editedWith(String text) {
        return org.fxt.freexmltoolkit.service.EditedWithComment.applyIfEnabled(text,
                org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()::get);
    }

    /** The titled, fully loaded documents whose files are watched for external changes. */
    private java.util.List<ExternalChangeMonitor.Watched> watchedDocuments() {
        java.util.List<ExternalChangeMonitor.Watched> watched = new java.util.ArrayList<>();
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && !et.document.isUntitled() && !et.loading && et.saving == 0) {
                watched.add(new ExternalChangeMonitor.Watched(
                        et.document, et.document.getPath(), et.document.getDiskStamp()));
                // A model behind the text gets parsed anew anyway, and then reads the files as they are.
                if (et.editorContext != null && et.isModelCurrent()) {
                    et.dependencyStamps.forEach((file, stamp) -> watched.add(ExternalChangeMonitor.Watched.dependency(
                            et.document, file, stamp, et.dependencyChecksums.get(file))));
                }
                BoundSchemaWatch bound = et.boundSchemaWatch;
                if (bound != null && bound.schema().equals(et.schemaFile)) {
                    bound.stamps().forEach((file, stamp) -> watched.add(ExternalChangeMonitor.Watched.dependency(
                            et.document, file, stamp, bound.checksums().get(file))));
                }
            }
        }
        return watched;
    }

    /**
     * Refreshes the schema an XML tab is bound to when it or a file it pulls in changed. Nothing
     * is asked: only the schema facts change, the text and the undo history stay.
     */
    private void handleBoundSchemaChanges(EditorTab tab, java.util.List<ExternalChangeMonitor.Change> changes) {
        BoundSchemaWatch bound = tab.boundSchemaWatch;
        if (bound == null || !bound.schema().equals(tab.schemaFile)) {
            return;
        }
        boolean refresh = false;
        for (ExternalChangeMonitor.Change change : changes) {
            if (bound.stamps().containsKey(change.path())
                    && java.util.Objects.equals(change.known(), bound.stamps().get(change.path()))) {
                bound.stamps().put(change.path(), change.current());
                refresh |= change.known() != null && !change.sameContent();
            }
        }
        if (refresh) {
            host.schemaBinding.refreshBoundSchema(tab);
        }
    }

    /**
     * Handles files that were changed or deleted on disk by another program: asks the user,
     * one document after the other, whether to reload/ignore (changed) or keep/close (deleted).
     * Results that were overtaken meanwhile - tab closed, saved, reloaded, saved under another
     * name - are dropped; the next check re-evaluates them. Changes to files a document's model
     * pulls in come last, one question per document; a document just asked about waits for the
     * next check, because reloading it reads those files anyway.
     */
    private void handleExternalChanges(java.util.List<ExternalChangeMonitor.Change> changes) {
        java.util.Set<OpenDocument> asked = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Map<OpenDocument, java.util.List<ExternalChangeMonitor.Change>> dependencyChanges =
                new java.util.LinkedHashMap<>();
        for (ExternalChangeMonitor.Change change : changes) {
            if (change.dependency()) {
                dependencyChanges.computeIfAbsent(change.document(), d -> new java.util.ArrayList<>()).add(change);
                continue;
            }
            EditorTab tab = host.editorTabOf(change.document());
            if (tab == null || tab.loading || tab.saving > 0 || !change.path().equals(tab.document.getPath())
                    || !java.util.Objects.equals(change.known(), tab.document.getDiskStamp())) {
                continue;
            }
            OpenDocument document = tab.document;
            if (change.known() == null) {
                document.setDiskStamp(change.current()); // first known state: nothing to compare
                continue;
            }
            if (change.deleted()) {
                asked.add(document);
                host.tabPane.getSelectionModel().select(tab);
                if (externalChangePrompt.fileDeleted(document, document.isDirty())
                        == ExternalChangePrompt.DeletedChoice.CLOSE) {
                    host.closeTabNow(tab);
                } else {
                    document.setDiskStamp(DiskStamp.MISSING);
                    document.setDirty(true); // the content now only exists in the editor
                }
                continue;
            }
            if (change.diskText() != null && change.diskText().equals(tab.text())) {
                // Touched or rewritten with identical content: nothing to ask.
                document.setDiskStamp(change.current());
                document.setDirty(false);
                continue;
            }
            asked.add(document);
            host.tabPane.getSelectionModel().select(tab);
            ExternalChangePrompt.ChangedChoice choice =
                    externalChangePrompt.fileChanged(document, document.isDirty());
            // Either way the user has seen this state of the file: do not ask about it again.
            document.setDiskStamp(change.current());
            if (choice == ExternalChangePrompt.ChangedChoice.RELOAD) {
                reloadFromDisk(tab, change.path());
            } else {
                document.setDirty(true); // editor and disk differ; saving overwrites the disk
            }
        }
        dependencyChanges.forEach((document, files) -> {
            if (!asked.contains(document)) {
                handleDependencyChanges(document, files);
            }
        });
    }

    /**
     * Asks once whether to rebuild a schema's model from files it pulls in that changed. Asked
     * rather than done silently because a new model drops the undo history. Either answer
     * settles this state of the files; only a further change asks again.
     */
    private void handleDependencyChanges(OpenDocument document,
                                         java.util.List<ExternalChangeMonitor.Change> changes) {
        EditorTab tab = host.editorTabOf(document);
        if (tab == null || tab.loading || tab.saving > 0) {
            return;
        }
        if (document.getFileType() == EditorFileType.XML) {
            handleBoundSchemaChanges(tab, changes);
            return;
        }
        java.util.List<ExternalChangeMonitor.Change> pending = new java.util.ArrayList<>();
        for (ExternalChangeMonitor.Change change : changes) {
            if (!tab.dependencyStamps.containsKey(change.path())
                    || !java.util.Objects.equals(change.known(), tab.dependencyStamps.get(change.path()))) {
                continue; // a newer model has read the file meanwhile
            }
            if (change.known() == null || change.sameContent()) {
                tab.dependencyStamps.put(change.path(), change.current()); // nothing to compare, or to tell
            } else {
                pending.add(change);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        host.tabPane.getSelectionModel().select(tab);
        ExternalChangePrompt.ChangedChoice choice = externalChangePrompt.dependencyChanged(document,
                pending.stream().map(ExternalChangeMonitor.Change::path).toList());
        pending.forEach(change -> tab.dependencyStamps.replace(change.path(), change.current()));
        if (choice == ExternalChangePrompt.ChangedChoice.RELOAD && !EditorTab.isAbandoned(tab)) {
            tab.reparseFromDependencies();
            if (!document.isUntitled()) {
                host.schemaBinding.reloadXmlSchemasBoundTo(document.getPath()); // XML tabs validate against those files too
            }
        }
    }

    /**
     * Replaces a tab's content with the file's current content, discarding unsaved changes.
     * The caret, the view mode and the schema binding are kept (a changed schema reference is
     * picked up by the validation-time reconcile). On a read failure the editor content stays.
     */
    void reloadFromDisk(EditorTab tab, Path path) {
        tab.loading = true;
        tab.beginLoading();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            DiskStamp stamp = DiskStamp.of(path); // before the read, as in loadAsync
            TextEncoding.Read read;
            try {
                read = TextEncoding.read(path);
            } catch (IOException e) {
                Platform.runLater(() -> {
                    tab.endLoading();
                    tab.loading = false;
                    if (EditorTab.isAbandoned(tab)) {
                        return;
                    }
                    tab.document.setDirty(true); // still the old content, which is not on disk
                    org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorFileSync.reload-failed"),
                            I18n.t("EditorFileSync.could-not-read-2", path),
                            I18n.t("EditorFileSync.the-editor-still-shows-the"), e);
                });
                return;
            }
            Platform.runLater(() -> {
                tab.endLoading();
                tab.loading = false;
                if (EditorTab.isAbandoned(tab)) {
                    return;
                }
                tab.replaceContentFromDisk(read.text());
                tab.document.setEncoding(read.encoding());
                tab.document.setDirty(false);
                tab.document.setDiskStamp(stamp);
                if (tab.isSelected()) {
                    host.activeViewMode.set(tab.viewMode);
                    host.refreshSelectedNode();
                }
            });
        });
    }

    /** Test seam: answers external-change prompts without a blocking dialog. */
    void setExternalChangePrompt(ExternalChangePrompt prompt) {
        this.externalChangePrompt = java.util.Objects.requireNonNull(prompt, "prompt");
    }

    /** Test seam: checks the open documents' files now, regardless of window focus. */
    void checkExternalChangesNow() {
        externalChangeMonitor.checkNow();
    }

    /** Test seam: whether an external-change check is running or being handled. */
    boolean isExternalChangeCheckRunning() {
        return externalChangeMonitor.isBusy();
    }

    /**
     * Asks about unsaved documents before the application closes (window button, File → Exit,
     * Ctrl/Cmd+Q, macOS Quit): one dialog listing them with Save All / Don't Save / Cancel.
     *
     * @return whether the application may close now
     */
    public boolean confirmExit() {
        return confirmExit(this::promptExit);
    }

    /**
     * @param prompter asks the user about the given unsaved documents (shown only when there are any)
     * @return whether the application may close now
     */
    boolean confirmExit(java.util.function.Function<java.util.List<OpenDocument>, ExitChoice> prompter) {
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab.getContent() instanceof LargeFileView viewer && viewer.hasPendingChanges()) {
                host.tabPane.getSelectionModel().select(tab);
                if (!viewer.confirmDiscard()) {
                    return false;
                }
            }
        }
        java.util.List<EditorTab> dirty = new java.util.ArrayList<>();
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.isDirty()) {
                dirty.add(et);
            }
        }
        if (dirty.isEmpty()) {
            return true;
        }
        host.tabPane.getSelectionModel().select(dirty.get(0));
        ExitChoice choice = prompter.apply(dirty.stream().map(et -> et.document).toList());
        if (choice == null || choice == ExitChoice.CANCEL) {
            return false;
        }
        if (choice == ExitChoice.SAVE_ALL) {
            for (EditorTab tab : dirty) {
                host.tabPane.getSelectionModel().select(tab);
                boolean ok = tab.document.isUntitled() ? saveTabAs(tab) : write(tab, tab.document.getPath());
                if (!ok) {
                    return false; // a cancelled Save As or a failed write keeps the application open
                }
            }
        }
        return true;
    }

    private ExitChoice promptExit(java.util.List<OpenDocument> documents) {
        boolean one = documents.size() == 1;
        String names = documents.stream().map(OpenDocument::getDisplayName)
                .collect(java.util.stream.Collectors.joining("\n"));
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Unsaved Changes",
                one ? "Save changes to " + documents.get(0).getDisplayName() + " before quitting?"
                        : "Save changes to " + documents.size() + " documents before quitting?",
                (one ? "" : names + "\n\n") + "Your changes will be lost if you don't save them.");
        javafx.scene.control.ButtonType save = new javafx.scene.control.ButtonType(
                one ? "Save" : "Save All", javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType discard =
                new javafx.scene.control.ButtonType("Don't Save", javafx.scene.control.ButtonBar.ButtonData.NO);
        javafx.scene.control.ButtonType cancel =
                new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);
        if (host.getScene() != null && host.getScene().getWindow() != null) {
            alert.initOwner(host.getScene().getWindow());
        }
        var choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            return ExitChoice.CANCEL;
        }
        return choice.get() == save ? ExitChoice.SAVE_ALL : ExitChoice.DISCARD;
    }

    void confirmCloseIfDirty(EditorTab tab, javafx.event.Event closeEvent) {
        if (!tab.document.isDirty()) {
            return;
        }
        host.tabPane.getSelectionModel().select(tab);
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Unsaved Changes",
                "Save changes to " + tab.document.getDisplayName() + "?",
                "Your changes will be lost if you don't save them.");
        javafx.scene.control.ButtonType save =
                new javafx.scene.control.ButtonType("Save", javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType discard =
                new javafx.scene.control.ButtonType("Don't Save", javafx.scene.control.ButtonBar.ButtonData.NO);
        javafx.scene.control.ButtonType cancel =
                new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);

        var choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            closeEvent.consume();
        } else if (choice.get() == save) {
            boolean ok = tab.document.isUntitled() ? saveTabAs(tab) : write(tab, tab.document.getPath());
            if (!ok) {
                closeEvent.consume();
            }
        }
        // 'Don't Save' falls through and the tab closes.
    }

    boolean saveTabAs(EditorTab tab) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(I18n.t("EditorFileSync.save-as"));
        tab.document.getFileType().configureSaveChooser(chooser, tab.document.getDisplayName());
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, host.getScene() != null ? host.getScene().getWindow() : null);
        Path target = file == null ? null : withTypeExtension(tab, file.toPath());
        if (target != null && write(tab, relocateGeneratedSchemaReference(tab, target))) {
            tab.document.setPath(target);
            tab.refreshIcon();
            host.debugSupport.installBreakpointGutter(tab);
            EditorHost.rememberRecentFile(target);
            return true;
        }
        return false;
    }

    /**
     * Appends the document type's extension to a Save-As target that has none (a new XML
     * document saved as {@code foo} becomes {@code foo.xml}). The native chooser only checked
     * the bare name for an existing file, so an appended name that already exists is confirmed
     * here.
     *
     * @return the target to write, or {@code null} if the user declined to replace a file
     */
    private Path withTypeExtension(EditorTab tab, Path target) {
        Path normalized = tab.document.getFileType().withDefaultExtension(target);
        if (normalized == null || normalized.equals(target) || !java.nio.file.Files.exists(normalized)) {
            return normalized;
        }
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, "Replace File",
                normalized.getFileName() + " already exists.",
                "Do you want to replace it?");
        javafx.scene.control.ButtonType replace =
                new javafx.scene.control.ButtonType("Replace", javafx.scene.control.ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(replace, javafx.scene.control.ButtonType.CANCEL);
        return alert.showAndWait().filter(replace::equals).isPresent() ? normalized : null;
    }

    /**
     * First save of a generated document (Generate Sample XML): its root element references
     * the bound schema by bare file name, which only resolves next to the schema. Saving it
     * anywhere else rewrites that reference to a path relative to {@code target}'s folder,
     * so the file validates from where it lands — also in other tools and after a restart.
     * Titled documents and hand-written references are never touched
     * (see {@link org.fxt.freexmltoolkit.service.SampleSchemaLocation#relocate}).
     *
     * @return {@code target}, for chaining into {@link #write(EditorTab, Path)}
     */
    private Path relocateGeneratedSchemaReference(EditorTab tab, Path target) {
        File xsd = tab.schemaFile;
        if (xsd != null && tab.document.isUntitled() && target != null && target.getParent() != null) {
            org.fxt.freexmltoolkit.service.SampleSchemaLocation
                    .relocate(tab.text(), xsd, target.getParent())
                    .ifPresent(tab.view::setText);
        }
        return target;
    }
}
