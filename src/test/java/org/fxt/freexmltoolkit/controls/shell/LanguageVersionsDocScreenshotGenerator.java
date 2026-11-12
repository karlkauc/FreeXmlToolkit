package org.fxt.freexmltoolkit.controls.shell;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.standards.StandardsUi;
import org.fxt.freexmltoolkit.controls.shell.editor.standards.VersionChoice;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.standards.StandardsSettings;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the language-version (expert mode) features and the oXygen / XMLSpy-like syntax schemes for the
 * documentation ({@code docs/img/}). In the shell's package to reach the Query Console.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*LanguageVersionsDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class LanguageVersionsDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");
    private static final Path ORDER = Path.of("src/main/resources/examples/getting-started/order.xml");
    private static final String[] KEYS = {StandardsSettings.KEY_EXPERT_MODE, StandardsSettings.KEY_XPATH,
            StandardsSettings.KEY_XSLT, StandardsSettings.KEY_XSD,
            StandardsSettings.toolKey(StandardsSettings.TOOL_QUERY_CONSOLE, "xpath"),
            StandardsSettings.toolKey(StandardsSettings.TOOL_TRANSFORM, "xslt"),
            StandardsSettings.toolKey(StandardsSettings.TOOL_VALIDATION, "xsd")};

    private Parent root;
    private UnifiedShellView shell;
    private PropertiesService props;

    @Start
    void start(Stage stage) throws Exception {
        ServiceRegistry.initialize();
        props = ServiceRegistry.get(PropertiesService.class);
        props.set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        // expert mode with older defaults, set before the shell is built
        props.set(StandardsSettings.KEY_EXPERT_MODE, "true");
        props.set(StandardsSettings.KEY_XPATH, "2.0");
        props.set(StandardsSettings.KEY_XSLT, "2.0");
        props.set(StandardsSettings.KEY_XSD, "1.0");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        StandardsUi.settingsChanged();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateLanguageVersionScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        var host = shell.getEditorHost();
        try {
            onFx(() -> ThemeManager.apply(root.getScene(), false));

            // Settings → Schemas & Validation → Standards (expert mode)
            onFx(() -> shell.getSelectionModel().select(Activity.SETTINGS));
            settle(800);
            onFx(() -> findSettings(root).showSection("schemas"));
            settle(800);
            shot("unified-shell-settings-standards");

            // Query Console with XPath 2.0: an XPath 3.0 function is refused
            onFx(() -> {
                shell.getSelectionModel().select(Activity.EXPLORER);
                host.openFile(ORDER.toAbsolutePath());
            });
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("<order")).orElse(false));
            onFx(() -> shell.toggleQueryConsole());
            settle(600);
            onFx(() -> {
                var console = shell.getQueryConsole();
                VersionChoice choice = (VersionChoice) console.lookup("#query-version");
                choice.getItems().stream().filter(i -> i.value() == XPathVersion.V2_0).findFirst()
                        .ifPresent(choice::setValue);
                CodeArea field = (CodeArea) console.lookup(".fxt-xpath-field");
                field.replaceText("string-join(head(//item)/@sku, ', ')");
                console.run();
            });
            settle(2000);
            shot("unified-shell-query-console-xpath-version");
            onFx(() -> shell.toggleQueryConsole());

            // Validation panel: the STANDARDS row
            onFx(() -> shell.getSelectionModel().select(Activity.VALIDATION));
            settle(1000);
            shot("unified-shell-validation-standards");

            // Run ▾ → Check Compatibility on an XSLT 3.0 stylesheet, checked as XSLT 2.0
            Path xslt = Files.createTempDirectory("fxt-shots").resolve("report.xsl");
            Files.writeString(xslt, """
                    <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                    expand-text="yes">
                      <xsl:output method="text"/>
                      <xsl:template match="/order">
                        <xsl:iterate select="items/item">
                          <xsl:param name="total" select="0"/>
                          <xsl:on-completion>Total: {$total}</xsl:on-completion>
                          <xsl:next-iteration>
                            <xsl:with-param name="total" select="$total + @quantity * @price"/>
                          </xsl:next-iteration>
                        </xsl:iterate>
                        <xsl:value-of select="head(items/item)/@sku || ' first'"/>
                      </xsl:template>
                    </xsl:stylesheet>
                    """);
            onFx(() -> {
                shell.getSelectionModel().select(Activity.TRANSFORM);
                host.openFile(xslt.toAbsolutePath());
            });
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("xsl:iterate")).orElse(false));
            settle(800);
            onFx(() -> shell.onCheckCompatibility());
            settle(2500);
            shot("unified-shell-check-compatibility");

            // The refused transformation with "Run anyway"
            String text = Files.readString(xslt);
            var refused = org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner.xsltIncompatibility(text,
                    xslt.toUri().toString(), org.fxt.freexmltoolkit.service.standards.XsltVersion.V2_0);
            onFx(() -> host.transformOutputPanel().showIncompatible("ERROR: " + refused.getErrorMessage(), () -> { }));
            settle(1000);
            shot("unified-shell-transform-run-anyway");

            // Syntax schemes oXygen and XMLSpy-like on the order document
            SettingsPanel panel = findSettings(root);
            onFx(() -> {
                host.transformOutputPanel().hide();
                host.setActiveProblems(java.util.List.of());
            });
            for (String scheme : new String[]{"oxygen", "xmlspy"}) {
                onFx(() -> {
                    panel.applySyntaxScheme(scheme);
                    shell.getSelectionModel().select(Activity.EXPLORER);
                    host.openFile(ORDER.toAbsolutePath());
                });
                settle(1500);
                shot("unified-shell-syntax-" + scheme);
            }
        } finally {
            onFx(() -> {
                ThemeManager.resetSyntaxColors();
                SettingsPanel panel = findSettings(root);
                if (panel != null) {
                    panel.applyTheme(false);
                }
            });
            for (String key : KEYS) {
                props.set(key, "");
            }
            StandardsUi.settingsChanged();
        }
    }

    private static SettingsPanel findSettings(Node node) {
        if (node instanceof SettingsPanel panel) {
            return panel;
        }
        if (node instanceof javafx.scene.control.TabPane tabs) {
            for (var tab : tabs.getTabs()) {
                SettingsPanel found = tab.getContent() == null ? null : findSettings(tab.getContent());
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                SettingsPanel found = findSettings(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(60_000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(60_000,
                () -> (shell != null ? shell : root).snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[language-versions-screenshot] wrote " + out.getAbsolutePath());
    }
}
