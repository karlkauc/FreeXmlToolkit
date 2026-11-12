package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.SandboxedQuery;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.agent.CandidateExtractor;
import org.fxt.freexmltoolkit.service.ai.agent.ProblemFixVerifier;
import org.fxt.freexmltoolkit.service.ai.agent.QueryVerifier;
import org.fxt.freexmltoolkit.service.ai.agent.VerifiedGeneration;
import org.fxt.freexmltoolkit.service.ai.agent.Verifier;
import org.fxt.freexmltoolkit.service.ai.agent.XsltVerifier;
import org.fxt.freexmltoolkit.service.ai.context.AiContext;
import org.fxt.freexmltoolkit.service.ai.context.ContextBudget;
import org.fxt.freexmltoolkit.service.ai.context.ContextBuilder;
import org.fxt.freexmltoolkit.service.ai.context.ValidationProblemFacts;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderSecrets;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderStore;
import org.fxt.freexmltoolkit.service.ai.provider.Providers;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * The AI actions of the editor (Phase 2): explain or fix a validation problem, a question → XPath / XQuery for the
 * Query Console, and a stylesheet from a source and a target sample (or a revision of the chosen stylesheet). Each
 * action is an {@link AiConversation}: build the request within the data level (background) → ask for consent / show
 * the request (FX) → generate, verify, repair (background) → show the proposal in the {@link AiProposalView} (FX);
 * follow-ups in the panel continue it. Nothing is applied without the user; nothing is written to disk.
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

    /** A short explanation of the problem (no verifier: the answer is text); follow-up questions continue it. */
    public static void explainProblem(EditorHost host, ValidationProblem problem) {
        Snapshot snapshot = Snapshot.of(host);
        if (snapshot == null) {
            return;
        }
        start(host, "explain", I18n.t("AiActions.explain.heading"), snapshot, (builder, schema) -> builder.forProblem(
                snapshot.text, schema, facts(problem), false), null, null, (conversation, outcome) ->
                conversation.view().show(new AiProposalView.Proposal(outcome.answer(), null, null, outcome.answer(),
                        null, null, null, null), I18n.t("AiActions.explained")), List.of(), null);
    }

    /** A verified fix of the problem, applied as one undo step on request; follow-ups refine the fix. */
    public static void fixProblem(EditorHost host, ValidationProblem problem) {
        Snapshot snapshot = Snapshot.of(host);
        if (snapshot == null) {
            return;
        }
        ProblemFixVerifier[] verifier = new ProblemFixVerifier[1];
        start(host, "fix_problem", I18n.t("AiActions.fix.heading"), snapshot,
                (builder, schema) -> builder.forProblem(snapshot.text, schema, facts(problem), true),
                CandidateExtractor.json(), (context, schema) -> verifier[0] = new ProblemFixVerifier(snapshot.text,
                        facts(problem), schema, host.getActiveSchematron() == null ? null
                        : host.getActiveSchematron().toPath(), context),
                (conversation, outcome) -> {
                    AiProposalView view = conversation.view();
                    EditPlan plan = outcome.candidate() == null ? null : verifier[0].planFor(outcome.candidate());
                    if (plan == null) {
                        view.show(new AiProposalView.Proposal(outcome.answer(), outcome.verification(), null, null, null,
                                null, null, null), statusOf(conversation, outcome));
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
                    view.show(proposal, statusOf(conversation, outcome));
                }, List.of(), conversation -> conversation.setFollowUpBlocker(() -> snapshot.staleReason(host)));
    }

    /**
     * A question → an XPath expression or XQuery query for the active document; {@code place} puts the verified (or,
     * after confirmation, unverified) expression into the console's input.
     */
    public static void askQuery(EditorHost host, String question, boolean xquery, Consumer<String> place) {
        askQuery(host, question, xquery, null, () -> null, place);
    }

    /**
     * A question → an XPath expression or XQuery query for the active document. A non-blank {@code currentQuery} (the
     * console's input) is sent along and refined; {@code inputNow} reads the console's input later, so a follow-up in
     * the AI panel carries the user's own edits of an inserted query; {@code place} puts a result into the console.
     */
    public static void askQuery(EditorHost host, String question, boolean xquery, String currentQuery,
                                Supplier<String> inputNow, Consumer<String> place) {
        Snapshot snapshot = Snapshot.of(host);
        if (snapshot == null) {
            return;
        }
        start(host, xquery ? "ask_xquery" : "ask_xpath", I18n.t(xquery ? "AiActions.askXQuery.heading"
                : "AiActions.askXPath.heading"), snapshot,
                (builder, schema) -> builder.forQuery(snapshot.text, schema, question, xquery, currentQuery),
                xquery ? CandidateExtractor.xquery() : CandidateExtractor.xpath(),
                (context, schema) -> new QueryVerifier(xquery ? SandboxedQuery.Language.XQUERY
                        : SandboxedQuery.Language.XPATH, snapshot.text, snapshot.uri(), context),
                (conversation, outcome) -> {
                    String candidate = outcome.candidate();
                    conversation.view().show(new AiProposalView.Proposal(outcome.answer(), outcome.verification(),
                            candidate == null ? null : AiProposalView.code(candidate), candidate,
                            candidate == null ? null : I18n.t("AiActions.insertQuery"),
                            candidate == null ? null : () -> {
                                place.accept(candidate);
                                conversation.placed(candidate, inputNow);
                            }, null, null), statusOf(conversation, outcome));
                }, List.of(), conversation -> {
                    if (currentQuery != null && !currentQuery.isBlank()) {
                        conversation.placed(currentQuery, inputNow);
                    }
                });
    }

    /**
     * What the Generate Stylesheet dialog asks for.
     *
     * @param targetSample        a document with the wanted structure; may be blank when refining
     * @param currentStylesheet   the stylesheet to refine (text), or {@code null} for a new one
     * @param stylesheetName      its name for the panel
     * @param stylesheetDocument  the open tab holding it ({@code Apply to …} replaces its text), or {@code null}
     */
    public record StylesheetRequest(String source, String sourceName, File sourceFile, String targetSample,
                                    File targetSampleFile, File targetSchema, String notes, String version,
                                    String currentStylesheet, String stylesheetName, OpenDocument stylesheetDocument) {

        public StylesheetRequest(String source, String sourceName, File sourceFile, String targetSample,
                                 File targetSampleFile, File targetSchema, String notes, String version) {
            this(source, sourceName, sourceFile, targetSample, targetSampleFile, targetSchema, notes, version, null,
                    null, null);
        }

        /** Whether an existing stylesheet is refined. */
        public boolean refining() {
            return currentStylesheet != null && !currentStylesheet.isBlank();
        }
    }

    /**
     * A stylesheet that turns the source into a document like the target sample — or the chosen stylesheet revised as
     * the notes ask; offered as a new unsaved document or, when refining an open tab, applied to it as one undo step.
     */
    public static void generateStylesheet(EditorHost host, StylesheetRequest request) {
        Snapshot snapshot = new Snapshot(null, null, request.source(), request.sourceFile() == null ? null
                : request.sourceFile().toPath());
        OpenDocument target = request.refining() ? request.stylesheetDocument() : null;
        Supplier<String> targetText = target == null ? () -> null : () -> host.getDocumentText(target).orElse(null);
        start(host, "generate_xslt", I18n.t(request.refining() ? "AiActions.xsltRefine.heading"
                        : "AiActions.xslt.heading"), snapshot,
                (builder, schema) -> builder.forStylesheet(request.source(), request.targetSample(),
                        request.targetSchema() == null ? null : request.targetSchema().toPath(), request.notes(),
                        request.version(), request.currentStylesheet()),
                CandidateExtractor.xslt(), (context, schema) -> new XsltVerifier(request.source(),
                        request.targetSample(), request.targetSchema() == null ? null : request.targetSchema().toPath(),
                        context),
                (conversation, outcome) -> {
                    String candidate = outcome.candidate();
                    Runnable open = candidate == null ? null : () -> {
                        OpenDocument opened = host.openGeneratedDocument(candidate, EditorFileType.XSLT,
                                I18n.t("AiActions.xslt.documentName"));
                        if (opened != null) {
                            conversation.placed(candidate, () -> host.getDocumentText(opened).orElse(null));
                        }
                    };
                    String baseline = conversation.artifactAtTurnStart();
                    boolean canApply = candidate != null && target != null;
                    Runnable apply = !canApply ? null : () -> {
                        if (host.replaceDocumentText(target, candidate)) {
                            conversation.placed(candidate, targetText);
                        }
                    };
                    Supplier<String> stale = !canApply ? null : () -> {
                        String now = targetText.get();
                        if (now == null) {
                            return I18n.t("AiActions.stale.stylesheetClosed");
                        }
                        return baseline != null && !now.equals(baseline) ? I18n.t("AiActions.stale.stylesheetChanged")
                                : null;
                    };
                    conversation.view().show(new AiProposalView.Proposal(outcome.answer(), outcome.verification(),
                            candidate == null ? null : AiProposalView.code(candidate), candidate,
                            canApply ? I18n.t("AiActions.applyToStylesheet", request.stylesheetName()) : null, apply,
                            stale, open), statusOf(conversation, outcome));
                    if (open != null && !request.refining() && conversation.turn() == 1
                            && outcome.status() == VerifiedGeneration.Status.VERIFIED) {
                        open.run();
                    }
                }, java.util.Arrays.asList(request.targetSampleFile(), request.targetSchema()), conversation -> {
                    if (target != null) {
                        conversation.placed(request.currentStylesheet(), targetText);
                    }
                });
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

    /** The provider and the data level of a conversation, fixed at its start. */
    record Setup(EditorHost host, ProviderConfig config, boolean local, ChatProvider provider, ContextBuilder builder) {
    }

    private static void start(EditorHost host, String actionId, String heading, Snapshot snapshot,
                              ContextFactory contextFactory, CandidateExtractor extractor, VerifierFactory verifierFactory,
                              AiConversation.Presenter presenter, List<File> extraFiles,
                              Consumer<AiConversation> configure) {
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
        boolean local = config.isLocal(store.localHosts());
        ContextBuilder builder = new ContextBuilder(AiSettings.current().effectiveLevel(local), ContextBudget.DEFAULT);
        AiProposalView view = AiProposalView.open(host, heading);
        AiConversation conversation = new AiConversation(new Setup(host, config, local, provider, builder), actionId,
                view, snapshot, extractor, verifierFactory, presenter, extraFiles);
        if (configure != null) {
            configure.accept(conversation);
        }
        view.setConversation(conversation);
        conversation.start(contextFactory);
    }

    // ---------------------------------------------------------------- helpers

    /** The document a run is about, to refuse applying after it changed. */
    record Snapshot(OpenDocument document, String revision, String text, Path path) {

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

    static Path schemaOf(EditorHost host, Snapshot snapshot) {
        if (snapshot.document == null) {
            return null;
        }
        try {
            File file = host.schemaForValidation(snapshot.text).get();
            return file == null ? null : file.toPath();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static List<Path> projectFolders() {
        return ProjectService.getInstance().current().map(FxtProject::folders).orElse(List.of());
    }

    private static ValidationProblemFacts facts(ValidationProblem p) {
        return new ValidationProblemFacts(p.source(), p.severity(), p.line(), p.column(), p.message(), p.ruleId(),
                p.context());
    }

    private static String statusOf(AiConversation conversation, VerifiedGeneration.Outcome outcome) {
        String status = switch (outcome.status()) {
            case VERIFIED -> I18n.t("AiActions.verified", outcome.repairRounds());
            case FAILED -> I18n.t("AiActions.unverified", outcome.repairRounds());
            case REFUSED -> I18n.t("AiActions.refused");
            case CANCELLED -> I18n.t("AiActions.cancelled");
        };
        return conversation.turn() > 1 ? I18n.t("AiActions.turn", conversation.turn(), status) : status;
    }

    static String outcomeOf(VerifiedGeneration.Status status) {
        return switch (status) {
            case VERIFIED -> "verified";
            case FAILED -> "unverified";
            case REFUSED -> "refused";
            case CANCELLED -> "cancelled";
        };
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
