package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.SandboxedQuery;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
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
import org.fxt.freexmltoolkit.service.ai.context.ValidationProblemFacts;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.ChatResponse;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderSecrets;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderStore;
import org.fxt.freexmltoolkit.service.ai.provider.Providers;
import org.fxt.freexmltoolkit.service.ai.provider.StopReason;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * The AI actions of the editor (Phase 2): explain or fix a validation problem, a question → XPath / XQuery for the
 * Query Console, and a stylesheet from a source and a target sample. Each run: build the request within the data level
 * (background) → ask for consent / show the request (FX) → generate, verify, repair (background) → show the proposal in
 * the {@link AiProposalView} (FX). Nothing is applied without the user; nothing is written to disk.
 */
public final class AiActions {

    /** Repair rounds after the first answer (D8). */
    static final int MAX_REPAIRS = 3;

    private AiActions() {
    }

    // ---------------------------------------------------------------- entry points

    /** Explain / Fix with AI for a problem row's menu; empty when AI is not available. */
    public static List<MenuItem> problemItems(EditorHost host, ValidationProblem problem) {
        if (!AiState.available() || problem == null || problem.line() <= 0) {
            return List.of();
        }
        MenuItem explain = item("problems-ctx-ai-explain", I18n.t("AiActions.explain"), "bi-chat-left-text",
                () -> explainProblem(host, problem));
        MenuItem fix = item("problems-ctx-ai-fix", I18n.t("AiActions.fix"), "bi-stars", () -> fixProblem(host, problem));
        return List.of(new SeparatorMenuItem(), explain, fix);
    }

    private static MenuItem item(String id, String text, String icon, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setId(id);
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        item.setGraphic(graphic);
        item.setOnAction(e -> action.run());
        return item;
    }

    /** A short explanation of the problem (no verifier: the answer is text). */
    public static void explainProblem(EditorHost host, ValidationProblem problem) {
        Snapshot snapshot = Snapshot.of(host);
        if (snapshot == null) {
            return;
        }
        run(host, "explain", I18n.t("AiActions.explain.heading"), snapshot, (builder, schema) -> builder.forProblem(
                snapshot.text, schema, facts(problem), false), null, null, (view, outcome) -> view.show(
                new AiProposalView.Proposal(outcome.answer(), null, null, outcome.answer(), null, null, null, null),
                I18n.t("AiActions.explained")));
    }

    /** A verified fix of the problem, applied as one undo step on request. */
    public static void fixProblem(EditorHost host, ValidationProblem problem) {
        Snapshot snapshot = Snapshot.of(host);
        if (snapshot == null) {
            return;
        }
        ProblemFixVerifier[] verifier = new ProblemFixVerifier[1];
        run(host, "fix_problem", I18n.t("AiActions.fix.heading"), snapshot,
                (builder, schema) -> builder.forProblem(snapshot.text, schema, facts(problem), true),
                CandidateExtractor.json(), (context, schema) -> verifier[0] = new ProblemFixVerifier(snapshot.text,
                        facts(problem), schema, host.getActiveSchematron() == null ? null
                        : host.getActiveSchematron().toPath(), context),
                (view, outcome) -> {
                    EditPlan plan = outcome.candidate() == null ? null : verifier[0].planFor(outcome.candidate());
                    if (plan == null) {
                        view.show(new AiProposalView.Proposal(outcome.answer(), outcome.verification(), null, null, null,
                                null, null, null), statusOf(outcome));
                        return;
                    }
                    String after = plan.applyTo(snapshot.text);
                    AiProposalView.Proposal proposal = new AiProposalView.Proposal(outcome.answer(),
                            outcome.verification(), AiProposalView.diff(window(snapshot.text, plan, 0),
                            window(after, plan, after.length() - snapshot.text.length())), after,
                            I18n.t("AiActions.applyFix"), () -> {
                                host.replaceActiveTextRegion(plan.minStart(), plan.maxEnd(), plan.mergedRegion(snapshot.text));
                                host.getQuickFixController().fixApplied();
                            }, () -> snapshot.staleReason(host), null);
                    view.show(proposal, statusOf(outcome));
                });
    }

