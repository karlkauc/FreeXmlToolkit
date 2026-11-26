package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Assignment;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Candidate;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Entry;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Party;
import org.fxt.freexmltoolkit.service.xmledit.TextEdit;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * FundsXML panel → Documents: a table of the {@code Documents/Document} elements of the active document with
 * Open Link, Save Embedded Document, Embed File, Embed Linked Document and Replace by Link. Reading and planning
 * run in the background; changes go into the editor as one undo step each, when the document did not change
 * meanwhile, and the table is read again.
 */
public final class FundsXmlDocumentsDialog {

    /** Test hook (scenarios, videos): picks the file to embed instead of a file chooser ({@code null}: cancelled). */
    public static volatile Function<String, File> openFileChooser;
    /** Test hook: picks the target of Save Embedded Document (gets the proposed name) instead of a file chooser. */
    public static volatile BiFunction<String, String, File> saveFileChooser;

    private final EditorHost editorHost;
    private final Dialog<Void> dialog;
    private final TableView<Entry> table = new TableView<>();
    private final Label message = new Label();
    private final Button openLink = button("documents-open-link", "FundsXmlPanel.documents-open-link");
    private final Button save = button("documents-save", "FundsXmlPanel.documents-save");
    private final Button embed = button("documents-embed", "FundsXmlPanel.documents-embed");
    private final Button embedLinked = button("documents-embed-linked", "FundsXmlPanel.documents-embed-linked");
    private final Button replaceByLink = button("documents-replace-by-link", "FundsXmlPanel.documents-replace-by-link");
    private List<Candidate> candidates = List.of();
    private boolean busy;
    /** The document (tab and revision) the listed entries were read from; their indexes are only valid there. */
    private EditorHost.ValidationTarget listed;

    private FundsXmlDocumentsDialog(EditorHost editorHost) {
        this.editorHost = editorHost;
        dialog = DialogHelper.createDialog(I18n.t("FundsXmlPanel.documents-title"), null, "bi-file-earmark-richtext");
        dialog.setResizable(true);
        dialog.getDialogPane().setId("documents-dialog");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType(I18n.t("FundsXmlPanel.documents-close"),
                ButtonBar.ButtonData.CANCEL_CLOSE));

