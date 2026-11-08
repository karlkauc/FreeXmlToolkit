package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javafx.scene.control.Tab;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.ResolvedQueryTarget;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The XSLT / XQuery / Schematron debugger wiring of an {@link EditorHost}: starting and ending
 * a session, breakpoint gutters on stylesheet tabs (following edits), input-node breakpoints,
 * inline values, the marked input line, Run to Cursor and launch configurations. All methods
 * run on the FX thread unless stated otherwise.
 */
final class EditorDebugSupport {

    private final EditorHost host;

    EditorDebugSupport(EditorHost host) {
        this.host = host;
    }

    /**
     * Shows the paused values after their declaration lines on a stylesheet's editor tab
     * ({@code null} clears): see {@link org.fxt.freexmltoolkit.debugger.InlineValues}.
     */
    private void setDebugInlineValuesFor(OpenDocument document,
            org.fxt.freexmltoolkit.debugger.PausedSnapshot snapshot) {
        EditorTab tab = host.editorTabOf(document);
        if (tab == null) {
            return;
        }
        java.util.Map<Integer, String> values = snapshot == null ? java.util.Map.of()
                : org.fxt.freexmltoolkit.debugger.InlineValues.compute(
                        host.getDocumentText(document).orElse(""), snapshot.lineNumber(), snapshot);
        tab.view.setDebugInlineValues(values);
    }

    /** A stylesheet module opened during a debug session, with its breakpoint gutter. */
    private record DebugModule(OpenDocument document, org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory gutter) { }

    /** Everything belonging to the one active debug session. */
    private static final class XsltDebugSession {
        final org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController controller;
        final org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane pane;
        final File mainFile;
        final String mainPath;
        /** Modules by normalised file path, the main stylesheet first. */
        final java.util.Map<String, DebugModule> modules = new java.util.LinkedHashMap<>();
        /** Normalised paths of the modules the main stylesheet includes/imports (static scan). */
        final java.util.Set<String> resolvedModules;
        /** Value popups installed on the module editors for this session. */
        final java.util.Map<OpenDocument, org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugHover> hovers =
                new java.util.HashMap<>();
        /** The run's input: its file (when it came from one) and/or its open document. */
        Path inputFile;
        OpenDocument inputDocument;
        /** The input document whose context-node line is marked, and that line. */
        OpenDocument markedInput;
        int markedInputLine;

        XsltDebugSession(org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController controller,
                         org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane pane, File mainFile,
                         java.util.Set<String> resolvedModules) {
            this.controller = controller;
            this.pane = pane;
            this.mainFile = mainFile;
            this.mainPath = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(mainFile.getAbsolutePath());
            this.resolvedModules = java.util.Set.copyOf(resolvedModules);
        }
    }

    private XsltDebugSession activeDebug;

    /** @return {@code true} while an XSLT debug session is alive (its pane is in the dock). */
    public boolean isDebugSessionActive() {
        return activeDebug != null;
    }

    /** @return the DEBUG pane of the active session, or {@code null}. */
    public org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane activeDebugPane() {
        return activeDebug == null ? null : activeDebug.pane;
    }

    /**
     * Routes the debugger keys (F5/F7/F8/Shift+F8 while paused) to the active session.
     *
     * @return {@code true} if the key was consumed by the debugger
     */
    public boolean handleDebugShortcut(javafx.scene.input.KeyEvent event) {
        return activeDebug != null && activeDebug.pane.handleShortcut(event);
    }

    /**
     * Launches an interactive XSLT debug session: opens the stylesheet as an editor
     * document with a breakpoint gutter, shows the DEBUG pane in the bottom dock (the
     * stylesheet stays visible above it) and starts the run — paused at the first
     * instruction unless breakpoints exist. Only one session at a time: starting another
     * ends the current one first.
     */
    public void startXsltDebug(java.io.File xsltFile, String xml,
            java.util.Map<String, Object> parameters,
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format) {
        startXsltDebug(xsltFile, xml, parameters, format, null);
    }

    /**
     * Like {@link #startXsltDebug(File, String, java.util.Map, org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat)},
     * additionally handing every finished run's result to {@code onFinished} (FX thread) —
     * the PDF/FOP panel uses it to pick up the XSL-FO a debugged stylesheet produced.
     */
    public void startXsltDebug(java.io.File xsltFile, String xml,
            java.util.Map<String, Object> parameters,
            org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format,
            java.util.function.Consumer<org.fxt.freexmltoolkit.service.XsltTransformationResult> onFinished) {
        if (activeDebug != null) {
            endXsltDebug();
        }

        // 1. Open (or focus) the stylesheet as an editor document.
        host.openFile(xsltFile);
        OpenDocument xsltDoc = host.getActiveDocument().orElse(null);
        if (xsltDoc == null) {
            return;
        }

        // Read the stylesheet up front: if it cannot be read, surface a clear error and abort
        // rather than silently running Saxon on an empty stylesheet.
        try {
            java.nio.file.Files.readString(xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // debug_run (stage start) carries the failure; the dialog explains it
            UsageEvents.debugStartFailed("xslt", org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e));
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("EditorDebugSupport.xslt-debug"), I18n.t("EditorDebugSupport.could-not-start-the-debugger"),
                    I18n.t("EditorDebugSupport.could-not-read-stylesheet", e.getMessage()), e, false);
            return;
        }