    /**
     * A question → an XPath expression or XQuery query for the active document; {@code place} puts the verified (or,
     * after confirmation, unverified) expression into the console's input.
     */
    public static void askQuery(EditorHost host, String question, boolean xquery, Consumer<String> place) {
        Snapshot snapshot = Snapshot.of(host);
        if (snapshot == null) {
            return;
        }
        run(host, xquery ? "ask_xquery" : "ask_xpath", I18n.t(xquery ? "AiActions.askXQuery.heading"
                : "AiActions.askXPath.heading"), snapshot,
                (builder, schema) -> builder.forQuery(snapshot.text, schema, question, xquery),
                xquery ? CandidateExtractor.xquery() : CandidateExtractor.xpath(),
                (context, schema) -> new QueryVerifier(xquery ? SandboxedQuery.Language.XQUERY
                        : SandboxedQuery.Language.XPATH, snapshot.text, snapshot.uri(), context),
                (view, outcome) -> view.show(new AiProposalView.Proposal(outcome.answer(), outcome.verification(),
                        outcome.candidate() == null ? null : AiProposalView.code(outcome.candidate()), outcome.candidate(),
                        outcome.candidate() == null ? null : I18n.t("AiActions.insertQuery"),
                        outcome.candidate() == null ? null : () -> place.accept(outcome.candidate()), null, null),
                        statusOf(outcome)));
    }

    /** What the Generate Stylesheet dialog asks for. */
    public record StylesheetRequest(String source, String sourceName, File sourceFile, String targetSample,
                                    File targetSampleFile, File targetSchema, String notes, String version) {
    }

    /** A stylesheet that turns the source into a document like the target sample; offered as a new unsaved document. */
    public static void generateStylesheet(EditorHost host, StylesheetRequest request) {
        Snapshot snapshot = new Snapshot(null, null, request.source(), request.sourceFile() == null ? null
                : request.sourceFile().toPath());
        run(host, "generate_xslt", I18n.t("AiActions.xslt.heading"), snapshot,
                (builder, schema) -> builder.forStylesheet(request.source(), request.targetSample(),
                        request.targetSchema() == null ? null : request.targetSchema().toPath(), request.notes(),
                        request.version()),
                CandidateExtractor.xslt(), (context, schema) -> new XsltVerifier(request.source(),
                        request.targetSample(), request.targetSchema() == null ? null : request.targetSchema().toPath(),
                        context),
                (view, outcome) -> {
                    Runnable open = outcome.candidate() == null ? null : () -> host.openGeneratedDocument(
                            outcome.candidate(), EditorFileType.XSLT, I18n.t("AiActions.xslt.documentName"));
                    view.show(new AiProposalView.Proposal(outcome.answer(), outcome.verification(),
                            outcome.candidate() == null ? null : AiProposalView.code(outcome.candidate()),
                            outcome.candidate(), null, null, null, open), statusOf(outcome));
                    if (open != null && outcome.status() == VerifiedGeneration.Status.VERIFIED) {
                        open.run();
                    }
                }, List.of(request.targetSampleFile(), request.targetSchema()));
    }

    // ---------------------------------------------------------------- the run

    /** Builds the request from the level and the schema of the document. */
    @FunctionalInterface
    interface ContextFactory {
        AiContext build(ContextBuilder builder, Path schema);
    }

    @FunctionalInterface
    interface VerifierFactory {
        Verifier create(ToolContext context, Path schema);
    }

    @FunctionalInterface
    interface Presenter {
        void present(AiProposalView view, VerifiedGeneration.Outcome outcome);
    }

    private static void run(EditorHost host, String actionId, String heading, Snapshot snapshot, ContextFactory contextFactory,
                            CandidateExtractor extractor, VerifierFactory verifierFactory, Presenter presenter) {
        run(host, actionId, heading, snapshot, contextFactory, extractor, verifierFactory, presenter, List.of());
    }