        table.setId("documents-table");
        table.setPlaceholder(new Label(I18n.t("FundsXmlPanel.documents-empty")));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("FundsXmlPanel.documents-col-type", 120, Entry::type));
        table.getColumns().add(column("FundsXmlPanel.documents-col-name", 210, Entry::name));
        table.getColumns().add(column("FundsXmlPanel.documents-col-language", 70, Entry::language));
        table.getColumns().add(column("FundsXmlPanel.documents-col-assigned", 300, FundsXmlDocumentsDialog::assignedTo));
        table.getColumns().add(column("FundsXmlPanel.documents-col-storage", 110, FundsXmlDocumentsDialog::storage));
        table.getColumns().add(column("FundsXmlPanel.documents-col-size", 120, FundsXmlDocumentsDialog::size));
        table.setPrefSize(1040, 300);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateButtons());

        openLink.setOnAction(e -> onOpenLink());
        save.setOnAction(e -> onSave());
        embed.setOnAction(e -> onEmbed());
        embedLinked.setOnAction(e -> onEmbedLinked());
        replaceByLink.setOnAction(e -> onReplaceByLink());
        FlowPane buttons = new FlowPane(8, 8, openLink, save, embed, embedLinked, replaceByLink);
        message.setId("documents-message");
        message.setWrapText(true);
        message.getStyleClass().add("fxt-placeholder-text");
        VBox content = new VBox(10, table, buttons, message);
        content.setPadding(new Insets(4, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
    }

    /** Shows the dialog for the active document with entries already read (FX thread). */
    static void show(EditorHost editorHost, List<Entry> entries, List<Candidate> candidates) {
        FundsXmlDocumentsDialog d = new FundsXmlDocumentsDialog(editorHost);
        d.listed = editorHost.validationTarget();
        d.setEntries(entries, candidates);
        d.dialog.show();
    }

    private static Button button(String id, String key) {
        Button b = new Button(I18n.t(key));
        b.setId(id);
        return b;
    }

    private static TableColumn<Entry, String> column(String key, double width, Function<Entry, String> value) {
        TableColumn<Entry, String> c = new TableColumn<>(I18n.t(key));
        c.setPrefWidth(width);
        c.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return c;
    }

    static String assignedTo(Entry e) {
        return e.assignments().stream().map(a -> level(a.level(), a.parties().size()) + " " + a.parties().stream()
                        .map(p -> p.main() == null ? "?" : p.main().value()).collect(Collectors.joining(", ")))
                .collect(Collectors.joining("; "));
    }

    private static String level(FundsXmlDocuments.Level level, int count) {
        return switch (level) {
            case FUND -> I18n.t("FundsXmlPanel.documents-level-fund");
            case SUBFUND -> I18n.t(count > 1 ? "FundsXmlPanel.documents-level-subfunds" : "FundsXmlPanel.documents-level-subfund");
            case SHARE_CLASS -> I18n.t(count > 1 ? "FundsXmlPanel.documents-level-share-classes"
                    : "FundsXmlPanel.documents-level-share-class");
        };
    }

    static String storage(Entry e) {
        if (e.embedded() && e.linked()) {
            return I18n.t("FundsXmlPanel.documents-embedded") + " + " + I18n.t("FundsXmlPanel.documents-linked");
        }
        return e.embedded() ? I18n.t("FundsXmlPanel.documents-embedded")
                : e.linked() ? I18n.t("FundsXmlPanel.documents-linked") : I18n.t("FundsXmlPanel.documents-none");
    }

    static String size(Entry e) {
        if (e.sizeMismatch()) {
            return I18n.t("FundsXmlPanel.documents-size-mismatch", bytes(e.decodedSize()), bytes(e.sizeInBytes()));
        }
        Long size = e.sizeInBytes() != null ? e.sizeInBytes() : e.decodedSize();
        return size == null ? "" : bytes(size);
    }

    private static String bytes(long n) {
        return I18n.t("FundsXmlPanel.documents-bytes", String.format("%,d", n));
    }

    private void setEntries(List<Entry> entries, List<Candidate> candidates) {
        int selected = table.getSelectionModel().getSelectedIndex();
        this.candidates = candidates;
        table.setItems(FXCollections.observableArrayList(entries));
        if (selected >= 0 && selected < entries.size()) {
            table.getSelectionModel().select(selected);
        }
        long linked = entries.stream().filter(Entry::linked).count();
        long embedded = entries.stream().filter(Entry::embedded).count();
        dialog.setHeaderText(I18n.t("FundsXmlPanel.documents-header", entries.size(), linked, embedded));
        updateButtons();
    }

    private void updateButtons() {
        Entry e = table.getSelectionModel().getSelectedItem();
        openLink.setDisable(busy || e == null || !e.linked());
        save.setDisable(busy || e == null || !e.embedded());
        embed.setDisable(busy);
        embedLinked.setDisable(busy || e == null || !e.linked() || e.embedded());
        replaceByLink.setDisable(busy || e == null || !e.embedded());
    }

    private Window window() {
        return dialog.getDialogPane().getScene().getWindow();
    }

    private void info(String text) {
        message.setText(text);
    }

    /** Runs {@code work} in the background with the buttons disabled, then {@code done} on the FX thread. */
    private <T> void background(String working, Callable<T> work, Consumer<T> done) {
        busy = true;
        updateButtons();
        info(working);
        FxtGui.executorService.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> {
                    busy = false;
                    updateButtons();
                    done.accept(result);
                });
            } catch (Exception | OutOfMemoryError ex) {
                Platform.runLater(() -> {
                    busy = false;
                    updateButtons();
                    info(I18n.t("FundsXmlPanel.documents-change-failed") + " " + ex.getMessage());
                });
            }
        });
    }

    // ---- actions -------------------------------------------------------------------------------------------

    /**
     * Whether the active document is still the one the list was read from. Another tab: nothing happens (the
     * dialog stays non-modal); the same tab edited since: the list is read again, as the entries' indexes may
     * have moved.
     */
    private boolean stillListed() {
        EditorHost.ValidationTarget now = editorHost.validationTarget();
        if (listed == null || now == null || now.tab() != listed.tab()) {
            info(I18n.t("FundsXmlPanel.documents-other-tab"));
            return false;
        }
        if (now.revision() != listed.revision()) {
            reload(I18n.t("FundsXmlPanel.documents-reread"));
            return false;
        }
        return true;
    }

    /** Only web addresses are opened: a link in a delivered file must not start a program or open a share. */
    static boolean openableLink(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.strip().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private void onOpenLink() {
        Entry e = table.getSelectionModel().getSelectedItem();
        if (e == null || !e.linked()) {
            return;
        }
        Consumer<Exception> failed = ex -> info(I18n.t("FundsXmlPanel.could-not-open-browser-2", ex.getMessage()));
        if (!openableLink(e.url())) {
            info(I18n.t("FundsXmlPanel.documents-link-refused", e.url()));
            return;
        }
        try {
            org.fxt.freexmltoolkit.util.DesktopActions.browse(new java.net.URI(e.url().strip().replace(" ", "%20")), failed);
        } catch (java.net.URISyntaxException ex) {
            failed.accept(ex);
        }
    }

    private void onSave() {
        Entry e = table.getSelectionModel().getSelectedItem();
        String xml = editorHost.getActiveText().orElse(null);
        if (e == null || !e.embedded() || xml == null || !stillListed()) {
            return;
        }
        background(I18n.t("FundsXmlPanel.documents-decoding"), () -> FundsXmlDocuments.decode(xml, e.index()), decoded -> {
            if (decoded.sizeMismatch() && !DialogHelper.showConfirmation(I18n.t("FundsXmlPanel.documents-save"),
                    I18n.t("FundsXmlPanel.documents-mismatch-header"),
                    I18n.t("FundsXmlPanel.documents-mismatch-content", decoded.declaredSize(), decoded.bytes().length))) {
                info("");
                return;
            }
            String proposed = decoded.fileName() != null && !decoded.fileName().isBlank() ? decoded.fileName()
                    : (e.name() == null || e.name().isBlank() ? "document-" + e.index() : e.name());
            File target = chooseSaveTarget(proposed);
            if (target == null) {
                info("");
                return;
            }
            background(I18n.t("FundsXmlPanel.documents-saving"), () -> Files.write(target.toPath(), decoded.bytes()),
                    path -> info(I18n.t("FundsXmlPanel.documents-saved", path)));
        });
    }

    private File chooseSaveTarget(String proposed) {
        if (saveFileChooser != null) {
            return saveFileChooser.apply(I18n.t("FundsXmlPanel.documents-choose-target"), proposed);
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("FundsXmlPanel.documents-choose-target"));
        chooser.setInitialFileName(proposed);
        return FileChooserHelper.showSaveDialog(chooser, window());
    }

    private File chooseFile() {
        String title = I18n.t("FundsXmlPanel.documents-choose-file");
        if (openFileChooser != null) {
            return openFileChooser.apply(title);
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.t("FundsXmlPanel.documents-filter-documents"),
                        "*.pdf", "*.html", "*.htm", "*.docx", "*.xlsx", "*.txt", "*.xml"),
                new FileChooser.ExtensionFilter(I18n.t("FundsXmlPanel.documents-filter-all"), "*.*"));
        return FileChooserHelper.showOpenDialog(chooser, window());
    }

    private void onEmbed() {
        if (candidates.isEmpty()) {
            info(I18n.t("FundsXmlPanel.documents-no-candidates"));
            return;
        }
        File file = chooseFile();
        if (file == null) {
            return;
        }
        EmbedChoice choice = askEmbedDetails(file.getName());
        if (choice == null) {
            return;
        }
        change(xml -> {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return List.of(FundsXmlDocuments.embed(xml, new FundsXmlDocuments.NewDocument(bytes, file.getName(),
                    choice.name(), choice.type(), choice.language(), choice.assignment()), LocalDate.now()));
        }, I18n.t("FundsXmlPanel.documents-embedded-done", file.getName()));
    }

    private void onEmbedLinked() {
        Entry e = table.getSelectionModel().getSelectedItem();
        if (e == null || !e.linked()) {
            return;
        }
        File file = chooseFile();
        if (file == null) {
            return;
        }
        change(xml -> FundsXmlDocuments.toEmbedded(xml, e.index(), Files.readAllBytes(file.toPath())),
                I18n.t("FundsXmlPanel.documents-embedded-done", file.getName()));
    }

    private void onReplaceByLink() {
        Entry e = table.getSelectionModel().getSelectedItem();
        if (e == null || !e.embedded()) {
            return;
        }
        var prompt = DialogHelper.createTextInputDialog(I18n.t("FundsXmlPanel.documents-replace-by-link"),
                I18n.t("FundsXmlPanel.documents-url-header"), I18n.t("FundsXmlPanel.documents-url-label"),
                e.linked() ? e.url() : "https://");
        prompt.getEditor().setId("documents-url");
        prompt.getEditor().setPrefColumnCount(40);
        String url = prompt.showAndWait().map(String::strip).orElse(null);
        if (url == null || url.isEmpty() || url.equals("https://")) {
            return;
        }
        change(xml -> FundsXmlDocuments.toLink(xml, e.index(), url), I18n.t("FundsXmlPanel.documents-linked-done", url));
    }

    /** A planned change of the document text. */
    private interface Planner {
        List<TextEdit> plan(String xml) throws IOException;
    }

    /**
     * Plans a change in the background and applies it as one undo step - if the document did not change
     * meanwhile -, then reads the documents again.
     */
    private void change(Planner planner, String done) {
        String xml = editorHost.getActiveText().orElse(null);
        EditorHost.ValidationTarget target = editorHost.validationTarget();
        if (xml == null || target == null) {
            info(I18n.t("FundsXmlPanel.notFundsXml"));
            return;
        }
        if (!stillListed()) {
            return;
        }
        if (org.fxt.freexmltoolkit.service.fundsxml.FundsXmlGridSetup.isSigned(xml)
                && !DialogHelper.showConfirmation(I18n.t("FundsXmlPanel.documents-title"),
                        I18n.t("FundsXmlPanel.signedHeader"), I18n.t("FundsXmlPanel.documents-signed"))) {
            return;
        }
        background(I18n.t("FundsXmlPanel.documents-changing"), () -> planner.plan(xml), edits -> {
            EditorHost.ValidationTarget now = editorHost.validationTarget();
            if (now == null || now.tab() != target.tab() || now.revision() != target.revision()) {
                info(I18n.t("FundsXmlPanel.documents-changed"));
                return;
            }
            if (!editorHost.replaceActiveTextEdits(edits)) {
                info(I18n.t("FundsXmlPanel.documents-change-failed"));
                return;
            }
            reload(done);
        });
    }

    private void reload(String done) {
        String xml = editorHost.getActiveText().orElse("");
        EditorHost.ValidationTarget target = editorHost.validationTarget();
        background(I18n.t("FundsXmlPanel.documents-loading"),
                () -> Loaded.of(xml), loaded -> {
                    listed = target;
                    setEntries(loaded.entries(), loaded.candidates());
                    info(done);
                });
    }

    /** Documents and assignment candidates read from a text. */
    record Loaded(List<Entry> entries, List<Candidate> candidates) {
        static Loaded of(String xml) throws IOException {
            return new Loaded(FundsXmlDocuments.list(xml), FundsXmlDocuments.candidates(xml));
        }
    }

    // ---- embed form ----------------------------------------------------------------------------------------

    private record EmbedChoice(String type, String language, String name, Assignment assignment) {
    }

    private EmbedChoice askEmbedDetails(String fileName) {
        Dialog<EmbedChoice> form = DialogHelper.createDialog(I18n.t("FundsXmlPanel.documents-embed"),
                I18n.t("FundsXmlPanel.documents-embed-header", fileName), "bi-file-earmark-plus");
        form.getDialogPane().setId("documents-embed-form");
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(
                FundsXmlDocuments.listedTypes(FundsXmlRunner.activeSchemaFile())));
        type.setId("documents-embed-type");
        type.getSelectionModel().select(type.getItems().contains("Factsheet") ? "Factsheet" : type.getItems().getFirst());
        TextField language = new TextField("en");
        language.setId("documents-embed-language");
        language.setPrefColumnCount(8);
        TextField name = new TextField(baseName(fileName));
        name.setId("documents-embed-name");
        ComboBox<Candidate> assign = new ComboBox<>(FXCollections.observableArrayList(candidates));
        assign.setId("documents-embed-assignment");
        assign.setConverter(new StringConverter<>() {
            @Override
            public String toString(Candidate c) {
                if (c == null) {
                    return "";
                }
                Party p = c.party();
                return level(c.level(), 1) + " " + (p.main() == null ? "?" : p.main().value())
                        + (p.name() == null ? "" : " - " + p.name());
            }

            @Override
            public Candidate fromString(String s) {
                return null;
            }
        });
        assign.getSelectionModel().select(candidates.stream()
                .filter(c -> c.level() == FundsXmlDocuments.Level.SHARE_CLASS).findFirst().orElse(candidates.getFirst()));
        assign.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label(I18n.t("FundsXmlPanel.documents-type")), type);
        grid.addRow(1, new Label(I18n.t("FundsXmlPanel.documents-language")), language);
        grid.addRow(2, new Label(I18n.t("FundsXmlPanel.documents-assign")), assign);
        grid.addRow(3, new Label(I18n.t("FundsXmlPanel.documents-name")), name);
        GridPane.setHgrow(assign, Priority.ALWAYS);
        form.getDialogPane().setContent(grid);
        form.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        form.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(javafx.beans.binding.Bindings
                .createBooleanBinding(() -> !language.getText().strip().matches("[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*")
                        || type.getValue() == null || assign.getValue() == null,
                        language.textProperty(), type.valueProperty(), assign.valueProperty()));
        form.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Candidate c = assign.getValue();
            Assignment assignment = c.level() == FundsXmlDocuments.Level.FUND ? Assignment.fund(c.party())
                    : new Assignment(c.level(), List.of(c.party()));
            return new EmbedChoice(type.getValue(), language.getText().strip(), name.getText().strip(), assignment);
        });
        return form.showAndWait().orElse(null);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
