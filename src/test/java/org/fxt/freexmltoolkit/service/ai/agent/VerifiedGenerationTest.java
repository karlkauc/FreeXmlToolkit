package org.fxt.freexmltoolkit.service.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.service.SandboxedQuery;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatMessage;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.StopReason;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifiedGenerationTest {

    private static final String XML = "<f:funds xmlns:f=\"urn:f\"><f:fund><f:nav>1</f:nav></f:fund><f:fund><f:nav>2</f:nav></f:fund></f:funds>";

    @TempDir
    Path temp;

    private QueryVerifier xpathVerifier() {
        return new QueryVerifier(SandboxedQuery.Language.XPATH, XML, null,
                InAppToolContext.forFiles(List.of(), List.of(temp), ToolLimits.DEFAULTS));
    }

    private static ChatRequest first() {
        return new ChatRequest("system", List.of(ChatMessage.user("count the funds")), List.of(), 0, null);
    }

    @Test
    void aFailingCandidateIsRepairedWithTheErrorFacts() throws Exception {
        ScriptedProvider provider = new ScriptedProvider("```xpath\ncount(//fund[\n```", "```xpath\ncount(//*:fund)\n```");
        VerifiedGeneration.Outcome outcome = new VerifiedGeneration().run(provider, first(), CandidateExtractor.xpath(),
                xpathVerifier(), 3, null, CancelToken.none());
        assertEquals(VerifiedGeneration.Status.VERIFIED, outcome.status());
        assertEquals("count(//*:fund)", outcome.candidate());
        assertEquals(1, outcome.repairRounds());
        String repair = provider.requests().get(1).messages().getLast().text();
        assertTrue(repair.contains("XPST0003") || repair.contains("compile"), repair);
        assertTrue(repair.contains("<tool-result>"), "feedback is delimited data");
        // append-only: the second request starts with the first request's messages and the first answer
        assertEquals(first().messages().getFirst(), provider.requests().get(1).messages().getFirst());
        assertEquals("```xpath\ncount(//fund[\n```", provider.requests().get(1).messages().get(1).text());
    }

    @Test
    void aPrefixedExpressionFailsBecauseTheConsoleDeclaresNoPrefixes() throws Exception {
        ScriptedProvider provider = new ScriptedProvider("```xpath\ncount(//f:fund)\n```", "```xpath\ncount(//*:fund)\n```");
        VerifiedGeneration.Outcome outcome = new VerifiedGeneration().run(provider, first(), CandidateExtractor.xpath(),
                xpathVerifier(), 3, null, CancelToken.none());
        assertEquals(VerifiedGeneration.Status.VERIFIED, outcome.status());
        assertEquals(1, outcome.repairRounds());
    }

    @Test
    void neverRightEndsFailedWithTheLastReport() throws Exception {
        ScriptedProvider provider = new ScriptedProvider("```xpath\n((\n```");
        VerifiedGeneration.Outcome outcome = new VerifiedGeneration().run(provider, first(), CandidateExtractor.xpath(),
                xpathVerifier(), 2, null, CancelToken.none());
        assertEquals(VerifiedGeneration.Status.FAILED, outcome.status());
        assertEquals(3, provider.requests().size());
        assertFalse(outcome.verification().verified());
    }

    @Test
    void anAnswerWithoutCodeIsAFailedRound() throws Exception {
        ScriptedProvider provider = new ScriptedProvider("I think count of funds", "```xpath\ncount(//*:fund)\n```");
        VerifiedGeneration.Outcome outcome = new VerifiedGeneration().run(provider, first(), CandidateExtractor.xpath(),
                xpathVerifier(), 3, null, CancelToken.none());
        assertEquals(VerifiedGeneration.Status.VERIFIED, outcome.status());
        assertTrue(provider.requests().get(1).messages().getLast().text().contains("no ```xpath block"));
    }

    @Test
    void aRefusalEndsAtOnce() throws Exception {
        ScriptedProvider provider = new ScriptedProvider("no").stopReason(StopReason.REFUSED);
        VerifiedGeneration.Outcome outcome = new VerifiedGeneration().run(provider, first(), CandidateExtractor.xpath(),
                xpathVerifier(), 3, null, CancelToken.none());
        assertEquals(VerifiedGeneration.Status.REFUSED, outcome.status());
        assertEquals(1, provider.requests().size());
    }

    @Test
    void cancelBetweenRounds() {
        CancelToken cancel = new CancelToken();
        ScriptedProvider provider = new ScriptedProvider("```xpath\n((\n```").beforeAnswer(() -> { });
        VerifiedGeneration.Listener listener = (round, phase) -> {
            if (round == 1) {
                cancel.cancel();
            }
        };
        ProviderException e = assertThrows(ProviderException.class, () -> new VerifiedGeneration().run(provider, first(),
                CandidateExtractor.xpath(), xpathVerifier(), 3, listener, cancel));
        assertEquals(ProviderException.Kind.CANCELLED, e.kind());
    }

    @Test
    void extractor() {
        assertEquals("a", CandidateExtractor.xpath().extract("text\n```xpath\na\n```\nmore").candidate());
        assertEquals("b", CandidateExtractor.xpath().extract("```\nb\n```").candidate());
        assertFalse(CandidateExtractor.xpath().extract("```xpath\na\n```\n```xpath\nb\n```").ok());
        assertFalse(CandidateExtractor.xpath().extract("no code").ok());
        assertEquals("<x/>", CandidateExtractor.xslt().extract("```xml\n<x/>\n```").candidate());
    }
}
