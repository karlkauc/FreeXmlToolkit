package org.fxt.freexmltoolkit.controls.shell.editor.standards;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.standards.StandardVersion;
import org.fxt.freexmltoolkit.service.standards.StandardsSettings;

/**
 * A compact version picker of a tool (Query Console, XPath search, Transform, Validation). Its first entry
 * "Default (…)" means "no choice of this tool" — the project binding or the global default applies; the other
 * entries are the versions of one standard. The choice is remembered per tool
 * ({@link StandardsSettings#setToolChoice}). Visible only in the expert mode.
 */
public final class VersionChoice extends ComboBox<VersionChoice.Item> {

    /** One entry: {@code value == null} is the default entry. */
    public record Item(StandardVersion value, String text) {
    }

    private final String tool;
    private String component;
    private Supplier<? extends StandardVersion> defaultValue;
    private boolean filling;

    /**
     * @param tool         tool id ({@code StandardsSettings.TOOL_*})
     * @param type         the standard's enum
     * @param component    settings component (xpath, xquery, xslt, xsd, xml, schematron)
     * @param defaultValue what applies without a choice of this tool (shown in the default entry)
     */
    public <E extends Enum<E> & StandardVersion> VersionChoice(String tool, Class<E> type, String component,
                                                                Supplier<E> defaultValue) {
        this.tool = tool;
        getStyleClass().add("fxt-version-choice");
        setConverter(new StringConverter<>() {
            @Override
            public String toString(Item item) {
                return item == null ? "" : item.text();
            }

            @Override
            public Item fromString(String s) {
                return null;
            }
        });
        setTooltip(new Tooltip(I18n.t("Standards.choice.tooltip")));
        StandardsUi.showInExpertMode(this);
        valueProperty().addListener((obs, old, now) -> {
            if (!filling && now != null) {
                StandardsUi.settings().setToolChoice(this.tool, this.component, now.value());
            }
        });
        StandardsUi.revisionProperty().addListener((obs, o, n) -> reload());
        StandardsUi.expertModeProperty().addListener((obs, o, n) -> reload());
        configure(type, component, defaultValue);
    }

    /** Switches the standard this picker chooses (the Query Console's XPath | XQuery switch). */
    public <E extends Enum<E> & StandardVersion> void configure(Class<E> type, String component,
                                                                Supplier<E> defaultValue) {
        this.component = component;
        this.defaultValue = defaultValue;
        filling = true;
        try {
            List<Item> items = new ArrayList<>();
            items.add(new Item(null, ""));
            for (E constant : type.getEnumConstants()) {
                items.add(new Item(constant, constant.label()));
            }
            getItems().setAll(items);
        } finally {
            filling = false;
        }
        reload();
    }

    /** The chosen version, or {@code null} = default (project / global). */
    public StandardVersion selected() {
        Item item = getValue();
        return item == null ? null : item.value();
    }

    /** Re-reads the remembered choice and the default's text. */
    public void reload() {
        filling = true;
        try {
            StandardVersion def = defaultValue == null ? null : defaultValue.get();
            String defaultText = I18n.t("Standards.choice.default", def == null ? "" : def.label());
            List<Item> items = new ArrayList<>(getItems());
            if (!items.isEmpty()) {
                items.set(0, new Item(null, defaultText));
                getItems().setAll(items);
            }
            String stored = StandardsUi.settings().expertMode()
                    ? org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                            .get(StandardsSettings.toolKey(tool, component))
                    : null;
            Item match = getItems().isEmpty() ? null : getItems().getFirst();
            for (Item item : getItems()) {
                if (item.value() != null && stored != null && item.value().cliValue().equals(stored.strip())) {
                    match = item;
                }
            }
            setValue(match);
        } finally {
            filling = false;
        }
    }
}
