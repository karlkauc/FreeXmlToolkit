package org.fxt.freexmltoolkit.plugins.xbrl;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugin.XmlScan;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * XBRL ("XBRL light"): recognises XBRL instances and inline XBRL reports, counts their facts,
 * contexts and units, and validates them with Arelle ({@link ArelleRunner}), which the user installs
 * separately. Off by default.
 */
public final class XbrlPlugin implements FxtPlugin {

    public static final String ID = "xbrl";
    /** Settings key of the Arelle command chosen by the user (empty: look it up on the PATH). */
    public static final String COMMAND_KEY = "plugin.xbrl.arelleCommand";
    static final String INSTANCE_NS = "http://www.xbrl.org/2003/instance";
    static final String INLINE_NS = "http://www.xbrl.org/2013/inlineXBRL";
    private static final Set<String> STRUCTURE_NS = Set.of(INSTANCE_NS, "http://www.xbrl.org/2003/linkbase",
            "http://xbrl.org/2006/xbrldi");

    @Override public String id() { return ID; }
    @Override public String name() { return "XBRL (with Arelle)"; }
    @Override public String version() { return "1.0"; }
    @Override public boolean enabledByDefault() { return false; }

    @Override
    public String description() {
        return "Recognises XBRL instances and inline XBRL reports and validates them with Arelle (installed separately).";
    }

    @Override
    public void register(PluginContext context) {
        context.addActivity(new PluginActivity(ID, "XBRL", "bi-graph-up", Workflow.VALIDATION,
                ui -> new IndustryPanel(ui, "XBRL",
                        "Validates XBRL instances (.xbrl) and inline XBRL reports (.xhtml) with Arelle, the open-source "
                                + "XBRL processor: XBRL 2.1, dimensions, calculations and the taxonomy's formulas. "
                                + "Arelle is a separate program — install it with \"pip install arelle-release\" "
                                + "(Python 3.11 or later) or choose its arelleCmdLine below. It downloads the "
                                + "taxonomies itself. Save the document before checking it.",
                        List.of(new IndustryPanel.Action("xbrl-arelle-command", "Choose Arelle Command…", "bi-gear",
                                ActionColor.TOOL, XbrlPlugin::chooseCommand)),
                        XbrlPlugin::describe, XbrlPlugin::check, List.of()),
                null));
    }

    private static String configuredCommand() {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(COMMAND_KEY);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The panel's check: Arelle on the saved file. */
    static List<org.fxt.freexmltoolkit.plugin.DocumentValidator.Problem> check(String content, Path path, String name)
            throws Exception {
        if (path == null) {
            throw new IllegalStateException("Save the document first: Arelle reads the file and its taxonomy references.");
        }
        Path command = ArelleRunner.findCommand(configuredCommand()).orElseThrow(() -> new IllegalStateException(
                "Arelle was not found. Install it with \"pip install arelle-release\" or choose its arelleCmdLine."));
        return ArelleRunner.validate(command, path, Duration.ofMinutes(10));
    }

    /** @return what the document is, or {@code null} when it is no XBRL */
    static String describe(String content) {
        String[] root = XmlScan.rootOf(content).orElse(null);
        if (root == null) {
            return null;
        }
        boolean instance = root[0].equals("xbrl") && root[1].equals(INSTANCE_NS);
        boolean inline = !instance && root[0].equals("html") && content.contains(INLINE_NS);
        if (!instance && !inline) {
            return null;
        }
        StringBuilder text = new StringBuilder(instance ? "XBRL instance" : "Inline XBRL report");
        try {
            XmlScan scan = XmlScan.of(content);
            long facts;
            if (instance) {
                facts = scan.elements().stream().filter(e -> e.depth() >= 1 && !STRUCTURE_NS.contains(e.namespace())
                        && e.attributes().containsKey("contextRef")).count();
            } else {
                facts = scan.elements().stream().filter(e -> e.namespace().equals(INLINE_NS)
                        && (e.localName().equals("nonFraction") || e.localName().equals("nonNumeric"))).count();
            }
            long contexts = scan.elements().stream().filter(e -> e.namespace().equals(INSTANCE_NS)
                    && e.localName().equals("context")).count();
            long units = scan.elements().stream().filter(e -> e.namespace().equals(INSTANCE_NS)
                    && e.localName().equals("unit")).count();
            text.append(" · ").append(facts).append(facts == 1 ? " fact · " : " facts · ")
                    .append(contexts).append(contexts == 1 ? " context · " : " contexts · ")
                    .append(units).append(units == 1 ? " unit" : " units");
        } catch (javax.xml.stream.XMLStreamException e) {
            text.append(" (not well-formed)");
        }
        text.append('\n').append(ArelleRunner.findCommand(configuredCommand())
                .map(c -> "Arelle: " + c).orElse("Arelle not found — install it or choose its command."));
        return text.toString();
    }

    static void chooseCommand(PluginUi ui) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("XbrlPlugin.choose-arellecmdline"));
        File file = FileChooserHelper.showOpenDialog(chooser, ui.window());
        if (file != null) {
            ServiceRegistry.get(PropertiesService.class).set(COMMAND_KEY, file.getAbsolutePath());
            ui.refreshContributions();
        }
    }
}
