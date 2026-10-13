package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.LinkAutocompletePopup;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.XsdElementPathExtractor;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Compact, structured editor for the JavaDoc-style {@code xs:appinfo} tags (@since, @version,
 * @see, @deprecated) used throughout the toolkit, with {@code {@link /XPath}}
 * autocomplete. Designed for the narrow shell Inspector; it commits an {@link XsdAppInfo} object
 * (not a display string) through a supplied callback so the host can route it through the
 * command stack, and it preserves any non-managed / complex (raw-XML) entries (e.g.
 * {@code altova:exampleValues}, {@code @sourceFile}, {@code @author}) that the structured fields
 * do not cover.
 */
public final class AppInfoFieldsEditor extends VBox {

    /** Tags this editor manages directly; all other entries are preserved untouched. */
    private static final Set<String> MANAGED_TAGS =
            Set.of("@since", "@version", "@see", "@deprecated", "@markdown");

    /** The three states of the per-node Markdown preference. */
    private static final String MARKDOWN_UNSET = "Not set";
    private static final String MARKDOWN_ON = "Markdown";
    private static final String MARKDOWN_OFF = "Plain text";

    private final Supplier<XsdSchema> schemaSupplier;
    private final Consumer<XsdAppInfo> onCommit;

    @FXML private TextField sinceField;
    @FXML private TextField versionField;
    private final ObservableList<String> seeItems = FXCollections.observableArrayList();
    @FXML private ListView<String> seeList;
    @FXML private Button removeSeeButton;
    @FXML private ComboBox<String> markdownChoice;
    @FXML private CheckBox deprecatedCheck;
    @FXML private TextArea deprecatedArea;
    private final ObservableList<String> exampleItems = FXCollections.observableArrayList();
    @FXML private ListView<String> exampleList;
    @FXML private Button removeExampleButton;

    /** The appinfo last loaded — its non-managed entries are carried over on each commit. */
    private XsdAppInfo base;
    /** True while repopulating from the model — suppresses commit. */
    private boolean updating;

    private XsdElementPathExtractor pathExtractor;
    private LinkAutocompletePopup autocompletePopup;

    /**
     * Creates the editor.
     *
     * @param schemaSupplier supplies the active schema for {@code {@link}} autocomplete (may return null)
     * @param onCommit       receives the new structured appinfo whenever a field commits
     */
    public AppInfoFieldsEditor(Supplier<XsdSchema> schemaSupplier, Consumer<XsdAppInfo> onCommit) {
        this.schemaSupplier = schemaSupplier;
        this.onCommit = onCommit;
        FxmlViews.load(this, "/pages/inspector/AppInfoFieldsEditor.fxml");

        seeList.setItems(seeItems);
        exampleList.setItems(exampleItems);
        markdownChoice.getItems().addAll(MARKDOWN_UNSET, MARKDOWN_ON, MARKDOWN_OFF);
        markdownChoice.getSelectionModel().selectFirst();
        wire();
    }

