package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.v2.view.render.DiagramExportMenu;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ToastNotification;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions;
import org.fxt.freexmltoolkit.service.export.DiagramExporter;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * The shell's "Export View…" and "Copy View as Image" actions: finds the graphical view of the
 * active tab (XSD diagram, XML/JSON grid in Graphic mode, a tool tab's flame graph), asks for the
 * options ({@link ExportViewDialog}) and the file, runs {@link DiagramExporter} and reports the
 * result. The last options and folder are remembered ({@code export.view.*}).
 */
public final class ViewExport {

    private static final Logger logger = LogManager.getLogger(ViewExport.class);
    private static final String PREFIX = "export.view.";

    /** Test hook: shows the options dialog ({@code null} = the real dialog). */
    private static Function<ExportViewDialog, Optional<DiagramExportOptions>> dialogHandler;
    /** Test hook: picks the target file from the proposal ({@code null} = file chooser). */
    private static BiFunction<Window, Path, Path> fileChooser;

    private ViewExport() {
    }

    /** @param handler answers the options dialog in tests, {@code null} restores the dialog */
    public static void setDialogHandler(Function<ExportViewDialog, Optional<DiagramExportOptions>> handler) {
        dialogHandler = handler;
    }

    /** @param chooser picks the file in tests, {@code null} restores the file chooser */
    public static void setFileChooser(BiFunction<Window, Path, Path> chooser) {
        fileChooser = chooser;
    }

    // ------------------------------------------------------------------ finding the view

    /** @return the exportable graphical view of the active tab, or null */
    public static ExportableDiagram activeView(EditorHost host) {
        Tab tab = host.activeTabProperty().get();
        if (tab == null) {
            return null;
        }
        if (tab instanceof EditorTab editor) {
            if (editor.viewMode != ViewMode.GRAPHIC) {
                return null;
            }
            if (editor.supportsStructuredViews()) {
                return editor.xsdGraphView;
            }
            if (editor.xmlGridView != null && editor.xmlGridView.getCanvasView() != null
                    && editor.xmlGridView.getCanvasView().isVisible()) {
                return editor.xmlGridView.getCanvasView().exportable();
            }
            if (editor.jsonGridView != null && editor.jsonGridView.getCanvasView() != null) {
                return editor.jsonGridView.getCanvasView().exportable();
            }
            return null;
        }
        return find(tab.getContent());
    }

    /** @return the first visible exportable view inside a tool tab's content (breadth first), or null */
    static ExportableDiagram find(Node root) {
        Deque<Node> queue = new ArrayDeque<>();
        if (root != null) {
            queue.add(root);
        }
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (!node.isVisible()) {
                continue;
            }
            if (node instanceof ExportableDiagram diagram) {
                return diagram;
            }
            if (node instanceof GridCanvasView<?> grid) {
                return grid.exportable();
            }
            if (node instanceof Parent parent) {
                queue.addAll(parent.getChildrenUnmodifiable());
            }
        }
        return null;
    }

    /** @return the file name stem for the active tab's export */
    static String baseName(EditorHost host, ExportableDiagram diagram) {
        if (host.activeTabProperty().get() instanceof EditorTab editor) {
            String name = editor.document.getDisplayName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
        return diagram.exportTitle();
    }

    private static Path folder(EditorHost host) {
        if (host.activeTabProperty().get() instanceof EditorTab editor && editor.document.getPath() != null) {
            return editor.document.getPath().toAbsolutePath().getParent();
        }
        return null;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Lets the views' own context menus export through this class ({@link DiagramExportMenu}).
     *
     * @param host the editor host, for file names and folders of the active document
     */
    public static void installMenuHandler(EditorHost host) {
        DiagramExportMenu.setHandler(new DiagramExportMenu.Handler() {
            @Override
            public void export(ExportableDiagram diagram, Node anchor) {
                boolean active = isActive(host, diagram);
                ViewExport.export(diagram, active ? baseName(host, diagram) : diagram.exportTitle(),
                        active ? folder(host) : null, windowOf(anchor));
            }

            @Override
            public void copy(ExportableDiagram diagram, Node anchor) {
                ViewExport.copy(diagram, windowOf(anchor));
            }
        });
    }

    /** @return whether the diagram is the active tab's view (grids hand out a new adapter per call) */
    private static boolean isActive(EditorHost host, ExportableDiagram diagram) {
        return host.activeTabProperty().get() instanceof EditorTab editor && editor.viewMode == ViewMode.GRAPHIC
                && (diagram == editor.xsdGraphView || diagram.getClass() == classOf(activeView(host)));
    }

    private static Class<?> classOf(Object object) {
        return object == null ? null : object.getClass();
    }

    private static Window windowOf(Node anchor) {
        return anchor != null && anchor.getScene() != null ? anchor.getScene().getWindow() : null;
    }

    /** File → Export View…: the options dialog, then the file, for the active tab's view. */
    public static void exportActive(EditorHost host, Window owner) {
        ExportableDiagram diagram = activeView(host);
        if (diagram == null) {
            DialogHelper.showInformation(I18n.t("ExportViewDialog.title"), I18n.t("ViewExport.no-view.header"),
                    I18n.t("ViewExport.no-view.text"));
            return;
        }
        export(diagram, baseName(host, diagram), folder(host), owner);
    }

    /** Edit → Copy View as Image for the active tab's view. */
    public static void copyActive(EditorHost host, Window owner) {
        ExportableDiagram diagram = activeView(host);
        if (diagram == null) {
            DialogHelper.showInformation(I18n.t("ViewExport.copy.title"), I18n.t("ViewExport.no-view.header"),
                    I18n.t("ViewExport.no-view.text"));
            return;
        }
        copy(diagram, owner);
    }

    /**
     * Asks for the options and the file and exports the view.
     *
     * @param diagram  the view
     * @param baseName the proposed file name without extension
     * @param folder   the proposed folder (the document's), or null
     * @param owner    the window for dialogs and the result toast
     * @return completes with the written file, or null when cancelled
     */
    public static CompletableFuture<Path> export(ExportableDiagram diagram, String baseName, Path folder, Window owner) {
        ExportViewDialog dialog = new ExportViewDialog(diagram, loadOptions(), baseName);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        Optional<DiagramExportOptions> answer = dialogHandler != null ? dialogHandler.apply(dialog) : dialog.showAndWait();
        if (answer.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        DiagramExportOptions options = answer.get();
        saveOptions(options);

        String extension = options.format().extension();
        Path directory = lastFolder();
        if (directory == null) {
            directory = folder;
        }
        Path proposal = (directory != null ? directory : Path.of(System.getProperty("user.home")))
                .resolve(safeFileName(baseName) + "." + extension);
        Path file = fileChooser != null ? fileChooser.apply(owner, proposal) : chooseFile(owner, proposal, options.format());
        if (file == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            file = file.resolveSibling(file.getFileName() + "." + extension);
        }
        if (file.getParent() != null) {
            set("dir", file.getParent().toString());
        }
        Path target = file;
        return DiagramExporter.export(diagram, options, target, FxtGui.executorService, null)
                .whenComplete((written, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        reportError(I18n.t("ExportViewDialog.title"), error);
                    } else {
                        ToastNotification.success(owner, I18n.t("ViewExport.saved", target.getFileName()));
                    }
                }));
    }

    /**
     * Puts the view on the clipboard as an image with the last PNG options (area, scale, palette,
     * background, padding).
     */
    public static CompletableFuture<javafx.scene.image.Image> copy(ExportableDiagram diagram, Window owner) {
        DiagramExportOptions options = loadOptions().withFormat(DiagramExportOptions.Format.PNG);
        // The whole content of a large grid or diagram exceeds what the clipboard takes: copy the
        // visible area instead and say so, rather than failing
        boolean visibleOnly = options.area() == DiagramExportOptions.Area.WHOLE && !fitsClipboard(diagram, options);
        DiagramExportOptions used = visibleOnly ? options.withArea(DiagramExportOptions.Area.VISIBLE) : options;
        return DiagramExporter.copyToClipboard(diagram, used, null)
                .whenComplete((image, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        reportError(I18n.t("ViewExport.copy.title"), error);
                    } else {
                        ToastNotification.success(owner,
                                I18n.t(visibleOnly ? "ViewExport.copied-visible" : "ViewExport.copied"));
                    }
                }));
    }

    /** @return whether the view's image in these options fits the clipboard (an unknown size counts as fitting) */
    static boolean fitsClipboard(ExportableDiagram diagram, DiagramExportOptions options) {
        try {
            int[] size = DiagramExporter.pixelSize(DiagramExporter.exportRegion(diagram, options), options.scale());
            return (long) size[0] * size[1] <= DiagramExporter.MAX_CLIPBOARD_PIXELS;
        } catch (RuntimeException e) {
            return true; // the copy itself reports why it cannot export
        }
    }

    private static void reportError(String title, Throwable error) {
        Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                ? error.getCause() : error;
        if (cause instanceof DiagramExporter.DiagramExportException) {
            // An expected limit (empty view, too large, no memory) explained to the user, not an app error
            DialogHelper.showErrorAlert(title, I18n.t("ViewExport.failed"), cause.getMessage());
        } else {
            logger.error("View export failed", cause);
            DialogHelper.showError(title, I18n.t("ViewExport.failed"), String.valueOf(cause.getMessage()), cause);
        }
    }

    private static Path chooseFile(Window owner, Path proposal, DiagramExportOptions.Format format) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("ExportViewDialog.title"));
        String pattern = "*." + format.extension();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                I18n.t("ExportViewDialog.format." + format.name()) + " (" + pattern + ")", pattern));
        if (proposal.getParent() != null && Files.isDirectory(proposal.getParent())) {
            chooser.setInitialDirectory(proposal.getParent().toFile());
        }
        chooser.setInitialFileName(proposal.getFileName().toString());
        File chosen = chooser.showSaveDialog(owner);
        return chosen == null ? null : chosen.toPath();
    }

    /** @return the name with characters that file systems reject replaced */
    static String safeFileName(String name) {
        String safe = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return safe.isEmpty() ? "diagram" : safe;
    }

    // ------------------------------------------------------------------ remembered options

    /** @return the last options used (defaults: PNG, whole view, 2×, light, theme background, 16 px) */
    static DiagramExportOptions loadOptions() {
        DiagramExportOptions defaults = DiagramExportOptions.defaults(DiagramExportOptions.Format.PNG);
        try {
            return new DiagramExportOptions(
                    enumValue(DiagramExportOptions.Format.class, get("format"), defaults.format()),
                    enumValue(DiagramExportOptions.Area.class, get("area"), defaults.area()),
                    doubleValue(get("scale"), defaults.scale()),
                    enumValue(DiagramExportOptions.ThemeChoice.class, get("theme"), defaults.theme()),
                    enumValue(DiagramExportOptions.Background.class, get("background"), defaults.background()),
                    doubleValue(get("padding"), defaults.padding()),
                    Boolean.parseBoolean(get("embedFonts")),
                    enumValue(DiagramExportOptions.PageSize.class, get("pageSize"), defaults.pageSize()), "");
        } catch (RuntimeException e) {
            return defaults;
        }
    }

    static void saveOptions(DiagramExportOptions options) {
        set("format", options.format().name());
        set("area", options.area().name());
        set("scale", Double.toString(options.scale()));
        set("theme", options.theme().name());
        set("background", options.background().name());
        set("padding", Double.toString(options.padding()));
        set("embedFonts", Boolean.toString(options.embedFonts()));
        set("pageSize", options.pageSize().name());
    }

    private static Path lastFolder() {
        String dir = get("dir");
        if (dir == null || dir.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(dir);
            return Files.isDirectory(path) ? path : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static double doubleValue(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String get(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(PREFIX + key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void set(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(PREFIX + key, value);
        } catch (RuntimeException e) {
            logger.debug("Export option {} not saved: {}", key, e.getMessage());
        }
    }
}
