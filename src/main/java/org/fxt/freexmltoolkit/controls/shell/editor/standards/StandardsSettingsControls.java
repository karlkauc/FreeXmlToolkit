package org.fxt.freexmltoolkit.controls.shell.editor.standards;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.service.standards.SchematronBinding;
import org.fxt.freexmltoolkit.service.standards.StandardVersion;
import org.fxt.freexmltoolkit.service.standards.StandardsProfile;
import org.fxt.freexmltoolkit.service.standards.StandardsSettings;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;
import org.fxt.freexmltoolkit.service.standards.XQueryVersion;
import org.fxt.freexmltoolkit.service.standards.XmlVersionMode;
import org.fxt.freexmltoolkit.service.standards.XsdVersionMode;
import org.fxt.freexmltoolkit.service.standards.XsltVersion;

/**
 * Behaviour of the Settings → Standards card: fills the six default pickers, loads, saves and restores them.
 * The controls themselves are declared in {@code SettingsPanel.fxml}.
 */
public final class StandardsSettingsControls {

    private final CheckBox expertMode;
    private final ComboBox<StandardVersion> xpath;
    private final ComboBox<StandardVersion> xquery;
    private final ComboBox<StandardVersion> xslt;
    private final ComboBox<StandardVersion> xsd;
    private final ComboBox<StandardVersion> xml;
    private final ComboBox<StandardVersion> schematron;

    public StandardsSettingsControls(CheckBox expertMode, ComboBox<StandardVersion> xpath,
                                     ComboBox<StandardVersion> xquery, ComboBox<StandardVersion> xslt,
                                     ComboBox<StandardVersion> xsd, ComboBox<StandardVersion> xml,
                                     ComboBox<StandardVersion> schematron) {
        this.expertMode = expertMode;
        this.xpath = xpath;
        this.xquery = xquery;
        this.xslt = xslt;
        this.xsd = xsd;
        this.xml = xml;
        this.schematron = schematron;
        fill(xpath, XPathVersion.values());
        fill(xquery, XQueryVersion.values());
        fill(xslt, XsltVersion.values());
        fill(xsd, XsdVersionMode.values());
        fill(xml, XmlVersionMode.values());
        fill(schematron, SchematronBinding.values());
    }

    /** The six pickers (for disable bindings). */
    public List<ComboBox<StandardVersion>> pickers() {
        return List.of(xpath, xquery, xslt, xsd, xml, schematron);
    }

    /** Settings keys per control, for the administrator's locks. */
    public void putPolicyKeys(Map<Node, String[]> keys) {
        keys.put(expertMode, new String[] {StandardsSettings.KEY_EXPERT_MODE});
        keys.put(xpath, new String[] {StandardsSettings.KEY_XPATH});
        keys.put(xquery, new String[] {StandardsSettings.KEY_XQUERY});
        keys.put(xslt, new String[] {StandardsSettings.KEY_XSLT});
        keys.put(xsd, new String[] {StandardsSettings.KEY_XSD});
        keys.put(xml, new String[] {StandardsSettings.KEY_XML});
        keys.put(schematron, new String[] {StandardsSettings.KEY_SCHEMATRON});
    }

    /** Shows the stored values. */
    public void load(Function<String, String> getter) {
        expertMode.setSelected(Boolean.parseBoolean(getter.apply(StandardsSettings.KEY_EXPERT_MODE)));
        xpath.setValue(XPathVersion.parse(getter.apply(StandardsSettings.KEY_XPATH)));
        xquery.setValue(XQueryVersion.parse(getter.apply(StandardsSettings.KEY_XQUERY)));
        xslt.setValue(XsltVersion.parse(getter.apply(StandardsSettings.KEY_XSLT)));
        xsd.setValue(XsdVersionMode.parse(getter.apply(StandardsSettings.KEY_XSD)));
        xml.setValue(XmlVersionMode.parse(getter.apply(StandardsSettings.KEY_XML)));
        schematron.setValue(SchematronBinding.parse(getter.apply(StandardsSettings.KEY_SCHEMATRON)));
    }

    /** Stores the shown values and tells the tools. */
    public void save(BiConsumer<String, String> setter) {
        setter.accept(StandardsSettings.KEY_EXPERT_MODE, String.valueOf(expertMode.isSelected()));
        store(setter, StandardsSettings.KEY_XPATH, xpath);
        store(setter, StandardsSettings.KEY_XQUERY, xquery);
        store(setter, StandardsSettings.KEY_XSLT, xslt);
        store(setter, StandardsSettings.KEY_XSD, xsd);
        store(setter, StandardsSettings.KEY_XML, xml);
        store(setter, StandardsSettings.KEY_SCHEMATRON, schematron);
        StandardsUi.settingsChanged();
    }

    /** Today's behaviour: expert mode off, newest versions. */
    public void restoreDefaults() {
        StandardsProfile latest = StandardsProfile.LATEST;
        expertMode.setSelected(false);
        xpath.setValue(latest.xpath());
        xquery.setValue(latest.xquery());
        xslt.setValue(latest.xslt());
        xsd.setValue(latest.xsd());
        xml.setValue(latest.xml());
        schematron.setValue(latest.schematron());
    }

    private static void store(BiConsumer<String, String> setter, String key, ComboBox<StandardVersion> combo) {
        if (combo.getValue() != null) {
            setter.accept(key, combo.getValue().cliValue());
        }
    }

    private static void fill(ComboBox<StandardVersion> combo, StandardVersion[] values) {
        combo.getItems().setAll(values);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(StandardVersion v) {
                return v == null ? "" : v.label();
            }

            @Override
            public StandardVersion fromString(String s) {
                return null;
            }
        });
    }
}
