package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.scene.control.Tab;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.BoundSchemaWatch;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaSource;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaStatus;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * Schema binding of an {@link EditorHost}'s documents: binding an XSD / JSON Schema by hand or
 * after generation, the auto-detection on open and on demand ({@code xsi:schemaLocation},
 * Schema Library, catalogs, JSON {@code $schema}), the validation-time reconcile, and the
 * XSD-derived {@code XmlSchemaProvider} behind XML tabs (also reloaded when the bound XSD is
 * saved). Detection and schema parsing run off the FX thread; results are applied on it.
 */
final class EditorSchemaBinding {

    private final EditorHost host;

    EditorSchemaBinding(EditorHost host) {
        this.host = host;
    }

    /**
     * Outcome of a schema auto-detection run: the bound XSD (or null) plus the terminal
     * status, how the binding was resolved, and an optional human-readable detail
     * (catalog target / namespace / root element) for the status bar tooltip. A failed
     * detection keeps the source that was tried and why it failed (anonymous telemetry).
     */
    record SchemaDetection(File schema, SchemaStatus status, SchemaSource source, String sourceDetail,
                                   String failure, String refKind) {
        static final SchemaDetection NOT_FOUND = new SchemaDetection(null, SchemaStatus.NONE, SchemaSource.NONE, null);
        /** {@link #failure} value: the referenced schema could not be resolved / reached. */
        static final String UNRESOLVED = "unresolved";
        /** {@link #failure} value: the schema file was found but is missing or not loadable. */
        static final String UNLOADABLE = "unloadable";

        SchemaDetection(File schema, SchemaStatus status, SchemaSource source, String sourceDetail) {
            this(schema, status, source, sourceDetail, null, null);
        }

        static SchemaDetection failed(SchemaSource source, String failure) {
            return failed(source, failure, null);
        }

        /** @param location the declared location, reduced to its {@link UsageEvents#referenceKind kind} */
        static SchemaDetection failed(SchemaSource source, String failure, String location) {
            return new SchemaDetection(null, SchemaStatus.ERROR, source, null, failure,
                    UsageEvents.referenceKind(location));
        }
    }

    /** A Schema Library / catalog resolution result, carrying provenance for the status bar. */
    private record LibraryHit(File file, SchemaSource source, String detail) { }

    /**
     * Binds a schema to the active document — an XSD (schema-aware IntelliSense +
     * validation) for the XML family, a JSON Schema (validation) for JSON documents.
     *
     * @return completes on the FX thread with {@code true} once bound (the schema is parsed off-thread)
     */
    public CompletableFuture<Boolean> setSchemaForActiveDocument(File xsd) {
        return bindSchemaToActiveDocument(xsd, SchemaRebindPolicy.SchemaBindingOrigin.MANUAL,
                SchemaSource.MANUAL, null, null);
    }

