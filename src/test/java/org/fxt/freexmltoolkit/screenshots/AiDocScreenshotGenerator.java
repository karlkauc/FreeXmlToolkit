package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ai.AiActions;
import org.fxt.freexmltoolkit.controls.shell.editor.ai.AiState;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.provider.FakeChatServer;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderKind;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderPreset;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the AI assistant for the documentation ({@code docs/img/}): Settings → AI with a configured provider, and
 * a verified fix proposal in the AI dock. The provider is a local fake (loopback, OpenAI-compatible) — no AI service
 * is contacted.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*AiDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class AiDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="customer" type="xs:string"/>
                    <xs:element name="item" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="sku" type="xs:string"/>
                          <xs:element name="quantity" type="xs:positiveInteger"/>
                          <xs:element name="price" type="xs:decimal"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:string" use="required"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static final String XML = """
            <order id="PO-1042" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:noNamespaceSchemaLocation="order.xsd">
              <customer>Example Trading Ltd.</customer>
              <item>
                <sku>A-100</sku>
                <quantity>2</quantity>
                <price>19.90</price>
              </item>
              <item>
                <sku>B-220</sku>
                <quantity>three</quantity>
                <price>4.50</price>
              </item>
            </order>
            """;

    private static final String FIX = "```json\\n{\\\"xpath\\\": \\\"/order/item[2]/quantity\\\", "
            + "\\\"mode\\\": \\\"SET_VALUE\\\", \\\"replacement\\\": \\\"3\\\"}\\n```\\n"
            + "quantity is declared as xs:positiveInteger, so the word \\\"three\\\" is not allowed; "
            + "the number 3 keeps the meaning.";

    @TempDir
    Path temp;

    private Parent root;
    private UnifiedShellView shell;
    private final PropertiesService properties = PropertiesServiceImpl.getInstance();
    private final List<String> keys = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(PropertiesService.class)
                .set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateAiScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        try (FakeChatServer server = new FakeChatServer()) {
            onFx(() -> ThemeManager.apply(root.getScene(), false));
            ProviderConfig local = new ProviderConfig("ollama-docs", "Ollama (this computer)",
                    ProviderKind.OPENAI_COMPATIBLE, ProviderPreset.CUSTOM, server.url("/v1"), "qwen2.5-coder:14b",
                    null, null, null, 0, 60);
            set(AiSettings.KEY_ENABLED, "true");
            set(AiSettings.KEY_PREVIEW_ALWAYS, "false");
            set("ai.provider." + local.id() + ".previewSeen", "true");
            keys.add(ProviderStore.KEY_PROVIDERS);
            keys.add(ProviderStore.KEY_DEFAULT);
            ProviderStore.settings().save(List.of(local));
            onFx(AiState::refresh);

            // Settings → AI with the provider
            onFx(() -> shell.getSelectionModel().select(Activity.SETTINGS));
            settle(800);
            SettingsPanel panel = findSettings(root);
            onFx(() -> panel.showSection("ai"));
            settle(1000);
            shot("ai-assistant-settings");

            // A validation problem fixed by the AI, verified with the schema, shown in the AI dock
            Files.writeString(temp.resolve("order.xsd"), XSD);
            Path xml = Files.writeString(temp.resolve("order.xml"), XML);
            EditorHost host = shell.getEditorHost();
            onFx(() -> {
                shell.getSelectionModel().select(Activity.VALIDATION);
                host.openFile(xml);
            });
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("three")).orElse(false));
            settle(1500);
            server.reply(FakeChatServer.Reply.json("{\"choices\":[{\"message\":{\"content\":\"" + FIX + "\"},"
                    + "\"finish_reason\":\"stop\"}]}"));
            ValidationProblem problem = new ValidationProblem("XSD", "error", 11,
                    "cvc-datatype-valid.1.2.1: 'three' is not a valid value for 'integer'.");
            onFx(() -> AiActions.fixProblem(host, problem));
            WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> ScreenshotFx.call("ai-screenshot",
                    () -> root.lookup("#ai-proposal-apply") instanceof javafx.scene.control.Button b && b.isVisible()));
            settle(1200);
            shot("ai-assistant-proposal");
        } finally {
            Properties all = properties.loadProperties();
            keys.forEach(all::remove);
            properties.saveProperties(all);
            onFx(AiState::refresh);
        }
    }

    private void set(String key, String value) {
        keys.add(key);
        properties.set(key, value);
    }

    /** @return the Settings page in the node tree, or null */
    private static SettingsPanel findSettings(Node node) {
        if (node instanceof SettingsPanel panel) {
            return panel;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                SettingsPanel found = findSettings(child);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof javafx.scene.control.TabPane tabs) {
            for (var tab : tabs.getTabs()) {
                SettingsPanel found = tab.getContent() == null ? null : findSettings(tab.getContent());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void onFx(Runnable action) {
        ScreenshotFx.run("ai-screenshot", action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = ScreenshotFx.call("ai-screenshot", () -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[ai-screenshot] wrote " + out.getAbsolutePath());
    }
}
