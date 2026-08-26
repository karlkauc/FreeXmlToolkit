package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lets the user pick which catalog entries to copy into the library as USER mappings. */
public class CatalogImportDialog extends Dialog<List<SchemaLibraryEntry>> {

    public CatalogImportDialog(List<SchemaLibraryEntry> preview, Set<String> existingKeys) {
        setTitle("Import Catalog Entries");
        setHeaderText(preview.size() + " namespace mapping(s) found. Entries already in the library are unchecked.");
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        List<CheckBox> boxes = new ArrayList<>();
        VBox list = new VBox(4);
        for (SchemaLibraryEntry e : preview) {
            CheckBox cb = new CheckBox(e.namespace() + "  →  " + e.location());
            cb.setSelected(!existingKeys.contains(e.key()));
            cb.setUserData(e);
            boxes.add(cb);
            list.getChildren().add(cb);
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(600, 320);
        CheckBox all = new CheckBox("Select all");
        all.setSelected(boxes.stream().allMatch(CheckBox::isSelected));
        all.setOnAction(ev -> boxes.forEach(b -> b.setSelected(all.isSelected())));
        getDialogPane().setContent(new VBox(8, all, scroll));

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            List<SchemaLibraryEntry> chosen = new ArrayList<>();
            for (CheckBox b : boxes) if (b.isSelected()) chosen.add((SchemaLibraryEntry) b.getUserData());
            return chosen;
        });
    }
}