    /**
     * Binds the schema a generated document was produced from (Generate Sample XML). The
     * document's root element references that schema only by file name — resolvable once
     * saved next to it, but not for the unsaved tab — so the binding is established directly,
     * and validation works on the very first run. The binding counts as auto-detected from
     * the declared reference (not as a manual choice): the reconcile keeps it while the
     * declaration is unchanged, and re-detects when the user edits or the first save
     * relativizes the reference.
     *
     * @return completes on the FX thread with {@code true} once bound
     */
    public CompletableFuture<Boolean> bindGeneratedSchemaToActiveDocument(File xsd) {
        if (xsd == null) {
            return CompletableFuture.completedFuture(false);
        }
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et)) {
            return CompletableFuture.completedFuture(false);
        }
        // What the reconcile will sniff from the buffer (no base directory: untitled).
        String declared = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.XmlService.class)
                .getSchemaNameFromXmlContent(et.view.getText(), null)
                .orElse(null);
        return bindSchemaToActiveDocument(xsd, SchemaRebindPolicy.SchemaBindingOrigin.AUTO,
                SchemaSource.DECLARED, xsd.getAbsolutePath(), declared);
    }

    /**
     * Binds {@code xsd} (or clears the binding for {@code null}) on the active tab. Parsing a
     * schema can take seconds to minutes for large XSDs, so it runs on a worker thread while
     * the status bar shows LOADING; the FX thread only applies the result. Call on the FX thread.
     *
     * @return completes on the FX thread with {@code true} once the schema is bound, or
     *         {@code false} if it failed to load, the tab was closed, or a newer bind superseded it
     */
    private CompletableFuture<Boolean> bindSchemaToActiveDocument(File xsd,
                                                                  SchemaRebindPolicy.SchemaBindingOrigin origin,
                                                                  SchemaSource source, String sourceDetail,
                                                                  String declared) {
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (!(tab instanceof EditorTab et) || !et.view.supportsSchema()) {
            return CompletableFuture.completedFuture(false);
        }
        long request = et.schemaBindRequest.incrementAndGet();
        et.schemaBindingGen.incrementAndGet(); // supersede queued schema reconciles
        if (xsd == null) {
            et.view.clearSchema(); // loadSchema(null) is a no-op failure in the views
            et.schemaFile = null;
            et.schemaOrigin = SchemaRebindPolicy.SchemaBindingOrigin.NONE;
            applySchemaBindingUi(et, null, SchemaSource.NONE, null);
            return CompletableFuture.completedFuture(true);
        }
        host.publishSchemaStatus(et, SchemaStatus.LOADING);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        long bindStart = System.nanoTime();
        var bindKind = et.document.getFileType().docKind();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            boolean bound = false;
            // Same per-tab lock as the detections: the Xerces DOM behind the schema pipeline is
            // not thread-safe, and holding it until the binding fields are set keeps a validation
            // reconcile from re-detecting over the user's choice in between.
            if (acquireSchemaDetection(et, () -> et.schemaBindRequest.get() != request)) {
                try {
                    if (et.schemaBindRequest.get() == request) {
                        bound = loadSchemaSafely(et, xsd);
                        // re-checked after the (long) parse: a newer bind or a clear may have arrived
                        if (bound && et.schemaBindRequest.get() == request) {
                            et.schemaFile = xsd;
                            et.schemaOrigin = origin;
                            if (origin == SchemaRebindPolicy.SchemaBindingOrigin.AUTO) {
                                // Behaves like a detection result: an unchanged declaration is
                                // KEEP-fast-pathed. Manual bindings leave the last detection alone
                                // (MANUAL never rebinds).
                                et.lastDetectedSchemaLocation = declared;
                            }
                        }
                    }
                } finally {
                    et.schemaDetecting.set(false);
                }
            }
            boolean applied = bound;
            Platform.runLater(() -> {
                if (EditorTab.isAbandoned(et) || et.schemaBindRequest.get() != request) {
                    result.complete(false); // closed, or a newer bind owns the status now
                    return;
                }
                if (applied) {
                    applySchemaBindingUi(et, xsd, source, sourceDetail);
                } else {
                    // the user picked a schema that failed to load — surface it in the status bar
                    host.publishSchemaStatus(et, SchemaStatus.ERROR);
                }
                if (origin == SchemaRebindPolicy.SchemaBindingOrigin.MANUAL) {
                    // the automatic bindings report themselves when a document is opened
                    UsageEvents.schemaBound(bindKind, "manual", applied, applied ? null : "unloadable",
                            applied ? null : "file", bindStart);
                }
                result.complete(applied);
            });
        });
        return result;
    }

    /** FX-thread half of a successful bind or clear: caches, active-schema mirror, status, provider. */
    private void applySchemaBindingUi(EditorTab et, File xsd, SchemaSource source, String sourceDetail) {
        et.view.invalidateIntelliSenseCache();
        if (et.isSelected()) {
            host.activeSchema.set(xsd);
        }
        host.publishSchemaSource(et, xsd == null ? SchemaSource.NONE : source, xsd == null ? null : sourceDetail);
        host.publishSchemaStatus(et, xsd == null ? SchemaStatus.NONE : SchemaStatus.READY);
        loadXmlSchemaProviderAsync(et, xsd);
    }

    /** {@code view.loadSchema} on a worker thread; an unexpected failure counts as "not bound". */
    private static boolean loadSchemaSafely(EditorTab tab, File xsd) {
        try {
            return tab.view.loadSchema(xsd);
        } catch (RuntimeException | StackOverflowError | OutOfMemoryError e) {
            EditorHost.logger.warn("Could not load schema {}: {}", xsd, e.toString());
            if (e instanceof OutOfMemoryError) {
                org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportError(e, "editor.schema_load");
            }
            return false;
        }
    }

    /**
     * Takes the tab's {@link EditorTab#schemaDetecting} lock on a worker thread, waiting for a
     * running detection (bounded, like {@link #redetectSchemaForActiveDocument()}).
     *
     * @param giveUp checked while waiting; {@code true} means the caller has been superseded
     * @return {@code true} if the lock is now held (release it with {@code schemaDetecting.set(false)})
     */
    private static boolean acquireSchemaDetection(EditorTab tab, java.util.function.BooleanSupplier giveUp) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
        while (!tab.schemaDetecting.compareAndSet(false, true)) {
            if (giveUp.getAsBoolean() || System.nanoTime() > deadline || EditorTab.isAbandoned(tab)) {
                return false;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * Counts schema bindings actually applied to a tab. Observable so tests can assert that a
     * detection abandoned mid-flight applies nothing; nothing in the UI reads it.
     */
    final java.util.concurrent.atomic.AtomicInteger schemaBindingsApplied =
            new java.util.concurrent.atomic.AtomicInteger();

    /** @return how many schema bindings have been applied since this host was created */
    int schemaBindingsApplied() {
        return schemaBindingsApplied.get();
    }

    /**
     * Re-runs the schema auto-detection ({@code xsi:schemaLocation} /
     * {@code xsi:noNamespaceSchemaLocation}, local and remote) for the active document
     * when it has no schema bound yet — e.g. when the Validation panel opens and the
     * reference was added after the file was opened, or an earlier remote download
     * failed. Detection reads the live editor buffer, so unsaved (even untitled)
     * documents work. No-op when a schema is already bound, so an explicit user choice
     * is never overridden; the changed/removed-reference cases are handled by the
     * validation-time reconcile in {@link #schemaForValidation(String)}.
     */
    public void redetectSchemaForActiveDocument() {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab tab)) {
            return;
        }
        if (tab.schemaFile != null || !tab.view.supportsSchema()) {
            return;
        }
        String content = tab.view.getText();
        Path path = tab.document.getPath();
        long generation = tab.schemaBindingGen.incrementAndGet(); // supersede queued reconciles
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            if (EditorTab.isAbandoned(tab)) {
                return;
            }
            // One detection at a time per tab: the open-time detection (loadAsync) or a
            // second redetect may still be running, and the schema pipeline's Xerces
            // deferred DOM is not thread-safe (concurrent runs corrupted it).
            // Do NOT give up on a lost race - the holder may itself be superseded and bind
            // nothing, and no later task would retry; wait for it, then re-check with fresh
            // state (same rule as reconcileSchemaBinding). The wait is bounded so a stuck
            // holder can never pin a thread of the fixed-size pool for good: giving up then is
            // no worse than the unconditional skip this replaces. The bound has to clear the
            // slowest realistic holder - an open-time detection whose declared location points
            // at an unroutable host waits out the connect timeout, measured at ~35 s here.
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
            while (!tab.schemaDetecting.compareAndSet(false, true)) {
                if (tab.schemaBindingGen.get() != generation || System.nanoTime() > deadline
                        || EditorTab.isAbandoned(tab)) {
                    return; // superseded, abandoned, or the holder never released
                }
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (tab.schemaBindingGen.get() != generation) {
                // superseded: a fresher snapshot owns the binding now
                tab.schemaDetecting.set(false);
                return;
            }
            // LOADING only after the CAS succeeded — a lost CAS returns without any
            // completion path, and a stray LOADING would stick in the status bar.
            Platform.runLater(() -> host.publishSchemaStatus(tab, SchemaStatus.LOADING));
            SchemaDetection detection;
            try {
                if (tab.schemaFile != null) {
                    // the other detection won while we waited
                    detection = new SchemaDetection(null, SchemaStatus.READY, SchemaSource.NONE, null);
                } else {
                    detection = detectSchemaFor(tab, content, path);
                }
            } finally {
                tab.schemaDetecting.set(false);
            }
            if (EditorTab.isAbandoned(tab)) {
                return; // closed while detecting - binding it would parse a schema for nothing
            }
            SchemaDetection result = detection;
            Platform.runLater(() -> {
                if (EditorTab.isAbandoned(tab)) {
                    return; // closed between the check above and this pulse
                }
                File autoXsd = result.schema();
                if (autoXsd != null) {
                    tab.schemaFile = autoXsd;
                    tab.schemaOrigin = SchemaRebindPolicy.SchemaBindingOrigin.AUTO;
                    tab.view.invalidateIntelliSenseCache();
                    if (tab.isSelected()) {
                        host.activeSchema.set(autoXsd);
                    }
                    host.publishSchemaSource(tab, result.source(), result.sourceDetail());
                    loadXmlSchemaProviderAsync(tab, autoXsd);
                    schemaBindingsApplied.incrementAndGet();
                }
                // Terminal status in every branch, so LOADING never sticks.
                host.publishSchemaStatus(tab, result.status());
            });
        });
    }

    /**
     * Reconciles the active document's XSD binding with the schema location declared in
     * {@code content} (the live editor buffer) and returns a supplier the validation
     * worker calls to obtain the XSD to validate against. Call this on the FX thread —
     * it snapshots the active tab; resolve the supplier on a worker thread only, as a
     * changed remote reference may be downloaded there. An added, changed, or removed
     * {@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} therefore takes
     * effect on the very validation run that observes it. Policy (see
     * {@link SchemaRebindPolicy}): manual bindings are never overridden, and an
     * unchanged declaration costs no I/O.
     */
    public java.util.function.Supplier<File> schemaForValidation(String content) {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab tab)
                || !tab.view.supportsSchema()) {
            return () -> null;
        }
        Path path = tab.document.getPath();
        long generation = tab.schemaBindingGen.incrementAndGet();
        return () -> reconcileSchemaBinding(tab, content, path, generation);
    }

    /** Worker-thread body of {@link #schemaForValidation(String)}. @return the XSD to validate with */
    private File reconcileSchemaBinding(EditorTab tab, String content, Path path, long generation) {
        File baseDir = path != null && path.getParent() != null ? path.getParent().toFile() : null;
        String declared = declaredSchemaLocation(tab, content, baseDir);
        if (SchemaRebindPolicy.decideRebind(tab.schemaOrigin, declared, tab.lastDetectedSchemaLocation)
                == SchemaRebindPolicy.RebindAction.KEEP) {
            return tab.schemaFile; // fast path: unchanged declaration, no lock, no I/O
        }
        // Rebinding needed — take the per-tab detection CAS. Do NOT give up on a lost
        // race: the holder (e.g. a redetect) may itself be superseded and bind nothing,
        // and no later task would retry — wait for it, then re-decide with fresh state.
        while (!tab.schemaDetecting.compareAndSet(false, true)) {
            if (tab.schemaBindingGen.get() != generation) {
                return tab.schemaFile; // superseded while waiting: a fresher task owns this
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return tab.schemaFile;
            }
        }
        try {
            if (tab.schemaBindingGen.get() != generation) {
                return tab.schemaFile; // superseded: a fresher snapshot owns the binding now
            }
            // Re-decide under the lock: the previous holder may have already bound
            // (or cleared) exactly what this snapshot declares.
            switch (SchemaRebindPolicy.decideRebind(tab.schemaOrigin, declared, tab.lastDetectedSchemaLocation)) {
                case KEEP -> {
                    return tab.schemaFile;
                }
                case CLEAR -> {
                    tab.lastDetectedSchemaLocation = null;
                    tab.schemaFile = null;
                    tab.schemaOrigin = SchemaRebindPolicy.SchemaBindingOrigin.NONE;
                    Platform.runLater(() -> {
                        clearSchemaBindingUi(tab);
                        host.publishSchemaSource(tab, SchemaSource.NONE, null);
                        host.publishSchemaStatus(tab, SchemaStatus.NONE);
                    });
                    return null;
                }
                case DETECT -> {
                    // LOADING only after the CAS succeeded — a lost CAS has no
                    // completion path, and a stray LOADING would stick in the status bar.
                    Platform.runLater(() -> host.publishSchemaStatus(tab, SchemaStatus.LOADING));
                    SchemaDetection detection = detectSchemaFor(tab, content, path);
                    File xsd = detection.schema();
                    tab.schemaFile = xsd;
                    tab.schemaOrigin = xsd != null
                            ? SchemaRebindPolicy.SchemaBindingOrigin.AUTO
                            : SchemaRebindPolicy.SchemaBindingOrigin.NONE;
                    Platform.runLater(() -> {
                        if (xsd != null) {
                            tab.view.invalidateIntelliSenseCache();
                            if (tab.isSelected()) {
                                host.activeSchema.set(xsd);
                            }
                            host.publishSchemaSource(tab, detection.source(), detection.sourceDetail());
                            loadXmlSchemaProviderAsync(tab, xsd);
                        } else {
                            // Declared but unresolvable: drop the stale binding too —
                            // keeping it would silently validate against the wrong schema.
                            clearSchemaBindingUi(tab);
                            host.publishSchemaSource(tab, SchemaSource.NONE, null);
                        }
                        // Terminal status in every branch, so LOADING never sticks.
                        host.publishSchemaStatus(tab, detection.status());
                    });
                    return xsd;
                }
                default -> { }
            }
            return tab.schemaFile; // unreachable
        } finally {
            tab.schemaDetecting.set(false);
        }
    }

    /**
     * Sniffs the schema location the buffer declares — a top-level {@code "$schema"}
     * member for JSON, {@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation}
     * for the XML family. When the XML family declares nothing, falls back to the Schema
     * Library resolution ({@link #schemaFromLibrary(String)}) so a library auto-binding is
     * treated as "declared" too and survives reconcile. Worker-thread safe (fresh,
     * stateless service instances).
     */
    private String declaredSchemaLocation(EditorTab tab, String content, File baseDir) {
        if (tab.document.getFileType() == EditorFileType.JSON) {
            var jsonService = new org.fxt.freexmltoolkit.service.JsonService();
            // A library-mapped raw $schema id (meta-schema ids included) is a "declaration"
            // too — see detectJsonSchemaFor. Without this, the filtered
            // getSchemaLocationFromJsonContent alone would report it as undeclared and the
            // reconcile would CLEAR the library binding on the very first validation run.
            // Gated on the auto-bind toggle (like the XML family's schemaFromLibrary), so
            // turning auto-bind off and reconciling clears a library binding here too.
            java.util.Optional<String> raw = jsonService.getRawSchemaIdFromJsonContent(content);
            if (raw.isPresent() && org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).isSchemaLibraryAutoBindEnabled()
                    && org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared()
                    .resolveJsonSchema(raw.get()).isPresent()) {
                return raw.get();
            }
            return jsonService.getSchemaLocationFromJsonContent(content).orElse(null);
        }
        String declared = org.fxt.freexmltoolkit.di.ServiceRegistry
                .get(org.fxt.freexmltoolkit.service.XmlService.class)
                .getSchemaNameFromXmlContent(content, baseDir)
                .orElse(null);
        if (declared == null) {
            // No xsi:schemaLocation declared — a Schema Library auto-binding (see
            // detectSchemaFor) is not a "declaration" the reconcile can see otherwise,
            // so without this the very first validation would CLEAR it. Re-resolving
            // the library lookup here is deterministic for local entries (same path
            // every call), so it correctly short-circuits to KEEP once bound.
            LibraryHit fromLibrary = schemaFromLibrary(content);
            if (fromLibrary != null) {
                declared = fromLibrary.file().getAbsolutePath();
            }
        }
        return declared;
    }

    /** FX thread: clears the editor/IntelliSense side of a dropped schema binding. */
    private void clearSchemaBindingUi(EditorTab tab) {
        tab.view.clearSchema();
        tab.view.invalidateIntelliSenseCache();
        tab.xmlSchemaProvider = null;
        if (tab.xmlEditorContext != null) {
            tab.xmlEditorContext.setSchemaProvider(null);
        }
        if (tab.isSelected()) {
            host.activeSchema.set(null);
        }
    }

    /**
     * Auto-detects and binds the XSD declared in {@code content} (the document text,
     * usually the live editor buffer): resolves both {@code xsi:schemaLocation}
     * (namespaced) and {@code xsi:noNamespaceSchemaLocation}, local (relative to
     * {@code pathOrNull}'s directory when given) and remote (http/https, downloaded and
     * cached) references via {@link org.fxt.freexmltoolkit.service.XmlService}. A fresh
     * service instance keeps detection per-tab and stateless. Runs on a worker thread
     * (remote download must not block the UI).
     */
    SchemaDetection detectSchemaFor(EditorTab tab, String content, Path pathOrNull) {
        if (!tab.view.supportsSchema()) {
            return SchemaDetection.NOT_FOUND;
        }
        if (tab.document.getFileType() == EditorFileType.JSON) {
            return detectJsonSchemaFor(tab, content, pathOrNull);
        }
        try {
            org.fxt.freexmltoolkit.service.XmlService service =
                    new org.fxt.freexmltoolkit.service.XmlServiceImpl();
            File baseDir = pathOrNull != null && pathOrNull.getParent() != null
                    ? pathOrNull.getParent().toFile() : null;
            java.util.Optional<String> declared = service.getSchemaNameFromXmlContent(content, baseDir);
            // Remember what the buffer declared (also on failure below) so the
            // validation-time reconcile can short-circuit unchanged documents.
            tab.lastDetectedSchemaLocation = declared.orElse(null);
            if (declared.isEmpty()) {
                // No xsi:schemaLocation — ask the Schema Library by root namespace / root element.
                LibraryHit fromLibrary = schemaFromLibrary(content);
                if (fromLibrary == null) {
                    return SchemaDetection.NOT_FOUND; // the document references no schema
                }
                tab.lastDetectedSchemaLocation = fromLibrary.file().getAbsolutePath();
                if (!tab.view.loadSchema(fromLibrary.file())) {
                    return SchemaDetection.failed(fromLibrary.source(), SchemaDetection.UNLOADABLE);
                }
                return new SchemaDetection(fromLibrary.file(), SchemaStatus.READY,
                        fromLibrary.source(), fromLibrary.detail());
            }
            // A reference exists. Schema Library / XML catalogs first: a `system` or
            // `rewriteSystem` catalog entry (or a user mapping) may redirect the declared
            // location to a local file, so an unreachable URL never has to be fetched.
            LibraryHit hit = schemaFromLibraryForDeclared(content, declared.get(), baseDir);
            File xsd;
            SchemaSource source;
            String detail;
            if (hit != null) {
                xsd = hit.file();
                source = hit.source();
                detail = hit.detail();
            } else {
                // From here on, every failure is an ERROR (the user expects IntelliSense
                // but won't get it), not a silent "No XSD".
                if (!service.loadSchemaFromLocation(declared.get())) {
                    // Unreachable location: the Schema Library may still know the
                    // document's namespace (user mapping, catalog `uri`, bundled entry).
                    LibraryHit fallback = schemaFromLibrary(content);
                    if (fallback == null) {
                        // referenced, but not resolvable
                        return SchemaDetection.failed(SchemaSource.DECLARED, SchemaDetection.UNRESOLVED,
                                declared.get());
                    }
                    xsd = fallback.file();
                    source = fallback.source();
                    detail = fallback.detail();
                } else {
                    xsd = service.getCurrentXsdFile();
                    source = SchemaSource.DECLARED;
                    detail = declared.get();
                }
            }
            if (xsd == null || !xsd.exists() || !tab.view.loadSchema(xsd)) {
                return SchemaDetection.failed(source, SchemaDetection.UNLOADABLE); // resolved, but missing or unparsable
            }
            return new SchemaDetection(xsd, SchemaStatus.READY, source, detail);
        } catch (Exception e) {
            // detection is best-effort; a malformed document simply binds no schema (a detector bug is counted)
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "editor.schema_detect");
            return SchemaDetection.NOT_FOUND;
        }
    }

    /**
     * Schema Library lookup for a document WITH a declared schema location: catalog
     * {@code system}/{@code rewriteSystem} entries and user mappings are matched against
     * the declared location (resolved against the document's directory), then the root
     * namespace is tried. Honours the auto-bind toggle and the offline rule. Returns null
     * on a miss. Worker-thread safe.
     */
    private static LibraryHit schemaFromLibraryForDeclared(String content, String declared, File baseDir) {
        try {
            if (!org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .isSchemaLibraryAutoBindEnabled()) {
                return null;
            }
            var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            String namespace = org.fxt.freexmltoolkit.service.XmlRootElementSniffer.sniff(content)
                    .map(r -> r.namespace()).filter(ns -> !ns.isEmpty()).orElse(null);
            String baseUri = baseDir != null ? baseDir.toURI().toString() : null;
            return org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                    .lookup(library, namespace, declared, baseUri, library.isRemoteDownloadAllowed())
                    .map(hit -> new LibraryHit(hit.file().toFile(),
                            hit.viaCatalog() ? SchemaSource.CATALOG : SchemaSource.LIBRARY, hit.detail()))
                    .orElse(null);
        } catch (Exception e) {
            return null;   // best-effort
        }
    }

    /**
     * Schema Library lookup for a document without a schema reference: the document
     * element's namespace (or, without a namespace, its local name) is mapped to an XSD.
     * Returns null when auto-binding is disabled or nothing matches. Worker-thread safe.
     *
     * <p>Auto-binding is a user-enabled feature, so a remote entry is downloaded on first
     * use — but it goes through the same offline rule as the resolver hooks
     * ({@link org.fxt.freexmltoolkit.service.SchemaLibraryLookup#materializeIfAllowed}), so
     * disabling remote downloads ({@code -Dfxt.schema.namespaceFallback=false}, as the test
     * suite does) restricts auto-binding to local and already-cached entries too.</p>
     */
    private static LibraryHit schemaFromLibrary(String content) {
        try {
            if (!org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .isSchemaLibraryAutoBindEnabled()) {
                return null;
            }
            var root = org.fxt.freexmltoolkit.service.XmlRootElementSniffer.sniff(content).orElse(null);
            if (root == null) return null;
            var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            boolean byRootElement = root.namespace().isEmpty();
            var entry = byRootElement
                    ? library.resolveByRootElement(root.localName())
                    : library.resolveNamespace(root.namespace(), org.fxt.freexmltoolkit.domain.SchemaKind.XSD);
            if (entry.isEmpty()) return null;
            File file = org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                    .materializeIfAllowed(library, entry.get(), library.isRemoteDownloadAllowed())
                    .map(java.nio.file.Path::toFile).orElse(null);
            if (file == null) return null;
            // resolveByRootElement never consults catalogs, so a root-element match is
            // always a plain library mapping; a namespace match may be catalog-sourced.
            SchemaSource source = !byRootElement
                    && entry.get().source() == org.fxt.freexmltoolkit.domain.EntrySource.CATALOG
                    ? SchemaSource.CATALOG : SchemaSource.LIBRARY;
            String detail = source == SchemaSource.CATALOG
                    ? entry.get().location()
                    : (byRootElement ? root.localName() : root.namespace());
            return new LibraryHit(file, source, detail);
        } catch (Exception e) {
            return null;   // best-effort
        }
    }

    /**
     * JSON counterpart of the XSD detection: binds the JSON Schema a top-level
     * {@code "$schema"} member declares — local (relative to the document's directory)
     * or remote (http/https, downloaded and cached). Meta-schema ids (json-schema.org)
     * are not bindings and detect as "no schema", unless the Schema Library maps them.
     * Runs on a worker thread.
     */
    private SchemaDetection detectJsonSchemaFor(EditorTab tab, String content, Path pathOrNull) {
        try {
            var service = new org.fxt.freexmltoolkit.service.JsonService();
            File baseDir = pathOrNull != null && pathOrNull.getParent() != null
                    ? pathOrNull.getParent().toFile() : null;
            // Schema Library first: a mapped $schema URI (meta-schema ids included) binds its local file.
            java.util.Optional<String> raw = service.getRawSchemaIdFromJsonContent(content);
            if (raw.isPresent() && org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).isSchemaLibraryAutoBindEnabled()) {
                var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
                var entry = library.resolveJsonSchema(raw.get());
                File mapped = entry.flatMap(e -> org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                                .materializeIfAllowed(library, e, library.isRemoteDownloadAllowed()))
                        .map(java.nio.file.Path::toFile).orElse(null);
                if (mapped != null) {
                    tab.lastDetectedSchemaLocation = raw.get();
                    SchemaSource source = entry.get().source() == org.fxt.freexmltoolkit.domain.EntrySource.CATALOG
                            ? SchemaSource.CATALOG : SchemaSource.LIBRARY;
                    if (!tab.view.loadSchema(mapped)) {
                        return SchemaDetection.failed(source, SchemaDetection.UNLOADABLE);
                    }
                    String detail = source == SchemaSource.CATALOG ? entry.get().location() : raw.get();
                    return new SchemaDetection(mapped, SchemaStatus.READY, source, detail);
                }
            }
            java.util.Optional<String> declared = service.getSchemaLocationFromJsonContent(content);
            // Remember what the buffer declared (also on failure below) so the
            // validation-time reconcile can short-circuit unchanged documents.
            tab.lastDetectedSchemaLocation = declared.orElse(null);
            if (declared.isEmpty()) {
                return SchemaDetection.NOT_FOUND; // the document references no schema
            }
            // A reference exists — from here on, every failure is an ERROR (the user
            // expects schema validation but won't get it), not a silent "No Schema".
            File schema = service.resolveJsonSchemaLocation(declared.get(), baseDir);
            if (schema == null || !schema.exists()) {
                return SchemaDetection.failed(SchemaSource.DECLARED, SchemaDetection.UNRESOLVED, declared.get());
            }
            if (!tab.view.loadSchema(schema)) {
                return SchemaDetection.failed(SchemaSource.DECLARED, SchemaDetection.UNLOADABLE);
            }
            return new SchemaDetection(schema, SchemaStatus.READY, SchemaSource.DECLARED, declared.get());
        } catch (Exception e) {
            // detection is best-effort; a malformed document simply binds no schema (a detector bug is counted)
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "editor.schema_detect");
            return SchemaDetection.NOT_FOUND;
        }
    }

    /**
     * Builds the read-only, XSD-derived schema provider for an XML-instance document off the
     * UI thread (parsing the XSD is expensive), then attaches it to the tab and — if the tab is
     * front-most — refreshes the inspector so the selected element shows its declared type and
     * documentation. Best-effort: a malformed/missing XSD simply binds no provider.
     */
    void loadXmlSchemaProviderAsync(EditorTab tab, File xsd) {
        if (xsd == null || tab.document.getFileType() != EditorFileType.XML) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            // Stamped first and kept on failure: repairing a broken schema must be noticed too.
            Path schemaPath = xsd.toPath().toAbsolutePath().normalize();
            java.util.Map<Path, DiskStamp> stamps = new java.util.LinkedHashMap<>();
            java.util.Map<Path, Long> checksums = new java.util.HashMap<>();
            stamps.put(schemaPath, DiskStamp.of(schemaPath));
            checksums.put(schemaPath, DiskStamp.checksum(schemaPath));
            try {
                // The editor view has usually just parsed this very XSD for IntelliSense: share that
                // model (read-only) instead of running the whole schema pipeline a second time.
                var data = tab.view instanceof XmlEditorView xmlView ? xmlView.loadedSchemaData(xsd) : null;
                if (data == null) {
                    var service = new org.fxt.freexmltoolkit.service.XsdDocumentationService();
                    service.setXsdFilePath(xsd.getAbsolutePath());
                    service.setEagerSampleData(false);
                    service.processXsd(Boolean.TRUE);
                    data = service.xsdDocumentationData;
                }
                var adapter = new org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter();
                adapter.setXsdDocumentationData(data);
                // The V2 model of the same XSD: lets the inspector show (and, through the schema's
                // own tab, edit) the declaration behind an element. Best-effort like the provider.
                org.fxt.freexmltoolkit.controls.v2.model.XsdSchema model = null;
                try {
                    XsdUiParser.Result parsed = XsdUiParser.parseDetailed(
                            Files.readString(xsd.toPath(), StandardCharsets.UTF_8), xsd.toPath(), null);
                    model = parsed.schema();
                    stamps.putAll(parsed.dependencyStamps());
                    checksums.putAll(parsed.dependencyChecksums());
                } catch (Exception | LinkageError e) {
                    model = null; // a schema that does not parse is the user's; a parser bug is counted
                    org.fxt.freexmltoolkit.service.telemetry.Telemetry.reportUnlessUserInput(e, "editor.parse_bound_xsd");
                }
                var boundModel = model;
                Platform.runLater(() -> {
                    tab.boundSchemaModel = boundModel;
                    tab.xmlSchemaProvider = adapter;
                    if (tab.xmlEditorContext != null) {
                        tab.xmlEditorContext.setSchemaProvider(adapter);
                    }
                    // Binding the schema drops the caret-selected node; pick it up again so the
                    // inspector shows the schema facts without the user moving the caret.
                    tab.resolveCaretXmlNode();
                    if (tab.isSelected()) {
                        host.refreshSelectedNode();
                    }
                    host.xmlSchemaProviderRevision.set(host.xmlSchemaProviderRevision.get() + 1);
                });
            } catch (Exception e) {
                // schema-derived info is best-effort; ignore failures
            } finally {
                Platform.runLater(() -> tab.boundSchemaWatch = new BoundSchemaWatch(xsd, stamps, checksums));
            }
        });
    }

    /** After an XSD was saved: XML tabs bound to it reload their schema facts from the new file. */
    void reloadXmlSchemasBoundTo(Path xsd) {
        Path saved = xsd.toAbsolutePath().normalize();
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.schemaFile != null
                    && saved.equals(et.schemaFile.toPath().toAbsolutePath().normalize())) {
                refreshBoundSchema(et);
            }
        }
    }

    /**
     * Reads a tab's bound schema anew, for the view's IntelliSense first because the schema
     * provider shares what the view has loaded. A newer binding wins over this refresh.
     */
    void refreshBoundSchema(EditorTab tab) {
        File xsd = tab.schemaFile;
        if (xsd == null) {
            return;
        }
        long request = tab.schemaBindRequest.get();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            if (!acquireSchemaDetection(tab, () -> tab.schemaBindRequest.get() != request)) {
                return;
            }
            boolean loaded = false;
            try {
                loaded = tab.schemaBindRequest.get() == request && loadSchemaSafely(tab, xsd);
            } finally {
                tab.schemaDetecting.set(false);
            }
            boolean applied = loaded;
            Platform.runLater(() -> {
                if (EditorTab.isAbandoned(tab) || tab.schemaBindRequest.get() != request || !xsd.equals(tab.schemaFile)) {
                    return;
                }
                tab.view.invalidateIntelliSenseCache();
                host.publishSchemaStatus(tab, applied ? SchemaStatus.READY : SchemaStatus.ERROR);
                loadXmlSchemaProviderAsync(tab, xsd);
            });
        });
    }
}
