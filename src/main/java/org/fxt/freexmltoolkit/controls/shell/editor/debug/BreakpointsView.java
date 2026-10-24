package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;

/**
 * Lists the session's breakpoints with Remove / Clear all; double-click jumps to the line,
 * {@code Delete} removes the selected one. Shown as the "Breakpoints" tab of the DEBUG pane;
 * {@link #setOnCountChanged} feeds the tab's badge.
 */
public class BreakpointsView extends VBox {

    private final ListView<Breakpoint> list = new ListView<>();
    private final DebugSession session;
    private java.util.List<javafx.scene.control.CheckMenuItem> breakOnItems = java.util.List.of();
    private IntConsumer onJumpToLine = line -> { };
    private java.util.function.Consumer<Breakpoint> onJumpToBreakpoint = bp -> { };
    private Runnable onChanged = () -> { };
    private Runnable onCountChanged = () -> { };
    private final ListView<org.fxt.freexmltoolkit.debugger.SymbolBreakpoint> symbolList = new ListView<>();
    private final Label symbolTitle = new Label("Templates & functions");
    private java.util.function.Supplier<String> stylesheetText = () -> "";
    private org.fxt.freexmltoolkit.debugger.ui.SymbolBreakpointDialog.Prompt symbolPrompt =
            org.fxt.freexmltoolkit.debugger.ui.SymbolBreakpointDialog.DEFAULT;
    private org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.Prompt propertiesPrompt =
            org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.DEFAULT;

