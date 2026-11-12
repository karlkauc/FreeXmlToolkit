package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.provider.FakeChatServer;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderKind;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderPreset;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * End to end with a local fake provider (OpenAI-compatible, loopback): a validation problem is fixed by the AI, the
 * fix is verified with the document's schema, shown in the bottom dock and applied as one edit; a proposal for a
 * document that changed is refused.
 */
@ExtendWith(ApplicationExtension.class)
class AiActionsUiTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="n" type="xs:int" maxOccurs="unbounded"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>""";

    private static final String XML = "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
            + "xsi:noNamespaceSchemaLocation=\"s.xsd\">\n  <n>1</n>\n  <n>x</n>\n</root>";

    private static final String FIX = "```json\\n{\\\"xpath\\\": \\\"/root/n[2]\\\", \\\"mode\\\": \\\"SET_VALUE\\\", "
            + "\\\"replacement\\\": \\\"2\\\"}\\n```\\nThe value must be an integer.";

    @TempDir
    Path temp;

    private EditorHost host;
    private Stage stage;
    private FakeChatServer server;
    private final PropertiesService properties = PropertiesServiceImpl.getInstance();
    private final List<String> keys = new java.util.ArrayList<>();

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        this.stage = stage;
        host = new EditorHost();
        stage.setScene(new Scene(host, 1100, 750));
        stage.show();
    }

    private void configure() throws Exception {
        server = new FakeChatServer();
        ProviderConfig fake = new ProviderConfig("fake-ui", "Fake", ProviderKind.OPENAI_COMPATIBLE, ProviderPreset.CUSTOM,
                server.url("/v1"), "fake-model", null, null, null, 0, 30);
        set(AiSettings.KEY_ENABLED, "true");
        set(AiSettings.KEY_PREVIEW_ALWAYS, "false");
        set(AiConsent.seenKey(fake.id()), "true");
        keys.add(ProviderStore.KEY_PROVIDERS);
        keys.add(ProviderStore.KEY_DEFAULT);
        ProviderStore.settings().save(List.of(fake));
        AiState.refresh();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void set(String key, String value) {
        keys.add(key);
        properties.set(key, value);
    }

    @AfterEach
    void cleanUp() {
        if (server != null) {
            server.close();
        }
        Properties all = properties.loadProperties();
        keys.forEach(all::remove);
        properties.saveProperties(all);
        AiState.refresh();
    }

    private Path openDocument() throws Exception {
        Files.writeString(temp.resolve("s.xsd"), XSD);
        Path xml = Files.writeString(temp.resolve("doc.xml"), XML);
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> host.openFile(xml));
        waitFor(() -> host.getActiveText().map(t -> t.contains("<n>x</n>")).orElse(false));
        return xml;
    }

    private static ValidationProblem problem() {
        return new ValidationProblem("XSD", "error", 3, "cvc-datatype-valid.1.2.1: 'x' is not a valid value for 'integer'.");
    }

    @Test
    void aVerifiedFixIsShownAndAppliedAsOneEdit() throws Exception {
        configure();
        assertTrue(AiState.available());
        openDocument();
        server.reply(FakeChatServer.Reply.json("{\"choices\":[{\"message\":{\"content\":\"" + FIX + "\"},"
                + "\"finish_reason\":\"stop\"}]}"));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> AiActions.fixProblem(host, problem()));
        Button apply = waitForVisible("#ai-proposal-apply");
        assertTrue(lookupText("#ai-proposal-status").contains("Verified"), lookupText("#ai-proposal-status"));
        assertTrue(server.last().body().contains("cvc-datatype-valid"), "the problem was sent");
        WaitForAsyncUtils.waitForAsyncFx(5000, apply::fire);
        waitFor(() -> host.getActiveText().map(t -> t.contains("<n>2</n>")).orElse(false));
        assertEquals(XML.replace("<n>x</n>", "<n>2</n>"), host.getActiveText().orElseThrow());
    }

    @Test
    void aProposalForAChangedDocumentIsNotApplied() throws Exception {
        configure();
        openDocument();
        server.reply(FakeChatServer.Reply.json("{\"choices\":[{\"message\":{\"content\":\"" + FIX + "\"},"
                + "\"finish_reason\":\"stop\"}]}"));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> AiActions.fixProblem(host, problem()));
        Button apply = waitForVisible("#ai-proposal-apply");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.replaceActiveTextRegion(0, 0, "<!-- edited -->"));
        WaitForAsyncUtils.waitForAsyncFx(5000, apply::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(host.getActiveText().orElseThrow().contains("<n>x</n>"), "not applied");
        assertTrue(lookupText("#ai-proposal-status").contains("changed"), lookupText("#ai-proposal-status"));
    }

    private static FakeChatServer.Reply answer(String content) {
        return FakeChatServer.Reply.json("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"},"
                + "\"finish_reason\":\"stop\"}]}");
    }

    @Test
    void askingWithAFilledConsoleRefinesTheCurrentQuery() throws Exception {
        configure();
        openDocument();
        server.reply(answer("```xpath\\n//n[1]\\n```\\nThe first n."));
        AtomicReference<String> console = new AtomicReference<>("//n");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> AiActions.askQuery(host, "only the first one", false, "//n",
                console::get, console::set));
        Button insert = waitForVisible("#ai-proposal-apply");
        String body = server.last().body();
        assertTrue(body.contains("The current expression"), body);
        assertTrue(body.contains("only the first one"), body);
        WaitForAsyncUtils.waitForAsyncFx(5000, insert::fire);
        assertEquals("//n[1]", console.get());
    }

    @Test
    void aFollowUpContinuesTheConversationWithTheUsersEdits() throws Exception {
        configure();
        openDocument();
        server.reply(answer("```xpath\\n//n[1]\\n```\\nThe first n."));
        AtomicReference<String> console = new AtomicReference<>("");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> AiActions.askQuery(host, "all n", false, null, console::get,
                console::set));
        Button insert = waitForVisible("#ai-proposal-apply");
        assertFalse(server.last().body().contains("The current expression"), "an empty console starts over");
        WaitForAsyncUtils.waitForAsyncFx(5000, insert::fire);
        assertEquals("//n[1]", console.get());
        console.set("//n[last()]");                       // the user edits the inserted query
        server.reply(answer("```xpath\\n//n[last()]/text()\\n```\\nIts text."));
        TextField field = (TextField) stage.getScene().lookup("#ai-proposal-followup");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            assertTrue(field.isVisible(), "the follow-up field is offered after an answer");
            field.setText("only the text");
            ((Button) stage.getScene().lookup("#ai-proposal-followup-send")).fire();
        });
        waitFor(() -> server.requests().size() == 2 && lookupText("#ai-proposal-status").startsWith("Answer 2"));
        String body = server.last().body();
        assertTrue(body.contains("all n"), "the first question is replayed: " + body);
        assertTrue(body.contains("The first n."), "the first answer is replayed: " + body);
        assertTrue(body.contains("only the text"), body);
        assertTrue(body.contains("//n[last()]") && body.contains("edited your last result"),
                "the user's edit goes along: " + body);
        WaitForAsyncUtils.waitForAsyncFx(5000, ((Button) stage.getScene().lookup("#ai-proposal-apply"))::fire);
        assertEquals("//n[last()]/text()", console.get());
    }

    @Test
    void aFixFollowUpIsRefusedOnceTheDocumentChanged() throws Exception {
        configure();
        openDocument();
        server.reply(answer(FIX));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> AiActions.fixProblem(host, problem()));
        waitForVisible("#ai-proposal-apply");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.replaceActiveTextRegion(0, 0, "<!-- edited -->"));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            ((TextField) stage.getScene().lookup("#ai-proposal-followup")).setText("use 3 instead");
            ((Button) stage.getScene().lookup("#ai-proposal-followup-send")).fire();
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, server.requests().size(), "nothing sent");
        assertTrue(lookupText("#ai-proposal-status").contains("changed"), lookupText("#ai-proposal-status"));
    }

    @Test
    void aRefinedStylesheetIsAppliedToItsTabAsOneEdit() throws Exception {
        configure();
        Path xml = openDocument();
        String before = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:template match=\"/\"><out/></xsl:template>\n</xsl:stylesheet>";
        Path xsl = Files.writeString(temp.resolve("t.xsl"), before);
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> host.openFile(xsl));
        waitFor(() -> host.getActiveText().map(t -> t.contains("<out/>")).orElse(false));
        OpenDocument tab = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.getActiveDocument().orElseThrow());
        String after = "<xsl:stylesheet version=\\\"3.0\\\" xmlns:xsl=\\\"http://www.w3.org/1999/XSL/Transform\\\">"
                + "<xsl:template match=\\\"/\\\"><out><xsl:value-of select=\\\"count(//n)\\\"/></out></xsl:template>"
                + "</xsl:stylesheet>";
        server.reply(answer("```xml\\n" + after + "\\n```\\nCounts n."));
        AiActions.StylesheetRequest request = new AiActions.StylesheetRequest(XML, "doc.xml", xml.toFile(), null, null,
                null, "output the count of n", "3.0", before, "t.xsl", tab);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> AiActions.generateStylesheet(host, request));
        Button apply = waitForVisible("#ai-proposal-apply");
        assertTrue(apply.getText().contains("t.xsl"), apply.getText());
        assertTrue(server.last().body().contains("The current stylesheet"), server.last().body());
        WaitForAsyncUtils.waitForAsyncFx(5000, apply::fire);
        waitFor(() -> host.getDocumentText(tab).map(t -> t.contains("count(//n)")).orElse(false));
        assertEquals(1, host.getOpenDocuments().stream().filter(d -> d.getDisplayName().endsWith(".xsl")).count(),
                "no new document when refining a tab");
    }

    @Test
    void withoutAiTheProblemMenuHasNoAiItems() throws Exception {
        AiState.refresh();
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(AiState.available());
        assertTrue(AiActions.problemItems(host, problem()).isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    private Button waitForVisible(String id) throws Exception {
        waitFor(() -> stage.getScene().lookup(id) instanceof Button b && b.isVisible());
        return (Button) stage.getScene().lookup(id);
    }

    private String lookupText(String id) {
        Node node = stage.getScene().lookup(id);
        return node instanceof Label label ? String.valueOf(label.getText()) : "";
    }

    private static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> {
            java.util.concurrent.atomic.AtomicBoolean ok = new java.util.concurrent.atomic.AtomicBoolean();
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                ok.set(Boolean.TRUE.equals(condition.call()));
                return null;
            });
            return ok.get();
        });
    }
}
