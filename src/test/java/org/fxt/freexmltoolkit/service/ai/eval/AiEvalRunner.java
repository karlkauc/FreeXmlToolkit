package org.fxt.freexmltoolkit.service.ai.eval;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.fxt.freexmltoolkit.service.SandboxedQuery;
import org.fxt.freexmltoolkit.service.ai.agent.CandidateExtractor;
import org.fxt.freexmltoolkit.service.ai.agent.InAppToolContext;
import org.fxt.freexmltoolkit.service.ai.agent.ProblemFixVerifier;
import org.fxt.freexmltoolkit.service.ai.agent.QueryVerifier;
import org.fxt.freexmltoolkit.service.ai.agent.VerifiedGeneration;
import org.fxt.freexmltoolkit.service.ai.agent.Verifier;
import org.fxt.freexmltoolkit.service.ai.agent.XsltVerifier;
import org.fxt.freexmltoolkit.service.ai.context.AiContext;
import org.fxt.freexmltoolkit.service.ai.context.ContextBudget;
import org.fxt.freexmltoolkit.service.ai.context.ContextBuilder;
import org.fxt.freexmltoolkit.service.ai.context.DataLevel;
import org.fxt.freexmltoolkit.service.ai.context.ValidationProblemFacts;
import org.fxt.freexmltoolkit.service.ai.provider.AuthHeader;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatMessage;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.OpenAiCompatibleProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderKind;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderPreset;
import org.fxt.freexmltoolkit.service.ai.provider.anthropic.AnthropicProvider;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Runs the evaluation set ({@code src/test/resources/ai/evals/cases.json}) against a real model and writes
 * {@code build/ai-eval.md}: per case whether the candidate was verified on the first try, after repair rounds, or not
 * at all, and whether a query's result equals the expected one. Not part of {@code test}; started by
 * {@code ./gradlew aiEval -Pai.baseUrl=… -Pai.model=… [-Pai.kind=anthropic]}, the key from {@code FXT_AI_KEY}.
 */
public final class AiEvalRunner {

    private record Result(String id, String kind, String status, int repairs, boolean expectedMatched, long millis,
                          String note) {
    }

    public static void main(String[] args) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        String baseUrl = require("ai.baseUrl");
        String model = require("ai.model");
        boolean anthropic = "anthropic".equalsIgnoreCase(System.getProperty("ai.kind", ""));
        String key = System.getenv("FXT_AI_KEY");
        ProviderPreset preset = anthropic ? ProviderPreset.ANTHROPIC : ProviderPreset.CUSTOM;
        ProviderConfig config = new ProviderConfig("eval", "eval", anthropic ? ProviderKind.ANTHROPIC
                : ProviderKind.OPENAI_COMPATIBLE, preset, baseUrl, model, null, AuthHeader.BEARER, null, 4096, 900);
        ChatProvider provider = anthropic ? new AnthropicProvider(config, key) : new OpenAiCompatibleProvider(config, key);
        String only = System.getProperty("ai.cases", "");