    public BreakpointsView(DebugSession session) {
        this.session = session;
        getStyleClass().add("fxt-debug-panel");

        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            private final javafx.scene.control.CheckBox enabled = new javafx.scene.control.CheckBox();
            /** File header above the first breakpoint of each file (the list is sorted by file). */
            private final Label header = new Label();
            private final Label text = new Label();
            private final VBox box = new VBox(2, header, new HBox(6, enabled, text));

            {
                header.getStyleClass().add("fxt-bp-file");
                enabled.setTooltip(new javafx.scene.control.Tooltip("Enabled — untick to keep the breakpoint without pausing"));
                enabled.setOnAction(e -> {
                    Breakpoint bp = getItem();
                    if (bp != null) {
                        setEnabled(bp, enabled.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(Breakpoint bp, boolean empty) {
                super.updateItem(bp, empty);
                setText(null);
                text.getStyleClass().removeAll("fxt-bp-disabled", "fxt-bp-unreached");
                getStyleClass().removeAll("fxt-bp-disabled", "fxt-bp-unreached");
                if (empty || bp == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                int index = getIndex();
                boolean firstOfFile = index == 0 || index >= getListView().getItems().size()
                        || !getListView().getItems().get(index - 1).filePath().equals(bp.filePath());
                header.setText(fileHeader(bp.filePath()));
                header.setVisible(firstOfFile);
                header.setManaged(firstOfFile);
                enabled.setSelected(bp.enabled());
                if (!bp.enabled()) {
                    text.getStyleClass().add("fxt-bp-disabled");
                }
                int hits = session.hitCount(bp);
                boolean unreached = session.notReachedInLastRun(bp.filePath(), bp.lineNumber());
                if (unreached) {
                    text.getStyleClass().add("fxt-bp-unreached");
                }
                text.setText((unreached ? "⚠ " : "") + "line " + bp.lineNumber()
                        + (bp.hasCondition() ? "   if  " + bp.condition() : "")
                        + (bp.hit().isSet() ? "   ·  " + bp.hit().describe() : "")
                        + (bp.isLogpoint() ? "   ·  log \"" + bp.logMessage() + "\"" + (bp.suspend() ? "" : " (no pause)") : "")
                        + (hits > 0 ? "   ·  " + hits + (hits == 1 ? " hit" : " hits") : "")
                        + (unreached ? "   ·  not reached in the last run" : ""));
                setTooltip(unreached ? new javafx.scene.control.Tooltip(
                        "The last run never reached this line: it has no instruction Saxon traces "
                                + "(e.g. a closing tag or a comment), or the code there did not run.") : null);
                setGraphic(box);
            }
        });
        list.setContextMenu(listMenu());
        list.setPlaceholder(new Label("No breakpoints."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Breakpoint bp = list.getSelectionModel().getSelectedItem();
                if (bp != null) {
                    onJumpToLine.accept(bp.lineNumber());
                    onJumpToBreakpoint.accept(bp);
                }
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                removeSelected();
                e.consume();
            }
        });

        Button remove = DebugToolbar.action("Remove", "bi-dash-lg",
                "Remove the selected breakpoint (Delete)");
        remove.setOnAction(e -> removeSelected());
        Button condition = DebugToolbar.action("Edit…", "bi-sliders",
                "Condition and hit count of the selected breakpoint");
        condition.setOnAction(e -> editSelected());
        Button clear = DebugToolbar.action("Clear all", "bi-trash",
                "Remove every breakpoint of the session");
        clear.setOnAction(e -> {
            session.clearBreakpoints();
            refresh();
            onChanged.run();
        });

        javafx.scene.control.MenuButton breakOn = breakOnMenu();
        Button symbol = DebugToolbar.action("Template…", "bi-braces-asterisk",
                "Pause whenever a template rule, named template, function or mode is entered — at any line");
        symbol.setOnAction(e -> addSymbolBreakpoint());
        HBox toolbar = new HBox(remove, condition, symbol, clear, breakOn);
        toolbar.getStyleClass().add("fxt-debug-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(list, Priority.ALWAYS);
        symbolTitle.getStyleClass().add("fxt-output-status");
        symbolList.setPrefHeight(90);
        symbolList.setMinHeight(60);
        symbolList.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            private final javafx.scene.control.CheckBox enabled = new javafx.scene.control.CheckBox();

            {
                enabled.setOnAction(e -> {
                    org.fxt.freexmltoolkit.debugger.SymbolBreakpoint s = getItem();
                    if (s != null) {
                        session.addSymbolBreakpoint(s.withEnabled(enabled.isSelected()));
                        refresh();
                        onChanged.run();
                    }
                });
            }

            @Override
            protected void updateItem(org.fxt.freexmltoolkit.debugger.SymbolBreakpoint s, boolean empty) {
                super.updateItem(s, empty);
                getStyleClass().remove("fxt-bp-disabled");
                if (empty || s == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                enabled.setSelected(s.enabled());
                setGraphic(enabled);
                setText(s.describe());
                if (!s.enabled()) {
                    getStyleClass().add("fxt-bp-disabled");
                }
            }
        });
        symbolList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                org.fxt.freexmltoolkit.debugger.SymbolBreakpoint s = symbolList.getSelectionModel().getSelectedItem();
                if (s != null) {
                    session.removeSymbolBreakpoint(s);
                    refresh();
                    onChanged.run();
                }
                e.consume();
            }
        });
        getChildren().addAll(toolbar, list, symbolTitle, symbolList);
        refresh();
    }

    /** Asks for a template / function / mode breakpoint and adds it (toolbar Template…). */
    public void addSymbolBreakpoint() {
        java.util.Map<org.fxt.freexmltoolkit.debugger.SymbolBreakpoint.Kind, List<String>> suggestions =
                org.fxt.freexmltoolkit.debugger.StylesheetSymbols.scan(stylesheetText.get());
        symbolPrompt.ask(getScene() == null ? null : getScene().getWindow(), suggestions).ifPresent(s -> {
            session.addSymbolBreakpoint(s);
            refresh();
            onChanged.run();
        });
    }

    /** Supplies the stylesheet text(s) the Template… dialog suggests names from. */
    public void setStylesheetSupplier(java.util.function.Supplier<String> supplier) {
        this.stylesheetText = supplier == null ? () -> "" : supplier;
    }

    /** Replaces the Template… prompt (tests). */
    public void setSymbolPrompt(org.fxt.freexmltoolkit.debugger.ui.SymbolBreakpointDialog.Prompt prompt) {
        this.symbolPrompt = prompt == null ? org.fxt.freexmltoolkit.debugger.ui.SymbolBreakpointDialog.DEFAULT : prompt;
    }

