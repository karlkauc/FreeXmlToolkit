package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorConfig;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Picks the XSLT processor of a run: the bundled Saxon HE or an external one from Settings → XSLT Processors,
 * with a button that opens the list. Built in code (its items come from the settings). Hidden while the
 * administrator does not allow external processors or none is configured - then every run uses Saxon HE.
 */
public final class XsltProcessorChoice extends HBox {

    private final ComboBox<String> combo = new ComboBox<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final boolean remember;
    private final ReadOnlyObjectWrapper<XsltProcessorConfig> selected = new ReadOnlyObjectWrapper<>(this, "selected");
    private boolean filling;

    /**
     * @param id       the control's id (the combo gets {@code id + "-combo"})
     * @param remember whether the choice is the Transform panel's remembered one ({@code xslt.processor.last});
     *                 otherwise it starts at the default processor and is not stored
     */
    public XsltProcessorChoice(String id, boolean remember) {
        this(id, remember, "XsltProcessorChoice.label");
    }

    /** @param labelKey the i18n key of the label ("Processor", or "XSLT processor" next to an XSL-FO processor) */
    public XsltProcessorChoice(String id, boolean remember, String labelKey) {
        super(6);
        this.remember = remember;
        setId(id);
        setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(I18n.t(labelKey));
        label.setMinWidth(Label.USE_PREF_SIZE);
        combo.setId(id + "-combo");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setTooltip(new Tooltip(I18n.t("XsltProcessorChoice.tooltip")));
        HBox.setHgrow(combo, Priority.ALWAYS);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String key) {
                return key == null ? "" : names.getOrDefault(key, key);
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });
        combo.valueProperty().addListener((obs, o, key) -> {
            if (!filling && key != null && remember) {
                try {
                    XsltProcessorStore.settings().setLastId(key);
                } catch (RuntimeException e) {
                    // no settings available: the choice is not remembered
                }
            }
            selected.set(processorFor(key));
        });
        Button manage = new Button();
        manage.setId(id + "-manage");
        IconifyIcon icon = new IconifyIcon("bi-gear");
        icon.setIconSize(16);
        manage.setGraphic(icon);
        manage.getStyleClass().addAll("fxt-tool-button", "fxt-tool-neutral");
        manage.setTooltip(new Tooltip(I18n.t("XsltProcessorChoice.manage")));
        manage.setAccessibleText(I18n.t("XsltProcessorChoice.manage"));
        manage.setOnAction(e -> manage());
        getChildren().addAll(label, combo, manage);
        label.setLabelFor(combo);
        refresh();
    }

    /** @return the chosen external processor, or {@code null} for Saxon HE */
    public XsltProcessorConfig selected() {
        return selected.get();
    }

    /** @return the chosen processor as property ({@code null} = Saxon HE) */
    public ReadOnlyObjectProperty<XsltProcessorConfig> selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    /** Selects a processor by id or name ({@code null} or {@code builtin} = Saxon HE); unknown names keep Saxon HE. */
    public void select(String idOrName) {
        String key = XsltProcessorStore.BUILTIN_ID;
        if (idOrName != null) {
            for (Map.Entry<String, String> e : names.entrySet()) {
                if (e.getKey().equals(idOrName.strip()) || e.getValue().equalsIgnoreCase(idOrName.strip())) {
                    key = e.getKey();
                }
            }
        }
        combo.setValue(key);
    }

    /** Fills the combo from the settings and selects the remembered choice (or the default). */
    public void refresh() {
        String keep = combo.getValue();
        filling = true;
        try {
            XsltProcessorStore store = XsltProcessorStore.settings();
            names.clear();
            names.put(XsltProcessorStore.BUILTIN_ID, I18n.t("XsltProcessorsSettings.builtin"));
            store.list().forEach(p -> names.put(p.id(), p.name()));
            combo.getItems().setAll(names.keySet());
            String start = keep != null && names.containsKey(keep) ? keep : remember ? store.lastId() : store.defaultId();
            combo.setValue(names.containsKey(start) ? start : XsltProcessorStore.BUILTIN_ID);
            boolean show = store.externalAllowed() && names.size() > 1;
            setVisible(show);
            setManaged(show);
        } catch (RuntimeException e) {
            names.clear();
            names.put(XsltProcessorStore.BUILTIN_ID, I18n.t("XsltProcessorsSettings.builtin"));
            combo.getItems().setAll(XsltProcessorStore.BUILTIN_ID);
            combo.setValue(XsltProcessorStore.BUILTIN_ID);
            setVisible(false);
            setManaged(false);
        } finally {
            filling = false;
        }
        selected.set(processorFor(combo.getValue()));
    }

    private XsltProcessorConfig processorFor(String key) {
        if (key == null || XsltProcessorStore.BUILTIN_ID.equals(key)) {
            return null;
        }
        try {
            return XsltProcessorStore.settings().byId(key).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Opens the processor list (the same as Settings → XSLT Processors) in a dialog. */
    private void manage() {
        javafx.scene.control.Dialog<Void> dialog = DialogHelper.createDialog(I18n.t("XsltProcessorChoice.manage.title"),
                I18n.t("XsltProcessorChoice.manage.header"), "bi-cpu");
        dialog.getDialogPane().setContent(new XsltProcessorsSettings());
        dialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.showAndWait();
        refresh();
    }
}