        JsonObject set;
        try (InputStream in = AiEvalRunner.class.getResourceAsStream("/ai/evals/cases.json")) {
            set = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Path work = Files.createTempDirectory("fxt-ai-eval");
        Map<String, Path> schemas = new java.util.HashMap<>();
        for (Map.Entry<String, JsonElement> e : set.getAsJsonObject("schemas").entrySet()) {
            schemas.put(e.getKey(), Files.writeString(work.resolve(e.getKey() + ".xsd"), e.getValue().getAsString()));
        }
        ToolContext context = InAppToolContext.forFiles(List.of(), List.of(work), ToolLimits.DEFAULTS);
        ContextBuilder builder = new ContextBuilder(DataLevel.EXCERPTS, ContextBudget.DEFAULT);
        List<Result> results = new ArrayList<>();
        for (JsonElement element : set.getAsJsonArray("cases")) {
            JsonObject c = element.getAsJsonObject();
            String id = c.get("id").getAsString();
            if (!only.isBlank() && !List.of(only.split(",")).contains(id)) {
                continue;
            }
            String kind = c.get("kind").getAsString();
            String doc = c.has("doc") ? set.getAsJsonObject("documents").get(c.get("doc").getAsString()).getAsString()
                    : c.get("xml").getAsString();
            Path schema = c.has("schema") ? schemas.get(c.get("schema").getAsString()) : null;
            AiContext ai;
            CandidateExtractor extractor;
            Verifier verifier;
            switch (kind) {
                case "xpath", "xquery" -> {
                    boolean xquery = kind.equals("xquery");
                    ai = builder.forQuery(doc, null, c.get("question").getAsString(), xquery);
                    extractor = xquery ? CandidateExtractor.xquery() : CandidateExtractor.xpath();
                    verifier = new QueryVerifier(xquery ? SandboxedQuery.Language.XQUERY : SandboxedQuery.Language.XPATH,
                            doc, null, context);
                }
                case "xslt" -> {
                    String target = c.get("target").getAsString();
                    ai = builder.forStylesheet(doc, target, schema, c.has("notes") ? c.get("notes").getAsString() : "",
                            "3.0");
                    extractor = CandidateExtractor.xslt();
                    verifier = new XsltVerifier(doc, target, schema, context);
                }
                default -> {
                    ValidationProblemFacts problem = new ValidationProblemFacts("XSD", "error", c.get("line").getAsInt(), 0,
                            c.get("message").getAsString(), null, null);
                    ai = builder.forProblem(doc, schema, problem, true);
                    extractor = CandidateExtractor.json();
                    verifier = new ProblemFixVerifier(doc, problem, schema, null, context);
                }
            }
            long start = System.nanoTime();
            String status;
            int repairs = 0;
            boolean matched = false;
            String note = "";
            try {
                VerifiedGeneration.Outcome outcome = new VerifiedGeneration().run(provider, new ChatRequest(
                        ai.systemPrompt(), List.of(ChatMessage.user(ai.userText())), List.of(), 0, null), extractor,
                        verifier, 3, null, CancelToken.none());
                repairs = outcome.repairRounds();
                status = outcome.status() == VerifiedGeneration.Status.VERIFIED
                        ? (repairs == 0 ? "verified first try" : "verified after repair") : outcome.status().name()
                        .toLowerCase(Locale.ROOT);
                if (c.has("expected") && verifier instanceof QueryVerifier q && q.lastOutcome() != null) {
                    matched = normalise(q.lastOutcome().output()).equals(normalise(c.get("expected").getAsString()));
                    note = matched ? "" : "`" + oneLine(outcome.candidate()) + "` got: " + normalise(q.lastOutcome().output());
                } else if (kind.equals("xslt") && verifier instanceof XsltVerifier x) {
                    matched = outcome.verification() != null && outcome.verification().checks().stream()
                            .anyMatch(ch -> ch.detail() != null && ch.detail().contains("identical"));
                } else {
                    matched = outcome.status() == VerifiedGeneration.Status.VERIFIED;
                }
            } catch (Exception e) {
                status = "error";
                note = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            Result r = new Result(id, kind, status, repairs, matched, (System.nanoTime() - start) / 1_000_000, note);
            results.add(r);
            System.out.println(r);
        }
        StringBuilder md = new StringBuilder("# AI evaluation\n\nModel `" + model + "` at `" + baseUrl + "`, "
                + java.time.LocalDate.now() + "\n\n| Case | Kind | Outcome | Repairs | Expected result | ms | Note |\n"
                + "|---|---|---|---|---|---|---|\n");
        for (Result r : results) {
            md.append("| ").append(r.id()).append(" | ").append(r.kind()).append(" | ").append(r.status()).append(" | ")
                    .append(r.repairs()).append(" | ").append(r.expectedMatched() ? "yes" : "no").append(" | ")
                    .append(r.millis()).append(" | ").append(r.note().replace("|", "\\|").replace("\n", " ")).append(" |\n");
        }
        long first = results.stream().filter(r -> r.status().equals("verified first try")).count();
        long repaired = results.stream().filter(r -> r.status().equals("verified after repair")).count();
        long expected = results.stream().filter(Result::expectedMatched).count();
        md.append("\n**Total:** ").append(results.size()).append(" cases — verified first try ").append(first)
                .append(", after repair ").append(repaired).append(", not verified ")
                .append(results.size() - first - repaired).append("; expected result reached ").append(expected)
                .append(".\n");
        Path out = Path.of("build", "ai-eval.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md);
        System.out.println(md);
        System.exit(0);
    }

    private static String oneLine(String text) {
        String line = text == null ? "" : text.strip().replaceAll("\\s+", " ").replace("|", "\\|").replace("`", "'");
        return line.length() > 120 ? line.substring(0, 120) + "…" : line;
    }

    private static String normalise(String text) {
        // string values count: tags of selected elements go, any whitespace run is one separator
        return text == null ? "" : text.replaceAll("<[^>]+>", " ").strip().replaceAll("\\s+", " ")
                .replaceAll("\\.0+(?=\\s|$)", "");
    }

    private static String require(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set -P" + name + "=…");
        }
        return value;
    }
}