    /** @return the number of line and template / function / mode breakpoints (tab badge). */
    public int getTotalCount() {
        return list.getItems().size() + symbolList.getItems().size();
    }

    /** @return the symbol breakpoints as listed (tests). */
    public List<org.fxt.freexmltoolkit.debugger.SymbolBreakpoint> getSymbolItems() {
        return List.copyOf(symbolList.getItems());
    }

    /** Preference keys of the "Break on" options (defaults: errors and terminate on). */
    static final String BREAK_ON_ERRORS_KEY = "debug.breakOn.errors";
    static final String BREAK_ON_TERMINATE_KEY = "debug.breakOn.terminate";
    static final String BREAK_ON_MESSAGES_KEY = "debug.breakOn.messages";
    static final String BREAK_ON_ERROR_CODES_KEY = "debug.breakOn.errorCodes";

    /** Asks for the error-code list (current text → new text, empty = all); replaceable for tests. */
    private java.util.function.Function<String, java.util.Optional<String>> errorCodesPrompt = current -> {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(current);
        dialog.setTitle("Break on Error Codes");
        dialog.setHeaderText("Pause only on these dynamic errors");
        dialog.setContentText("Error codes (comma-separated, XTDE* for a prefix; empty = every error):");
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        try {
            dialog.getDialogPane().getStylesheets().addAll(org.fxt.freexmltoolkit.util.DialogHelper.getThemeStylesheets());
        } catch (RuntimeException ignored) {
            // unthemed is fine
        }
        return dialog.showAndWait();
    };
    private javafx.scene.control.MenuItem errorCodesItem;

    /** Replaces the error-code prompt (tests). */
    void setErrorCodesPrompt(java.util.function.Function<String, java.util.Optional<String>> prompt) {
        this.errorCodesPrompt = prompt;
    }

    /** "Only error codes…": edits the list, applies it to the session and remembers it. */
    void editErrorCodes() {
        String current = String.join(", ", session.getBreakOnErrorCodes());
        errorCodesPrompt.apply(current).ifPresent(text -> {
            session.setBreakOnErrorCodes(org.fxt.freexmltoolkit.debugger.DebugSession.parseErrorCodes(text));
            try {
                org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                        .set(BREAK_ON_ERROR_CODES_KEY, String.join(",", session.getBreakOnErrorCodes()));
            } catch (Throwable ignored) {
                // the choice holds for this session
            }
            updateErrorCodesText();
        });
    }

    private void updateErrorCodesText() {
        java.util.List<String> codes = session.getBreakOnErrorCodes();
        errorCodesItem.setText(codes.isEmpty() ? "Only error codes… (all)" : "Only error codes: " + String.join(", ", codes));
    }

    /** The "Only error codes" menu item (tests). */
    javafx.scene.control.MenuItem errorCodesItem() {
        return errorCodesItem;
    }

