package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.v2.editor.panels.LinkAutocompletePopup;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.XsdElementPathExtractor;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Compact, structured editor for the JavaDoc-style {@code xs:appinfo} tags (@since, @version,
 * @author, @see, @deprecated) used throughout the toolkit, with {@code {@link /XPath}}
 * autocomplete. Designed for the narrow shell Inspector; it commits an {@link XsdAppInfo} object
 * (not a display string) through a supplied callback so the host can route it through the
 * command stack, and it preserves any non-managed / complex (raw-XML) entries (e.g.
 * {@code altova:exampleValues}, {@code @sourceFile}) that the structured fields do not cover.
 */
public final class AppInfoFieldsEditor extends VBox {

    /** Tags this editor manages directly; all other entries are preserved untouched. */
    private static final Set<String> MANAGED_TAGS = Set.of("@since", "@version", "@author", "@see", "@deprecated");

    private final Supplier<XsdSchema> schemaSupplier;
    private final Consumer<XsdAppInfo> onCommit;

    private final TextField sinceField = new TextField();
    private final TextField versionField = new TextField();
    private final TextField authorField = new TextField();
    private final ObservableList<String> seeItems = FXCollections.observableArrayList();
    private final ListView<String> seeList = new ListView<>(seeItems);
    private final Button removeSeeButton = new Button("Remove");
    private final CheckBox deprecatedCheck = new CheckBox("Deprecated");
    private final TextArea deprecatedArea = new TextArea();

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
        setSpacing(4);
        getStyleClass().add("fxt-appinfo-editor");
        build();
        wire();
    }

    private void build() {
        sinceField.setId("inspector-appinfo-since");
        sinceField.setPromptText("e.g. 4.0.0");
        versionField.setId("inspector-appinfo-version");
        versionField.setPromptText("e.g. 1.0");
        authorField.setId("inspector-appinfo-author");
        authorField.setPromptText("e.g. Jane Doe");
        for (TextField f : List.of(sinceField, versionField, authorField)) {
            f.getStyleClass().add("fxt-inspector-edit");
        }

        seeList.setId("inspector-appinfo-see");
        seeList.setPrefHeight(70);
        seeList.setPlaceholder(new Label("No @see references"));
        seeList.getStyleClass().add("fxt-facet-table");
        Button addSee = new Button("Add");
        addSee.setId("inspector-appinfo-see-add");
        addSee.setMinWidth(Region.USE_PREF_SIZE);
        addSee.setOnAction(e -> handleAddSee());
        removeSeeButton.setId("inspector-appinfo-see-remove");
        removeSeeButton.setMinWidth(Region.USE_PREF_SIZE);
        removeSeeButton.setDisable(true);
        removeSeeButton.setOnAction(e -> handleRemoveSee());
        HBox seeButtons = new HBox(6, addSee, removeSeeButton);
        seeButtons.setAlignment(Pos.CENTER_LEFT);

        deprecatedCheck.setId("inspector-appinfo-deprecated");
        deprecatedArea.setId("inspector-appinfo-deprecated-note");
        deprecatedArea.getStyleClass().add("fxt-inspector-edit");
        deprecatedArea.setWrapText(true);
        deprecatedArea.setPrefRowCount(2);
        deprecatedArea.setPromptText("e.g. Use {@link /NewElement} instead");
        deprecatedArea.setDisable(true);

        getChildren().addAll(
                subLabel("@since"), sinceField,
                subLabel("@version"), versionField,
                subLabel("@author"), authorField,
                subLabel("@see"), seeList, seeButtons,
                deprecatedCheck, deprecatedArea);
    }

    private Label subLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("fxt-inspector-sub-label");
        return l;
    }

    private void wire() {
        commitOnBlur(sinceField);
        commitOnBlur(versionField);
        commitOnBlur(authorField);
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
        seeList.getSelectionModel().selectedItemProperty().addListener(
                (o, ov, nv) -> removeSeeButton.setDisable(nv == null));
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
            authorField.setText(nullToEmpty(base.getAuthor()));
            seeItems.setAll(base.getSeeReferences());
            boolean deprecated = base.isDeprecated();
            deprecatedCheck.setSelected(deprecated);
            deprecatedArea.setDisable(!deprecated);
            deprecatedArea.setText(nullToEmpty(base.getDeprecated()));
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
        // Carry over entries this editor does not manage (raw XML, @sourceFile, unknown tags).
        if (base != null) {
            for (XsdAppInfo.AppInfoEntry entry : base.getEntries()) {
                if (entry.hasRawXml() || entry.getTag() == null || !MANAGED_TAGS.contains(entry.getTag())) {
                    result.addEntry(entry);
                }
            }
        }
        setIfPresent(result::setSince, sinceField.getText());
        setIfPresent(result::setVersion, versionField.getText());
        setIfPresent(result::setAuthor, authorField.getText());
        for (String see : seeItems) {
            if (see != null && !see.isBlank()) {
                result.addSeeReference(see.trim());
            }
        }
        if (deprecatedCheck.isSelected()) {
            result.setDeprecated(deprecatedArea.getText() == null ? "" : deprecatedArea.getText().trim());
        }
        onCommit.accept(result);
    }

    private void setIfPresent(Consumer<String> setter, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    private void handleAddSee() {
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

    private void handleRemoveSee() {
        String selected = seeList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            seeItems.remove(selected);
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