    private static void run(EditorHost host, String actionId, String heading, Snapshot snapshot, ContextFactory contextFactory,
                            CandidateExtractor extractor, VerifierFactory verifierFactory, Presenter presenter,
                            List<File> extraFiles) {
        Optional<ProviderConfig> configured = AiState.provider();
        if (configured.isEmpty()) {
            DialogHelper.showWarning(I18n.t("AiActions.noProvider.title"), I18n.t("AiActions.noProvider.header"),
                    I18n.t("AiActions.noProvider.content"));
            return;
        }
        ProviderConfig config = configured.get();
        ProviderStore store = ProviderStore.settings();
        ChatProvider provider;
        try {
            provider = Providers.create(config, store, ProviderSecrets.settings());
        } catch (ProviderException e) {
            DialogHelper.showWarning(I18n.t("AiActions.noProvider.title"), e.getMessage(), "");
            return;
        }
        AiSettings settings = AiSettings.current();
        boolean local = config.isLocal(store.localHosts());
        ContextBuilder builder = new ContextBuilder(settings.effectiveLevel(local), ContextBudget.DEFAULT);
        AiProposalView view = AiProposalView.open(host, heading);
        CancelToken cancel = new CancelToken();
        long started = System.nanoTime();
        view.running(I18n.t("AiActions.preparing"), cancel::cancel);
        String text = snapshot.text;
        FxtGui.executorService.submit(() -> {
            Path schema = schemaOf(host, text, snapshot);
            AiContext context = contextFactory.build(builder, schema);
            Platform.runLater(() -> {
                if (cancel.isCancelled()) {
                    return;
                }
                if (!AiConsent.confirm(config, local, context)) {
                    view.failed(I18n.t("AiActions.notSent"));
                    UsageEvents.aiAction(actionId, kindOf(config, local), "cancelled", 0, started);
                    return;
                }
                view.running(I18n.t("AiActions.asking", config.name()), cancel::cancel);
                FxtGui.executorService.submit(() -> generate(host, actionId, view, provider, config, local, context,
                        extractor, verifierFactory, schema, snapshot, extraFiles, presenter, cancel, started));
            });
        });
    }