    private void wire() {
        commitOnBlur(sinceField);
        commitOnBlur(versionField);
        deprecatedArea.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commit();
            }
        });
        deprecatedCheck.selectedProperty().addListener((o, ov, nv) -> {
            deprecatedArea.setDisable(!nv);
            if (!updating) {
                commit();
            }
        });
        markdownChoice.valueProperty().addListener((o, ov, nv) -> {
            if (!updating) {
                commit();
            }
        });
        seeList.getSelectionModel().selectedItemProperty().addListener(
                (o, ov, nv) -> removeSeeButton.setDisable(nv == null));
        exampleList.getSelectionModel().selectedItemProperty().addListener(
                (o, ov, nv) -> removeExampleButton.setDisable(nv == null));
        // {@link} autocomplete in the deprecation note.
        deprecatedArea.textProperty().addListener((o, ov, nv) -> {
            if (!updating && nv != null && LinkAutocompletePopup.isLinkTrigger(nv)) {
                showAutocomplete(deprecatedArea);
            }
        });
    }

    private void commitOnBlur(TextField field) {
        field.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                commit();
            }
        });
    }

    /**
     * Loads {@code appinfo} into the fields. A copy of it is retained so its non-managed and
     * complex entries are carried over when the user edits a managed tag.
     *
     * @param appinfo the node's current appinfo, or null
     */
    public void setAppinfo(XsdAppInfo appinfo) {
        updating = true;
        try {
            base = appinfo == null ? new XsdAppInfo() : appinfo.copy();
            sinceField.setText(nullToEmpty(base.getSince()));
            versionField.setText(nullToEmpty(base.getVersion()));
            seeItems.setAll(base.getSeeReferences());
            boolean deprecated = base.isDeprecated();
            deprecatedCheck.setSelected(deprecated);
            deprecatedArea.setDisable(!deprecated);
            deprecatedArea.setText(nullToEmpty(base.getDeprecated()));
            Boolean markdown = base.getMarkdown();
            markdownChoice.setValue(markdown == null
                    ? MARKDOWN_UNSET
                    : (markdown ? MARKDOWN_ON : MARKDOWN_OFF));
            exampleItems.setAll(base.getExampleValues());
        } finally {
            updating = false;
        }
    }

    /** Builds the new appinfo from the fields (preserving non-managed entries) and fires the callback. */
    private void commit() {
        if (updating) {
            return;
        }
        XsdAppInfo result = new XsdAppInfo();
        // An untouched example-values block is carried over verbatim, so a schema that still uses
        // the legacy altova: namespace is only rewritten once the user actually edits the values.
        List<String> examples = new ArrayList<>(exampleItems);
        boolean examplesChanged = base == null || !examples.equals(base.getExampleValues());
        // Carry over entries this editor does not manage (raw XML, @sourceFile, @author, unknown
        // tags).
        if (base != null) {
            for (XsdAppInfo.AppInfoEntry entry : base.getEntries()) {
                if (XsdAppInfo.isExampleValuesEntry(entry)) {
                    if (!examplesChanged) {
                        result.addEntry(entry);
                    }
                    continue;
                }
                if (entry.hasRawXml() || entry.getTag() == null || !MANAGED_TAGS.contains(entry.getTag())) {
                    result.addEntry(entry);
                }
            }
        }
        setIfPresent(result::setSince, sinceField.getText());
        setIfPresent(result::setVersion, versionField.getText());
        for (String see : seeItems) {
            if (see != null && !see.isBlank()) {
                result.addSeeReference(see.trim());
            }
        }
        if (deprecatedCheck.isSelected()) {
            result.setDeprecated(deprecatedArea.getText() == null ? "" : deprecatedArea.getText().trim());
        }
        result.setMarkdown(switch (nullToEmpty(markdownChoice.getValue())) {
            case MARKDOWN_ON -> Boolean.TRUE;
            case MARKDOWN_OFF -> Boolean.FALSE;
            default -> null;
        });
        if (examplesChanged) {
            result.setExampleValues(examples);
        }
        onCommit.accept(result);
    }

    private void setIfPresent(Consumer<String> setter, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    @FXML
    public void onAddSee() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add @see Reference");
        dialog.setHeaderText("Enter a reference (use {@link /XPath} for a link)");
        dialog.setContentText("Reference:");
        TextField editor = dialog.getEditor();
        editor.textProperty().addListener((o, ov, nv) -> {
            if (nv != null && LinkAutocompletePopup.isLinkTrigger(nv)) {
                showAutocomplete(editor);
            }
        });
        dialog.showAndWait().ifPresent(ref -> {
            if (!ref.isBlank()) {
                seeItems.add(ref.trim());
                commit();
            }
        });
    }

    @FXML
    public void onRemoveSee() {
        String selected = seeList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            seeItems.remove(selected);
            commit();
        }
    }

    @FXML
    public void onAddExample() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Example Value");
        dialog.setHeaderText("Enter a sample value for this element/attribute");
        dialog.setContentText("Value:");
        dialog.showAndWait().ifPresent(value -> {
            if (!value.isBlank()) {
                exampleItems.add(value.trim());
                commit();
            }
        });
    }

    @FXML
    public void onRemoveExample() {
        String selected = exampleList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            exampleItems.remove(selected);
            commit();
        }
    }

    private void showAutocomplete(TextInputControl control) {
        XsdSchema schema = schemaSupplier == null ? null : schemaSupplier.get();
        if (schema == null) {
            return;
        }
        if (autocompletePopup == null || pathExtractor == null) {
            pathExtractor = new XsdElementPathExtractor(schema);
            autocompletePopup = new LinkAutocompletePopup(pathExtractor);
        }
        int triggerPos = LinkAutocompletePopup.findLinkTriggerPosition(control.getText());
        if (triggerPos >= 0) {
            autocompletePopup.showFor(control, triggerPos, linkText -> {
                String text = control.getText();
                int pos = LinkAutocompletePopup.findLinkTriggerPosition(text);
                if (pos >= 0) {
                    String replaced = text.substring(0, pos) + linkText;
                    control.setText(replaced);
                    control.positionCaret(replaced.length());
                }
            });
        }
    }

    /** Drops any cached schema path data, so the next autocomplete rebuilds from the current schema. */
    public void invalidateSchema() {
        pathExtractor = null;
        autocompletePopup = null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