        // 2. Controller + session: restore the persisted breakpoints — the main stylesheet's
        //    (plus any legacy aggregate stored under it) and those set in the editor on the
        //    modules it includes — attach the gutter to the main module and to every module
        //    already open, and pause on entry when there is nothing to stop at.
        var controller = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController();
        // An .xq file is debugged as XQuery, a Schematron through its compiled XSLT (same
        // session, dock, gutter and watches).
        EditorFileType debugType = EditorFileType.fromFileName(xsltFile.getName());
        if (debugType == EditorFileType.XQUERY) {
            controller.setLanguage(org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController.Language.XQUERY);
        } else if (debugType == EditorFileType.SCHEMATRON) {
            controller.setLanguage(org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController.Language.SCHEMATRON);
        }
        var session = controller.getSession();
        for (org.fxt.freexmltoolkit.debugger.Breakpoint bp
                : org.fxt.freexmltoolkit.debugger.BreakpointStore.load(xsltFile.getAbsolutePath())) {
            session.addBreakpoint(bp);
        }
        java.util.Set<String> resolvedModules =
                org.fxt.freexmltoolkit.debugger.StylesheetModules.resolve(xsltFile.toPath());
        for (String module : resolvedModules) {
            mergeStoredBreakpoints(session, module);
        }
        org.fxt.freexmltoolkit.debugger.WatchStore.loadSymbols(xsltFile.getAbsolutePath())
                .forEach(session::addSymbolBreakpoint);
        controller.setPauseOnEntry(debugForcePauseOnEntry
                || session.getBreakpoints().isEmpty() && session.getSymbolBreakpoints().isEmpty());
        debugForcePauseOnEntry = false;
        if (debugRunToLine > 0) {
            controller.setRunToOnStart(debugRunToPath, debugRunToLine);
            debugRunToLine = 0;
        }

        Path inputFile = debugInputFile;
        debugInputFile = null;
        var pane = new org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane(controller);
        // The Template… dialog suggests the names declared in the stylesheet and its modules.
        pane.getBreakpointsView().setStylesheetSupplier(() -> stylesheetTexts(xsltFile, resolvedModules));
        // Smart Step Into: the calls on the paused line, from the editor text of the paused module.
        pane.setStepTargetSource(snap -> debugStepTargets(xsltFile, resolvedModules, snap));
        // Watches belong to the main stylesheet and come back with its next session.
        String watchKey = xsltFile.getAbsolutePath();
        pane.getWatchView().setWatches(org.fxt.freexmltoolkit.debugger.WatchStore.load(watchKey));
        pane.getWatchView().setOnWatchesChanged(
                list -> org.fxt.freexmltoolkit.debugger.WatchStore.save(watchKey, list));
        XsltDebugSession debug = new XsltDebugSession(controller, pane, xsltFile, resolvedModules);
        debug.inputFile = inputFile;
        debug.inputDocument = inputFile != null ? documentAt(inputFile) : documentWithText(xml, xsltDoc);
        lastLaunch = new LastLaunch(xsltFile, inputFile, debug.inputDocument,
                parameters == null ? java.util.Map.of() : new java.util.LinkedHashMap<>(parameters), format, onFinished);
        activeDebug = debug;
        attachDebugModule(debug, xsltDoc, debug.mainPath);
        for (Tab tab : new java.util.ArrayList<>(host.tabPane.getTabs())) {
            if (tab instanceof EditorTab et && et.document.getPath() != null) {
                String path = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(et.document.getPath().toString());
                if (resolvedModules.contains(path) && !debug.modules.containsKey(path)) {
                    attachDebugModule(debug, et.document, path);
                }
            }
        }

        // 3. Wire the pane back to this host.
        pane.setOnPaused(this::onDebugPaused);
        pane.setOnShowInput(() -> showDebugInput(debug));
        pane.setOnOpenDocument(uri -> {
            try {
                Path file = Path.of(java.net.URI.create(uri));
                if (java.nio.file.Files.isRegularFile(file)) {
                    host.openFile(file, true);
                }
            } catch (RuntimeException ignored) {
                // not a local file
            }
        });
        pane.setOnShowInEditor(() -> {
            var snap = session.getPausedSnapshot();
            if (snap != null) {
                jumpToDebugLocation(snap.filePath(), snap.lineNumber());
            } else {
                host.selectDocument(xsltDoc);
            }
        });
        pane.setOnJumpToBreakpoint(bp -> jumpToDebugLocation(bp.filePath(), bp.lineNumber()));
        pane.setOnJumpToFrame(frame -> jumpToDebugLocation(frame.filePath(), frame.lineNumber()));
        pane.setOnJumpToMessage(msg -> {
            if (msg.getLineNumber() > 0) {
                jumpToDebugLocation(moduleForMessage(debug, msg.getLocation()), msg.getLineNumber());
            }
        });
        pane.setOnBreakpointsChanged(() -> {
            debug.modules.values().forEach(m -> host.refreshGutterFor(m.document()));
            persistDebugBreakpoints(debug);
        });
        pane.setOnResult(result -> {
            showDebugResult(debug, result);
            if (onFinished != null && result != null) {
                onFinished.accept(result);
            }
        });
        pane.setOnClose(this::endXsltDebug);

        host.bottomDock().register(EditorHost.DEBUG_DOCK_KEY, pane, "DEBUG");
        host.bottomDock().show(EditorHost.DEBUG_DOCK_KEY);
        host.selectDocument(xsltDoc);