    /**
     * The toolbar's "Break on" menu: pause on dynamic errors (with the state at the failing
     * instruction), before {@code xsl:message terminate="yes"}, or before every
     * {@code xsl:message}. The choices are saved and applied to every session.
     */
    private javafx.scene.control.MenuButton breakOnMenu() {
        javafx.scene.control.CheckMenuItem errors = breakOnItem("Dynamic errors", BREAK_ON_ERRORS_KEY, true,
                session::setBreakOnErrors);
        javafx.scene.control.CheckMenuItem terminate = breakOnItem("xsl:message terminate=\"yes\"",
                BREAK_ON_TERMINATE_KEY, true, session::setBreakOnTerminate);
        javafx.scene.control.CheckMenuItem messages = breakOnItem("Every xsl:message", BREAK_ON_MESSAGES_KEY, false,
                session::setBreakOnMessages);
        org.fxt.freexmltoolkit.controls.icons.IconifyIcon icon =
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-exclamation-octagon");
        icon.setIconSize(14);
        try {
            session.setBreakOnErrorCodes(org.fxt.freexmltoolkit.debugger.DebugSession.parseErrorCodes(
                    org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                            .get(BREAK_ON_ERROR_CODES_KEY)));
        } catch (Throwable ignored) {
            // no stored list: every error
        }
        errorCodesItem = new javafx.scene.control.MenuItem();
        errorCodesItem.setId("debug-break-error-codes");
        errorCodesItem.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-funnel"),
                org.fxt.freexmltoolkit.controls.theme.ActionColor.NEUTRAL));
        errorCodesItem.setOnAction(e -> editErrorCodes());
        errorCodesItem.disableProperty().bind(errors.selectedProperty().not());
        updateErrorCodesText();
        javafx.scene.control.MenuButton menu = new javafx.scene.control.MenuButton("Break on", icon,
                errors, errorCodesItem, terminate, messages);
        menu.getStyleClass().addAll("fxt-sp-action", "fxt-output-action");
        menu.setTooltip(new javafx.scene.control.Tooltip(
                "Pause on errors or xsl:message, independent of line breakpoints (Mute silences these too)"));
        breakOnItems = java.util.List.of(errors, terminate, messages);
        return menu;
    }

    private static javafx.scene.control.CheckMenuItem breakOnItem(String text, String key, boolean defaultOn,
                                                                    java.util.function.Consumer<Boolean> apply) {
        boolean on = readFlag(key, defaultOn);
        apply.accept(on);
        javafx.scene.control.CheckMenuItem item = new javafx.scene.control.CheckMenuItem(text);
        item.setSelected(on);
        item.selectedProperty().addListener((obs, old, value) -> {
            apply.accept(value);
            try {
                org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                        .set(key, Boolean.toString(value));
            } catch (Throwable ignored) {
                // properties service unavailable — the choice holds for this session
            }
        });
        return item;
    }

    private static boolean readFlag(String key, boolean defaultValue) {
        try {
            String value = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(key);
            return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.strip());
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    /** The "Break on" items in menu order: errors, terminate, every message (tests). */
    java.util.List<javafx.scene.control.CheckMenuItem> breakOnItems() {
        return breakOnItems;
    }

    /**
     * Re-reads the breakpoints (and their hit counts) from the session into the list, keeping
     * the selected file:line selected.
     */
    public final void refresh() {
        Breakpoint selected = list.getSelectionModel().getSelectedItem();
        List<Breakpoint> sorted = new ArrayList<>(session.getBreakpoints());
        sorted.sort(java.util.Comparator.comparing(Breakpoint::filePath)
                .thenComparingInt(Breakpoint::lineNumber));
        list.getSelectionModel().clearSelection();
        list.getItems().setAll(sorted);
        if (selected != null) {
            for (int i = 0; i < sorted.size(); i++) {
                Breakpoint bp = sorted.get(i);
                if (bp.filePath().equals(selected.filePath()) && bp.lineNumber() == selected.lineNumber()) {
                    list.getSelectionModel().select(i);
                    break;
                }
            }
        }
        list.refresh(); // hit counts changed although the breakpoints are equal
        symbolList.getItems().setAll(session.getSymbolBreakpoints());
        boolean symbols = !symbolList.getItems().isEmpty();
        symbolTitle.setVisible(symbols);
        symbolTitle.setManaged(symbols);
        symbolList.setVisible(symbols);
        symbolList.setManaged(symbols);
        onCountChanged.run();
    }

    /** @return the file header: the file name and how many breakpoints it has. */
    private String fileHeader(String filePath) {
        long count = list.getItems().stream().filter(b -> b.filePath().equals(filePath)).count();
        return (filePath.isEmpty() ? "(this stylesheet)" : shortName(filePath)) + "  (" + count + ")";
    }

    /** Row menu: per-file enable / disable / remove, and Export / Import of all breakpoints. */
    private javafx.scene.control.ContextMenu listMenu() {
        javafx.scene.control.MenuItem enableFile = menuItem("Enable All in File", "bi-check2-square",
                org.fxt.freexmltoolkit.controls.theme.ActionColor.CREATE, () -> setEnabledInFile(true));
        javafx.scene.control.MenuItem disableFile = menuItem("Disable All in File", "bi-slash-circle",
                org.fxt.freexmltoolkit.controls.theme.ActionColor.NEUTRAL, () -> setEnabledInFile(false));
        javafx.scene.control.MenuItem removeFile = menuItem("Remove All in File", "bi-trash",
                org.fxt.freexmltoolkit.controls.theme.ActionColor.DELETE, this::removeAllInFile);
        javafx.scene.control.MenuItem export = menuItem("Export Breakpoints…", "bi-box-arrow-up",
                org.fxt.freexmltoolkit.controls.theme.ActionColor.TOOL, this::exportWithChooser);
        javafx.scene.control.MenuItem importItem = menuItem("Import Breakpoints…", "bi-box-arrow-in-down",
                org.fxt.freexmltoolkit.controls.theme.ActionColor.TOOL, this::importWithChooser);
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu(enableFile, disableFile, removeFile,
                new javafx.scene.control.SeparatorMenuItem(), export, importItem);
        menu.setOnShowing(e -> {
            Breakpoint bp = list.getSelectionModel().getSelectedItem();
            boolean none = bp == null;
            String file = none ? "" : (bp.filePath().isEmpty() ? "this stylesheet" : shortName(bp.filePath()));
            enableFile.setText(none ? "Enable All in File" : "Enable All in " + file);
            disableFile.setText(none ? "Disable All in File" : "Disable All in " + file);
            removeFile.setText(none ? "Remove All in File" : "Remove All in " + file);
            enableFile.setDisable(none);
            disableFile.setDisable(none);
            removeFile.setDisable(none);
            export.setDisable(session.getBreakpoints().isEmpty() && session.getSymbolBreakpoints().isEmpty());
        });
        return menu;
    }

    private static javafx.scene.control.MenuItem menuItem(String text, String icon,
            org.fxt.freexmltoolkit.controls.theme.ActionColor color, Runnable action) {
        org.fxt.freexmltoolkit.controls.icons.IconifyIcon graphic = new org.fxt.freexmltoolkit.controls.icons.IconifyIcon(icon);
        graphic.setIconSize(16);
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(text,
                org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(graphic, color));
        item.setOnAction(e -> action.run());
        return item;
    }

    /** Enables or disables every breakpoint of the selected row's file. */
    public void setEnabledInFile(boolean on) {
        Breakpoint selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        for (Breakpoint bp : session.getBreakpoints()) {
            if (bp.filePath().equals(selected.filePath())) {
                session.setEnabled(bp.filePath(), bp.lineNumber(), on);
            }
        }
        refresh();
        onChanged.run();
    }

    /** Removes every breakpoint of the selected row's file. */
    public void removeAllInFile() {
        Breakpoint selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        for (Breakpoint bp : session.getBreakpoints()) {
            if (bp.filePath().equals(selected.filePath())) {
                session.removeBreakpoint(bp.filePath(), bp.lineNumber());
            }
        }
        refresh();
        onChanged.run();
    }

    /** @return all line and template / function / mode breakpoints in the export format. */
    public String exportText() {
        return org.fxt.freexmltoolkit.debugger.BreakpointExport.write(session.getBreakpoints(),
                session.getSymbolBreakpoints());
    }

    /**
     * Adds the breakpoints of an export to the session (same file and line: replaced).
     *
     * @return how many breakpoints were imported
     * @throws IllegalArgumentException when the text is no breakpoint export
     */
    public int importText(String text) {
        var content = org.fxt.freexmltoolkit.debugger.BreakpointExport.read(text);
        content.breakpoints().forEach(session::addBreakpoint);
        content.symbols().forEach(session::addSymbolBreakpoint);
        refresh();
        onChanged.run();
        return content.breakpoints().size() + content.symbols().size();
    }

    private void exportWithChooser() {
        javafx.stage.FileChooser chooser = breakpointFileChooser("Export Breakpoints");
        chooser.setInitialFileName("breakpoints.txt");
        java.io.File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), exportText(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            showError("Could not export the breakpoints: " + e.getMessage());
        }
    }

    private void importWithChooser() {
        java.io.File file = breakpointFileChooser("Import Breakpoints")
                .showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            importText(java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | IllegalArgumentException e) {
            showError("Could not import the breakpoints: " + e.getMessage());
        }
    }

    private static javafx.stage.FileChooser breakpointFileChooser(String title) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Breakpoint files (*.txt)", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser;
    }

    private void showError(String message) {
        javafx.scene.control.Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                javafx.scene.control.Alert.AlertType.ERROR, "Breakpoints", "Breakpoints", message);
        if (getScene() != null) {
            alert.initOwner(getScene().getWindow());
        }
        alert.showAndWait();
    }

    /** Enables or disables {@code bp} (row checkbox) and tells the host (gutter, persistence). */
    public void setEnabled(Breakpoint bp, boolean enabled) {
        session.setEnabled(bp.filePath(), bp.lineNumber(), enabled);
        refresh();
        onChanged.run();
    }

    /** @return whether the row at {@code index} is enabled (tests). */
    public boolean isEnabledAt(int index) {
        return list.getItems().get(index).enabled();
    }

    /** Removes the selected breakpoint from the session, if any (Remove button, Delete key). */
    public void removeSelected() {
        Breakpoint bp = list.getSelectionModel().getSelectedItem();
        if (bp != null) {
            session.removeBreakpoint(bp.filePath(), bp.lineNumber());
            refresh();
            onChanged.run();
        }
    }

    /** Replaces the properties prompt (tests). */
    public void setPropertiesPrompt(org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.Prompt prompt) {
        this.propertiesPrompt = prompt == null
                ? org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.DEFAULT : prompt;
    }

    /** Edits condition and hit count of the selected breakpoint (toolbar Edit…). */
    public void editSelected() {
        Breakpoint bp = list.getSelectionModel().getSelectedItem();
        if (bp == null) {
            return;
        }
        String where = (bp.filePath().isEmpty() ? "" : shortName(bp.filePath()) + ", ") + "line " + bp.lineNumber();
        propertiesPrompt.ask(getScene() == null ? null : getScene().getWindow(), where,
                        org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.Settings.of(bp),
                        session.getState() == DebugSession.State.IDLE ? -1 : session.hitCount(bp))
                .ifPresent(settings -> {
                    session.updateBreakpoint(bp.filePath(), bp.lineNumber(), settings::applyTo);
                    refresh();
                    onChanged.run();
                });
    }

    /** @return whether the row at {@code index} is marked "not reached in the last run" (tests). */
    public boolean isUnreachedAt(int index) {
        Breakpoint bp = list.getItems().get(index);
        return session.notReachedInLastRun(bp.filePath(), bp.lineNumber());
    }

    /** @return the row text at {@code index} as shown (tests). */
    public String getRowText(int index) {
        Breakpoint bp = list.getItems().get(index);
        int hits = session.hitCount(bp);
        return bp.condition() + "|" + bp.hit().format() + "|" + hits;
    }

    /** @return the condition of the row at {@code index} (tests). */
    public String getConditionAt(int index) {
        return list.getItems().get(index).condition();
    }

    /** Selects the breakpoint at {@code index} (tests, keyboard flows). */
    public void select(int index) {
        list.getSelectionModel().clearAndSelect(index);
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    public void setOnJumpToLine(IntConsumer handler) {
        this.onJumpToLine = handler == null ? line -> { } : handler;
    }

    /** Breakpoint jump handler: receives the breakpoint so its own module can be opened. */
    public void setOnJumpToBreakpoint(java.util.function.Consumer<Breakpoint> handler) {
        this.onJumpToBreakpoint = handler == null ? bp -> { } : handler;
    }

    /** Invoked after the user removes/clears breakpoints (so the gutter can refresh). */
    public void setOnChanged(Runnable handler) {
        this.onChanged = handler == null ? () -> { } : handler;
    }

    /** Invoked after every change of the row count (tab badge). */
    public void setOnCountChanged(Runnable handler) {
        this.onCountChanged = handler == null ? () -> { } : handler;
    }

    /** The list, for tests that drive it with key events. */
    ListView<Breakpoint> list() {
        return list;
    }

    private static String shortName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
