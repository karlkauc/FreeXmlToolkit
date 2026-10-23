package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.domain.XmlTemplate;

/**
 * Lets the user pick an XML template to insert. Lists the available templates
 * with a read-only preview; returns the selected {@link XmlTemplate} on OK (the
 * caller renders it and inserts it at the caret). The dialog only selects —
 * rendering/insertion stays in {@link TemplateRunner}/{@link EditorHost}.
 */
public class TemplateInsertDialog extends Dialog<XmlTemplate> {

    @FXML private ListView<XmlTemplate> list;
    @FXML private TextArea preview;

    public TemplateInsertDialog() {
        this(TemplateRunner.list());
    }

    public TemplateInsertDialog(List<XmlTemplate> templates) {
        setTitle("Insert Template");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/TemplateInsertDialog.fxml"));

        list.getItems().setAll(templates);
        list.setCellFactory(lv -> new TemplateCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) ->
                preview.setText(newV == null ? "" : newV.getContent()));

        // OK only enabled once a template is selected.
        javafx.scene.Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        setResultConverter(button ->
                button == ButtonType.OK ? list.getSelectionModel().getSelectedItem() : null);
    }

    /** @return the number of templates listed (for tests/observers). */
    public int templateCount() {
        return list.getItems().size();
    }

    /** Selects a template programmatically (for tests/observers). */
    public void select(XmlTemplate template) {
        list.getSelectionModel().select(template);
    }

    /** Renders a template by name + category. */
    private static final class TemplateCell extends ListCell<XmlTemplate> {
        @Override
        protected void updateItem(XmlTemplate item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                String category = item.getCategory();
                setText(category == null || category.isBlank()
                        ? item.getName() : item.getName() + "  —  " + category);
            }
        }
    }
}