    private static void generate(EditorHost host, String actionId, AiProposalView view, ChatProvider provider,
                                 ProviderConfig config, boolean local, AiContext context, CandidateExtractor extractor,
                                 VerifierFactory verifierFactory, Path schema, Snapshot snapshot, List<File> extraFiles,
                                 Presenter presenter, CancelToken cancel, long started) {
        ChatRequest request = new ChatRequest(context.systemPrompt(),
                List.of(org.fxt.freexmltoolkit.service.ai.provider.ChatMessage.user(context.userText())), List.of(), 0,
                null);
        try {
            VerifiedGeneration.Outcome outcome;
            if (extractor == null) {
                ChatResponse response = provider.chat(request, delta -> Platform.runLater(() -> view.streaming(delta)),
                        cancel);
                outcome = new VerifiedGeneration.Outcome(response.stop() == StopReason.REFUSED
                        ? VerifiedGeneration.Status.REFUSED : VerifiedGeneration.Status.VERIFIED, null, null, 0,
                        response.text(), List.of());
            } else {
                List<File> files = new ArrayList<>();
                if (snapshot.path != null) {
                    files.add(snapshot.path.toFile());
                }
                if (schema != null) {
                    files.add(schema.toFile());
                }
                files.addAll(extraFiles.stream().filter(f -> f != null).toList());
                ToolContext toolContext = InAppToolContext.forFiles(files, projectFolders(),
                        AiSettings.current().toolLimits());
                Verifier verifier = verifierFactory.create(toolContext, schema);
                outcome = new VerifiedGeneration().run(provider, request, extractor, verifier, MAX_REPAIRS,
                        new VerifiedGeneration.Listener() {
                            @Override
                            public void round(int round, VerifiedGeneration.Phase phase) {
                                String key = phase == VerifiedGeneration.Phase.GENERATING ? "AiActions.round.generating"
                                        : "AiActions.round.checking";
                                Platform.runLater(() -> view.running(I18n.t(key, round + 1), cancel::cancel));
                            }
                        }, cancel);
            }
            VerifiedGeneration.Outcome result = outcome;
            UsageEvents.aiAction(actionId, kindOf(config, local), outcomeOf(result.status()), result.repairRounds(),
                    started);
            Platform.runLater(() -> {
                if (result.status() == VerifiedGeneration.Status.REFUSED) {
                    view.failed(I18n.t("AiActions.refused"));
                } else {
                    presenter.present(view, result);
                }
            });
        } catch (ProviderException e) {
            UsageEvents.aiAction(actionId, kindOf(config, local), e.kind() == ProviderException.Kind.CANCELLED
                    ? "cancelled" : "failed", 0, started);
            Platform.runLater(() -> view.failed(e.kind() == ProviderException.Kind.CANCELLED
                    ? I18n.t("AiActions.cancelled") : e.getMessage()));
        } catch (RuntimeException e) {
            UsageEvents.aiAction(actionId, kindOf(config, local), "failed", 0, started);
            Platform.runLater(() -> view.failed(I18n.t("AiActions.failed", e.getClass().getSimpleName())));
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The document a run is about, to refuse applying after it changed. */
    private record Snapshot(OpenDocument document, String revision, String text, Path path) {

        static Snapshot of(EditorHost host) {
            Optional<OpenDocument> document = host.getActiveDocument();
            Optional<String> text = host.getActiveText();
            if (document.isEmpty() || text.isEmpty()) {
                DialogHelper.showInformation(I18n.t("AiActions.noDocument.title"), I18n.t("AiActions.noDocument.header"),
                        "");
                return null;
            }
            return new Snapshot(document.get(), host.activeRevisionKey(), text.get(), document.get().getPath());
        }

        String uri() {
            return path == null ? null : path.toUri().toString();
        }

        /** Why the proposal can no longer be applied, or {@code null}. */
        String staleReason(EditorHost host) {
            Optional<OpenDocument> active = host.getActiveDocument();
            if (active.isEmpty() || active.get() != document) {
                return I18n.t("AiActions.stale.otherDocument");
            }
            if (!host.activeRevisionKey().equals(revision) || !host.getActiveText().orElse("").equals(text)) {
                return I18n.t("AiActions.stale.changed");
            }
            return null;
        }
    }

    private static Path schemaOf(EditorHost host, String text, Snapshot snapshot) {
        if (snapshot.document == null) {
            return null;
        }
        try {
            File file = host.schemaForValidation(text).get();
            return file == null ? null : file.toPath();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Path> projectFolders() {
        return ProjectService.getInstance().current().map(FxtProject::folders).orElse(List.of());
    }

    private static ValidationProblemFacts facts(ValidationProblem p) {
        return new ValidationProblemFacts(p.source(), p.severity(), p.line(), p.column(), p.message(), p.ruleId(),
                p.context());
    }

    private static String statusOf(VerifiedGeneration.Outcome outcome) {
        return switch (outcome.status()) {
            case VERIFIED -> I18n.t("AiActions.verified", outcome.repairRounds());
            case FAILED -> I18n.t("AiActions.unverified", outcome.repairRounds());
            case REFUSED -> I18n.t("AiActions.refused");
            case CANCELLED -> I18n.t("AiActions.cancelled");
        };
    }

    private static String outcomeOf(VerifiedGeneration.Status status) {
        return switch (status) {
            case VERIFIED -> "verified";
            case FAILED -> "unverified";
            case REFUSED -> "refused";
            case CANCELLED -> "cancelled";
        };
    }

    private static String kindOf(ProviderConfig config, boolean local) {
        if (local) {
            return "local";
        }
        return config.kind() == org.fxt.freexmltoolkit.service.ai.provider.ProviderKind.ANTHROPIC ? "anthropic"
                : "openai_compatible";
    }

    /** The lines around an edit, three before and after, in {@code text} (the edit ends {@code delta} later). */
    static String window(String text, EditPlan plan, int delta) {
        int startLine = lineOf(text, plan.minStart());
        int endLine = lineOf(text, Math.max(plan.minStart(), Math.min(text.length(), plan.maxEnd() + delta)));
        String[] lines = text.split("\n", -1);
        int from = Math.max(0, startLine - 3);
        int to = Math.min(lines.length, endLine + 4);
        return String.join("\n", java.util.Arrays.asList(lines).subList(from, to));
    }

    private static int lineOf(String text, int offset) {
        int line = 0;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