        // 4. Start the run: the stylesheet is re-read from disk on every (re)start.
        // The debugger runs what the editor shows: the open stylesheet's (and open modules')
        // text, saved or not — read on the FX thread at every (re)start. Breakpoints follow the
        // editor's lines, so they match.
        controller.setModuleTexts(() -> onFxThread(this::openStylesheetTexts));
        controller.start(xml, inputFile == null ? null : inputFile.toUri().toString(),
                () -> onFxThread(() -> stylesheetText(xsltFile, controller)),
                xsltFile.toURI().toString(), parameters, format);
    }

    /**
     * The text the debugger runs for the main stylesheet: the editor's when it is open (a note
     * goes to Messages when it has unsaved changes), else the file.
     */
    private String stylesheetText(File xsltFile,
                                  org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController controller) {
        OpenDocument doc = documentAt(xsltFile.toPath());
        if (doc != null && controller.getLanguage()
                == org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugController.Language.SCHEMATRON) {
            // SchXslt compiles the file (and its includes) from disk
            if (doc.isDirty()) {
                controller.note("Debugging the saved file of " + doc.getDisplayName()
                        + " - save it to include the unsaved changes");
            }
            doc = null;
        }
        if (doc != null) {
            var text = host.getDocumentText(doc);
            if (text.isPresent() && !text.get().isEmpty()) {
                if (doc.isDirty()) {
                    controller.note("Debugging the editor text of " + doc.getDisplayName()
                            + " (unsaved changes included)");
                }
                return text.get();
            }
        }
        try {
            return java.nio.file.Files.readString(xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The editor text of every open, saved XSLT document, by normalised path (FX thread). */
    private java.util.Map<String, String> openStylesheetTexts() {
        java.util.Map<String, String> texts = new java.util.HashMap<>();
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.getPath() != null
                    && et.document.getFileType() == EditorFileType.XSLT) {
                host.getDocumentText(et.document).filter(t -> !t.isEmpty()).ifPresent(t -> texts.put(
                        org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(et.document.getPath().toString()), t));
            }
        }
        return texts;
    }

    /** Runs {@code work} on the FX thread and waits for it (directly when already there). */
    private static <T> T onFxThread(java.util.concurrent.Callable<T> work) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            try {
                return work.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(work);
        javafx.application.Platform.runLater(task);
        try {
            return task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("The editor did not answer in time", e);
        }
    }

    /** @return the open document of {@code file}, or {@code null}. */
    private OpenDocument documentAt(Path file) {
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.getPath() != null
                    && et.document.getPath().toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())) {
                return et.document;
            }
        }
        return null;
    }

    /** @return the open document (other than {@code except}) whose current text is {@code text}. */
    private OpenDocument documentWithText(String text, OpenDocument except) {
        if (text == null) {
            return null;
        }
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document != except
                    && host.getDocumentText(et.document).map(text::equals).orElse(false)) {
                return et.document;
            }
        }
        return null;
    }

    /**
     * Marks the paused context node's line in its input document (blue tint) without switching
     * tabs, and enables the pane's Show input. In-memory input maps to the open document the
     * text came from; a file input — or a document loaded with {@code doc()} — is opened behind
     * the stylesheet when it is not open yet.
     */
    private void markDebugInput(XsltDebugSession debug, org.fxt.freexmltoolkit.debugger.PausedSnapshot snapshot) {
        if (debug.markedInput != null) {
            setDebugInputLineFor(debug.markedInput, -1);
            debug.markedInput = null;
            debug.markedInputLine = 0;
        }
        debug.pane.setInputLocationAvailable(false);
        if (snapshot == null) {
            return;
        }
        var location = org.fxt.freexmltoolkit.debugger.InputLocation.of(snapshot.contextXdm()).orElse(null);
        if (location == null) {
            return;
        }
        OpenDocument doc = inputDocumentFor(debug, location.systemId());
        if (doc == null) {
            return;
        }
        debug.markedInput = doc;
        debug.markedInputLine = location.line();
        whenDocumentLoaded(doc, () -> {
            if (debug.markedInput == doc) {
                setDebugInputLineFor(doc, location.line());
            }
        }, 15);
        debug.pane.setInputLocationAvailable(true);
    }

    /** @return the open document for an input node's system id, opening a file behind if needed. */
    private OpenDocument inputDocumentFor(XsltDebugSession debug, String systemId) {
        String path = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(systemId);
        if (path.isEmpty()) {
            return debug.inputDocument;
        }
        if (debug.modules.containsKey(path) || path.equals(debug.mainPath)) {
            return null; // a node of the stylesheet itself (document('')) — not an input
        }
        Path file = Path.of(path);
        OpenDocument doc = documentAt(file);
        if (doc == null && java.nio.file.Files.isRegularFile(file)) {
            host.openFile(file, false);
            doc = documentAt(file);
        }
        if (doc != null && debug.inputFile != null
                && file.toAbsolutePath().normalize().equals(debug.inputFile.toAbsolutePath().normalize())) {
            debug.inputDocument = doc;
        }
        return doc;
    }

    private void setDebugInputLineFor(OpenDocument document, int line) {
        EditorTab tab = host.editorTabOf(document);
        if (tab != null) {
            tab.view.setDebugInputLine(line);
        }
    }

    /** Runs {@code action} once {@code document}'s text is loaded (a just-opened file loads async). */
    private void whenDocumentLoaded(OpenDocument document, Runnable action, int attemptsLeft) {
        boolean loaded = host.getDocumentText(document).map(t -> !t.isEmpty()).orElse(false);
        if (loaded || attemptsLeft <= 0) {
            action.run();
            return;
        }
        var retry = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        retry.setOnFinished(e -> whenDocumentLoaded(document, action, attemptsLeft - 1));
        retry.play();
    }

    /** Shows the paused context node: selects its input document and moves the caret there. */
    private void showDebugInput(XsltDebugSession debug) {
        if (debug.markedInput == null) {
            return;
        }
        host.selectDocument(debug.markedInput);
        jumpWhenLoaded(debug.markedInput, debug.markedInputLine, 10);
        setDebugInputLineFor(debug.markedInput, debug.markedInputLine);
    }

    /** @return the 1-based line marked as the paused context node in {@code document}, or 0 (tests). */
    /** @return the inline values shown on a document's editor tab (tests). */
    java.util.Map<Integer, String> debugInlineValuesOf(OpenDocument document) {
        EditorTab tab = host.editorTabOf(document);
        return tab == null ? java.util.Map.of() : tab.view.getDebugInlineValues();
    }

    int debugInputLineOf(OpenDocument document) {
        XsltDebugSession debug = activeDebug;
        return debug != null && debug.markedInput == document ? debug.markedInputLine : 0;
    }

    /** Ends the active debug session: stops the run, detaches gutters/highlights, removes the pane. */
    public void endXsltDebug() {
        XsltDebugSession debug = activeDebug;
        if (debug == null) {
            return;
        }
        activeDebug = null;
        debug.hovers.values().forEach(org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugHover::uninstall);
        debug.hovers.clear();
        if (debug.markedInput != null) {
            setDebugInputLineFor(debug.markedInput, -1);
        }
        persistDebugBreakpoints(debug);
        debug.controller.stop();
        debug.controller.close();
        debug.pane.dispose();
        for (DebugModule module : debug.modules.values()) {
            host.setDebugLineFor(module.document(), -1);
            setDebugInlineValuesFor(module.document(), null);
            EditorTab tab = host.editorTabOf(module.document());
            if (tab != null) {
                // The session gutter goes; the file's own (persisted) gutter takes over again.
                EditorHost.setGutterContributor(tab, "xslt-debugger", null);
                installBreakpointGutter(tab);
                tab.view.refreshGutter();
            }
        }
        host.bottomDock().remove(EditorHost.DEBUG_DOCK_KEY);
    }

    /**
     * Gives a saved XSLT document the standalone breakpoint gutter, backed by the file's
     * persisted breakpoints ({@link org.fxt.freexmltoolkit.debugger.FileBreakpoints}), so
     * breakpoints can be set before the debugger starts. No-op for other file types, unsaved
     * documents, and modules a running debug session already owns; a module of the running
     * session that is opened now joins the session instead (its gutter talks to the run).
     */
    void installBreakpointGutter(EditorTab et) {
        EditorFileType type = et.document.getFileType();
        if ((type != EditorFileType.XSLT && type != EditorFileType.XQUERY && type != EditorFileType.SCHEMATRON)
                || et.document.getPath() == null) {
            return;
        }
        followEditsWithBreakpoints(et); // whichever gutter the tab carries, its breakpoints follow edits
        String path = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(et.document.getPath().toString());
        if (activeDebug != null) {
            if (activeDebug.modules.containsKey(path)) {
                return;
            }
            if (activeDebug.resolvedModules.contains(path)) {
                attachDebugModule(activeDebug, et.document, path);
                return;
            }
        }
        var gutter = new org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory(
                org.fxt.freexmltoolkit.debugger.FileBreakpoints.persistent(et.document.getPath()),
                v -> et.view.refreshGutter());
        gutter.setFilePath(path);
        if (EditorHost.setGutterContributor(et, "xslt-debugger", gutter)) {
            et.view.refreshGutter();
        }
    }

    private static final String BREAKPOINT_SHIFT_KEY = "fxt.breakpointShift";

    /**
     * Lets the breakpoints of an XSLT tab follow its edits: inserted or deleted lines move the
     * breakpoints below them, a deleted breakpoint line removes its breakpoint — in the file's
     * own store or, during a session, in the session. Whole-text replacements (loading,
     * reloading, reformatting) are ignored. Installed once per tab.
     */
    private void followEditsWithBreakpoints(EditorTab et) {
        var area = et.view.getCodeArea();
        if (area == null || area.getProperties().containsKey(BREAKPOINT_SHIFT_KEY)) {
            return;
        }
        area.getProperties().put(BREAKPOINT_SHIFT_KEY, Boolean.TRUE);
        org.fxt.freexmltoolkit.controls.v2.editor.TextEdits.of(area).subscribe(change -> {
            var gutter = breakpointGutterOf(et);
            if (gutter == null || gutter.getTarget() == null) {
                return;
            }
            int lengthBefore = area.getLength() - change.getInsertedLength() + change.getRemovedLength();
            if (change.getRemovedLength() == lengthBefore) {
                return; // the whole text was replaced (load, reload, format): no edit to follow
            }
            String removed = change.getRemoved();
            String inserted = change.getInserted();
            var shift = new org.fxt.freexmltoolkit.debugger.LineShift(
                    lineOf(area.getText(), change.getPosition()), columnOf(area.getText(), change.getPosition()),
                    org.fxt.freexmltoolkit.debugger.LineShift.newlines(removed),
                    org.fxt.freexmltoolkit.debugger.LineShift.newlines(inserted));
            if (shift.isNeutral() || !gutter.getTarget().shiftLines(gutter.getFilePath(), shift)) {
                return;
            }
            et.view.refreshGutter();
            XsltDebugSession debug = activeDebug;
            if (debug != null && gutter.getTarget() == debug.controller.getSession()) {
                debug.pane.getBreakpointsView().refresh();
                persistDebugBreakpoints(debug);
            }
        });
    }

    /** 1-based line of {@code offset} in {@code text}. */
    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 0-based column of {@code offset} in {@code text}. */
    static int columnOf(String text, int offset) {
        int end = Math.min(offset, text.length());
        if (end <= 0) {
            return 0;
        }
        int start = text.lastIndexOf('\n', end - 1);
        return end - (start + 1);
    }

    /** @return whether F9 / Toggle Breakpoint applies: the active tab is a saved XSLT document. */
    public boolean canToggleBreakpoint() {
        return host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                && breakpointGutterOf(et) != null;
    }

    /**
     * Toggles the breakpoint on the caret line of the active XSLT document (F9 / Tools → Toggle
     * Breakpoint) — the standalone or the session gutter, whichever is installed.
     *
     * @return {@code true} if a breakpoint was set or removed
     */
    public boolean toggleBreakpointAtCaret() {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)) {
            return false;
        }
        var gutter = breakpointGutterOf(et);
        if (gutter == null) {
            return false;
        }
        gutter.toggle(et.view.getCodeArea().getCurrentParagraph() + 1);
        return true;
    }

    /**
     * Run to Cursor (Ctrl+F10 / Tools → Run to Cursor) in the active XSLT document: while a
     * debug session runs or is paused in one of its modules, runs to the caret line.
     *
     * @return {@code false} when there is no session to run (the caller may start one with
     *         {@link #setDebugRunToOnStart})
     */
    public boolean runToCursor() {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)) {
            return false;
        }
        var gutter = breakpointGutterOf(et);
        if (gutter == null || activeDebug == null) {
            return false;
        }
        var session = activeDebug.controller.getSession();
        return session.runTo(gutter.getFilePath(), et.view.getCodeArea().getCurrentParagraph() + 1);
    }

    /** @return whether Run to Cursor applies: the active tab is a saved XSLT document. */
    public boolean canRunToCursor() {
        return canToggleBreakpoint();
    }

    /** @return the caret line (1-based) of the active XSLT document, or 0. */
    public int activeCaretLine() {
        return host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et
                ? et.view.getCodeArea().getCurrentParagraph() + 1 : 0;
    }

    /** How the last debug run was started — for Re-run Last Debug (Ctrl+Shift+F5). */
    private record LastLaunch(File xsltFile, Path inputFile, OpenDocument inputDocument,
                              java.util.Map<String, Object> parameters,
                              org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat format,
                              java.util.function.Consumer<org.fxt.freexmltoolkit.service.XsltTransformationResult> onFinished) { }

    private LastLaunch lastLaunch;
    /** "Pause on entry" forced by a debug configuration for the next {@link #startXsltDebug}. */
    private boolean debugForcePauseOnEntry;

    /** @return whether a debug run was started in this app run (Re-run Last Debug applies). */
    public boolean canRerunLastDebug() {
        return lastLaunch != null;
    }

    /**
     * Re-runs the last debug run: same stylesheet, parameters and format, the input read again
     * (its file, or the open document it came from).
     *
     * @return an error message, or {@code null} when the debugger started
     */
    public String rerunLastDebug() {
        LastLaunch last = lastLaunch;
        if (last == null) {
            return "No debug run to repeat yet.";
        }
        String xml;
        if (last.inputFile() != null) {
            try {
                xml = java.nio.file.Files.readString(last.inputFile(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "Could not read the input " + last.inputFile() + ": " + e.getMessage();
            }
            setDebugInputFile(last.inputFile());
        } else if (last.inputDocument() != null && host.getOpenDocuments().contains(last.inputDocument())) {
            xml = host.getDocumentText(last.inputDocument()).orElse("");
        } else {
            return "The input document of the last debug run is no longer open.";
        }
        startXsltDebug(last.xsltFile(), xml, last.parameters(), last.format(), last.onFinished());
        return null;
    }

    /**
     * Starts the debugger for {@code xsltFile} with a debug configuration: its input file — or
     * the stylesheet's Run target —, parameters, output format (detected from {@code xsl:output}
     * when automatic) and pause on entry.
     *
     * @return an error message, or {@code null} when the debugger started
     */
    public String debugWithConfig(File xsltFile, org.fxt.freexmltoolkit.debugger.DebugLaunchConfig config) {
        String xslt;
        try {
            xslt = java.nio.file.Files.readString(xsltFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            UsageEvents.debugStartFailed("xslt", org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e));
            return "Could not read the stylesheet: " + e.getMessage();
        }
        String xml;
        Path input = null;
        if (!config.inputPath().isEmpty()) {
            input = Path.of(config.inputPath());
            try {
                xml = java.nio.file.Files.readString(input, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                UsageEvents.debugStartFailed("xslt", org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e));
                return "Could not read the input " + input + ": " + e.getMessage();
            }
        } else {
            OpenDocument xsltDoc = documentAt(xsltFile.toPath());
            var target = xsltDoc == null ? Optional.<ResolvedQueryTarget>empty() : host.resolveQueryTarget(xsltDoc);
            if (target.isEmpty()) {
                return "Open an XML document first, or set an input file in the debug configuration.";
            }
            try {
                xml = target.get().loadXml();
            } catch (IOException e) {
                UsageEvents.debugStartFailed("xslt", org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e));
                return "Could not read the input: " + e.getMessage();
            }
            if (target.get().file() != null) {
                input = target.get().file().toPath();
            }
        }
        var format = config.outputFormat().isEmpty()
                ? TransformRunner.detectXsltOutputFormat(xslt)
                : org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.valueOf(config.outputFormat());
        if (input != null) {
            setDebugInputFile(input);
        }
        debugForcePauseOnEntry = config.pauseOnEntry();
        startXsltDebug(xsltFile, xml, new java.util.LinkedHashMap<>(config.parameters()), format);
        return null;
    }

    /** The input file of the next {@link #startXsltDebug} (set by callers whose input is a file). */
    private Path debugInputFile;

    /**
     * Tells the next {@link #startXsltDebug} that its XML text was read from {@code file}, so the
     * paused context node can be shown in that file. Without it the input is matched to the open
     * document with the same text.
     */
    public void setDebugInputFile(Path file) {
        this.debugInputFile = file;
    }

    /** Pending "debug to cursor" target for the next {@link #startXsltDebug} (path, line). */
    private String debugRunToPath;
    private int debugRunToLine;

    /**
     * Arms the next {@link #startXsltDebug} to run to the caret line of the active XSLT
     * document ("Debug to Cursor"). Cleared with {@code setDebugRunToOnStart(null, 0)}.
     *
     * @return {@code false} when the active tab is no saved XSLT document
     */
    public boolean prepareDebugToCursor() {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)) {
            return false;
        }
        var gutter = breakpointGutterOf(et);
        if (gutter == null) {
            return false;
        }
        setDebugRunToOnStart(gutter.getFilePath(), et.view.getCodeArea().getCurrentParagraph() + 1);
        return true;
    }

    /** Makes the next debug start run to {@code path:line} instead of pausing on entry. */
    public void setDebugRunToOnStart(String path, int line) {
        this.debugRunToPath = path;
        this.debugRunToLine = line;
    }

    private static org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory breakpointGutterOf(EditorTab et) {
        return et.gutter.get("xslt-debugger") instanceof org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory g
                ? g : null;
    }

    /**
     * Adds the breakpoints stored for {@code path} (set in the editor before the session, or in
     * an earlier session) to {@code session}; a line the session already has is left alone.
     */
    private static void mergeStoredBreakpoints(org.fxt.freexmltoolkit.debugger.DebugSession session, String path) {
        java.util.Set<org.fxt.freexmltoolkit.debugger.Breakpoint> present = session.getBreakpoints();
        for (org.fxt.freexmltoolkit.debugger.Breakpoint bp
                : org.fxt.freexmltoolkit.debugger.BreakpointStore.load(path)) {
            if (!bp.filePath().equals(path)) {
                continue; // a legacy aggregate from when this module was debugged as main
            }
            boolean known = present.stream().anyMatch(
                    b -> b.filePath().equals(path) && b.lineNumber() == bp.lineNumber());
            if (!known) {
                session.addBreakpoint(bp);
            }
        }
    }

    /** Attaches a breakpoint gutter for {@code path} to {@code document} and records the module. */
    private void attachDebugModule(XsltDebugSession debug, OpenDocument document, String path) {
        if (!path.equals(debug.mainPath)) {
            // An include opened during the session: pick up the breakpoints set on it in the editor.
            mergeStoredBreakpoints(debug.controller.getSession(), path);
        }
        var gutter = new org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory(
                debug.controller.getSession(), v -> {
                    host.refreshGutterFor(document);
                    debug.pane.getBreakpointsView().refresh();
                    persistDebugBreakpoints(debug);
                });
        gutter.setFilePath(path);
        host.setGutterFactoryFor(document, gutter);
        debug.modules.put(path, new DebugModule(document, gutter));
        EditorTab tab = host.editorTabOf(document);
        if (tab != null && !debug.hovers.containsKey(document)) {
            debug.hovers.put(document, new org.fxt.freexmltoolkit.controls.shell.editor.debug.DebugHover(
                    tab.view.getCodeArea(), debug.pane::currentScope, debug.controller::getWatchEvaluator,
                    debug.pane::openViewer).install());
        }
    }

    /**
     * Adds "Break When Processed" to an XML-family editor's context menu: shown while an XSLT
     * debug session runs (not on the session's stylesheets), it toggles an input-node
     * breakpoint on the element at the caret.
     */
    void installInputNodeBreakpointMenu(EditorTab tab) {
        javafx.scene.control.ContextMenu menu = tab.view.getEditorContextMenu();
        if (menu == null) {
            return;
        }
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(I18n.t("EditorDebugSupport.break-when-processed"));
        item.setId("editor-ctx-break-when-processed");
        item.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-record-circle"),
                org.fxt.freexmltoolkit.controls.theme.ActionColor.DELETE));
        item.setOnAction(e -> toggleInputNodeBreakpoint(tab.document,
                tab.view.getCodeArea().getText(), tab.view.getCodeArea().getCaretPosition()));
        menu.getItems().add(0, item);
        javafx.scene.control.SeparatorMenuItem separator = new javafx.scene.control.SeparatorMenuItem();
        menu.getItems().add(1, separator);
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, e -> {
            XsltDebugSession debug = activeDebug;
            boolean show = debug != null && !isDebugModule(debug, tab.document);
            item.setVisible(show);
            separator.setVisible(show);
            if (show) {
                item.setText(inputNodeBreakpointAt(debug, tab.document, tab.view.getCodeArea().getText(),
                        tab.view.getCodeArea().getCaretPosition()) != null
                        ? "Remove Break When Processed" : "Break When Processed");
            }
        });
        item.setVisible(false);
        separator.setVisible(false);
    }

    private static boolean isDebugModule(XsltDebugSession debug, OpenDocument document) {
        return debug.modules.values().stream().anyMatch(m -> m.document() == document);
    }

    /** The input-node breakpoint for the element at {@code caret}, as it is or would be stored. */
    private static org.fxt.freexmltoolkit.debugger.SymbolBreakpoint inputNodeAt(OpenDocument document,
            String text, int caret) {
        var element = org.fxt.freexmltoolkit.controls.shared.utilities.XmlElementAtCaret.at(text, caret).orElse(null);
        if (element == null || element.startTagEnd() < 0) {
            return null;
        }
        int gt = element.startTagEnd();
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < gt; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int column = gt - lineStart + 2; // Saxon: the 1-based column just after the '>'
        String path = document.getPath() == null ? ""
                : org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(document.getPath().toString());
        return org.fxt.freexmltoolkit.debugger.SymbolBreakpoint.inputNode(path, line, column, element.xpath());
    }

    private static org.fxt.freexmltoolkit.debugger.SymbolBreakpoint inputNodeBreakpointAt(XsltDebugSession debug,
            OpenDocument document, String text, int caret) {
        var wanted = inputNodeAt(document, text, caret);
        if (wanted == null) {
            return null;
        }
        return debug.controller.getSession().getSymbolBreakpoints().stream()
                .filter(s -> s.kind() == org.fxt.freexmltoolkit.debugger.SymbolBreakpoint.Kind.INPUT_NODE
                        && s.inputLine() == wanted.inputLine() && s.inputColumn() == wanted.inputColumn()
                        && s.inputPath().equals(wanted.inputPath()))
                .findFirst().orElse(null);
    }

    /**
     * Toggles the input-node breakpoint on the element at {@code caret} of {@code document} in
     * the running debug session.
     *
     * @return {@code true} when one was added, {@code false} when removed or no session / element
     */
    boolean toggleInputNodeBreakpoint(OpenDocument document, String text, int caret) {
        XsltDebugSession debug = activeDebug;
        if (debug == null || document == null || text == null) {
            return false;
        }
        var session = debug.controller.getSession();
        var existing = inputNodeBreakpointAt(debug, document, text, caret);
        boolean added = false;
        if (existing != null) {
            session.removeSymbolBreakpoint(existing);
        } else {
            var created = inputNodeAt(document, text, caret);
            if (created == null) {
                return false;
            }
            session.addSymbolBreakpoint(created);
            added = true;
        }
        debug.pane.getBreakpointsView().refresh();
        persistDebugBreakpoints(debug);
        return added;
    }

    /** Writes the session's breakpoints back, each file under its own key (modules without any are cleared). */
    private void persistDebugBreakpoints(XsltDebugSession debug) {
        java.util.Set<String> files = new java.util.LinkedHashSet<>();
        files.add(debug.mainPath);
        files.addAll(debug.resolvedModules);
        files.addAll(debug.modules.keySet());
        org.fxt.freexmltoolkit.debugger.BreakpointStore.saveByFile(
                debug.controller.getSession().getBreakpoints(), files);
        org.fxt.freexmltoolkit.debugger.WatchStore.saveSymbols(debug.mainPath,
                debug.controller.getSession().getSymbolBreakpoints());
    }

    /** The text of the main stylesheet and its included / imported modules (unreadable ones skipped). */
    /** The Smart Step Into targets of the paused line (FX thread: reads the editor text). */
    private java.util.List<org.fxt.freexmltoolkit.debugger.StepTarget> debugStepTargets(File main,
            java.util.Set<String> modules, org.fxt.freexmltoolkit.debugger.PausedSnapshot snap) {
        String path = snap.filePath() == null || snap.filePath().isEmpty()
                ? main.getAbsolutePath() : snap.filePath();
        String text = null;
        OpenDocument doc = documentAt(Path.of(path));
        if (doc != null) {
            text = host.getDocumentText(doc).orElse(null);
        }
        if (text == null) {
            try {
                text = java.nio.file.Files.readString(Path.of(path), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return java.util.List.of();
            }
        }
        java.util.List<String> functions = org.fxt.freexmltoolkit.debugger.StylesheetSymbols
                .scan(stylesheetTexts(main, modules) + "\n" + text)
                .getOrDefault(org.fxt.freexmltoolkit.debugger.SymbolBreakpoint.Kind.FUNCTION, java.util.List.of());
        return org.fxt.freexmltoolkit.debugger.StepTargets.find(text, snap.lineNumber(), functions);
    }

    private static String stylesheetTexts(File main, java.util.Set<String> modules) {
        StringBuilder sb = new StringBuilder();
        java.util.List<Path> files = new java.util.ArrayList<>();
        files.add(main.toPath());
        modules.forEach(m -> files.add(Path.of(m)));
        for (Path file : files) {
            try {
                sb.append(java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8)).append('\n');
            } catch (IOException | RuntimeException ignored) {
                // a module that cannot be read contributes no suggestions
            }
        }
        return sb.toString();
    }

    /**
     * Marks the paused location in the editor: the module's tab is selected (opened first
     * when Saxon paused inside an {@code xsl:include}/{@code xsl:import}), the line gets the
     * gutter arrow and the execution-line tint. {@code null} clears the markers (run moved on).
     */
    private void onDebugPaused(org.fxt.freexmltoolkit.debugger.PausedSnapshot snapshot) {
        XsltDebugSession debug = activeDebug;
        if (debug == null) {
            return;
        }
        String path = snapshot == null ? null : debugModulePath(debug, snapshot.filePath());
        for (java.util.Map.Entry<String, DebugModule> entry : debug.modules.entrySet()) {
            boolean here = snapshot != null && entry.getKey().equals(path);
            entry.getValue().gutter().setCurrentExecutionLine(here ? snapshot.lineNumber() : -1);
            host.setDebugLineFor(entry.getValue().document(), here ? snapshot.lineNumber() : -1);
            setDebugInlineValuesFor(entry.getValue().document(), here ? snapshot : null);
            host.refreshGutterFor(entry.getValue().document());
        }
        if (snapshot != null) {
            jumpToDebugLocation(path, snapshot.lineNumber());
        }
        markDebugInput(debug, snapshot);
    }

    /** @return the module path for a pause/jump location — the main stylesheet when it is unknown. */
    private static String debugModulePath(XsltDebugSession debug, String filePath) {
        return filePath == null || filePath.isEmpty() ? debug.mainPath : filePath;
    }

    /** @return the module whose file name appears in a message's "file:line" location, else the main one. */
    private static String moduleForMessage(XsltDebugSession debug, String location) {
        if (location != null) {
            // open modules first, then included ones not open yet (e.g. a finding in an sch:include)
            java.util.Set<String> candidates = new java.util.LinkedHashSet<>(debug.modules.keySet());
            candidates.addAll(debug.resolvedModules);
            for (String path : candidates) {
                String name = org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(path);
                if (!name.isEmpty() && location.startsWith(name)) {
                    return path;
                }
            }
        }
        return debug.mainPath;
    }

    /**
     * Selects (opening it if needed) the module at {@code filePath} and moves the caret to
     * {@code line}. A module opened for the first time loads asynchronously, so the jump is
     * retried briefly until its text is in.
     */
    private void jumpToDebugLocation(String filePath, int line) {
        XsltDebugSession debug = activeDebug;
        if (debug == null) {
            return;
        }
        String path = debugModulePath(debug, filePath);
        DebugModule module = debug.modules.get(path);
        if (module == null) {
            File file = new File(path);
            if (!file.isFile()) {
                return;
            }
            host.openFile(file);
            OpenDocument doc = host.getActiveDocument().orElse(null);
            if (doc == null) {
                return;
            }
            attachDebugModule(debug, doc, path);
            module = debug.modules.get(path);
            // A freshly opened include carries the current-line marker as well.
            var snap = debug.controller.getSession().getPausedSnapshot();
            if (snap != null && path.equals(debugModulePath(debug, snap.filePath()))) {
                module.gutter().setCurrentExecutionLine(snap.lineNumber());
                host.setDebugLineFor(doc, snap.lineNumber());
                setDebugInlineValuesFor(doc, snap);
            }
        }
        host.selectDocument(module.document());
        if (line > 0) {
            jumpWhenLoaded(module.document(), line, 10);
        }
    }

    private void jumpWhenLoaded(OpenDocument document, int line, int attemptsLeft) {
        boolean loaded = host.getActiveText().map(t -> !t.isEmpty()).orElse(false);
        if (loaded || attemptsLeft <= 0) {
            if (host.getActiveDocument().orElse(null) == document) {
                host.goToLine(line);
                host.refreshGutterFor(document);
            }
            return;
        }
        var retry = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        retry.setOnFinished(e -> jumpWhenLoaded(document, line, attemptsLeft - 1));
        retry.play();
    }

    /** Shows a debug run's result in OUTPUT without taking the DEBUG pane out of the front. */
    private void showDebugResult(XsltDebugSession debug, org.fxt.freexmltoolkit.service.XsltTransformationResult result) {
        if (activeDebug != debug || result == null) {
            return;
        }
        boolean stopped = !result.isSuccess() && result.getErrorMessage() != null
                && result.getErrorMessage().contains("stopped");
        if (stopped) {
            return;
        }
        TransformOutputPanel out = host.transformOutputPanel();
        if (result.isSuccess()) {
            out.showTransformResult(result.getOutputContent(), result.getOutputFormat(), result.getExecutionTime());
        } else {
            out.showError(I18n.t("EditorDebugSupport.error", result.getErrorMessage()));
        }
        host.bottomDock().select(EditorHost.DEBUG_DOCK_KEY);
        host.bottomDock().markUpdated(TransformOutputPanel.DOCK_KEY);
    }
}
