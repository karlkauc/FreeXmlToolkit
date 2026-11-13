package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javafx.application.Platform;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.agent.CandidateExtractor;
import org.fxt.freexmltoolkit.service.ai.agent.InAppToolContext;
import org.fxt.freexmltoolkit.service.ai.agent.VerifiedGeneration;
import org.fxt.freexmltoolkit.service.ai.agent.Verifier;
import org.fxt.freexmltoolkit.service.ai.context.AiContext;
import org.fxt.freexmltoolkit.service.ai.context.ContextBuilder;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatMessage;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.ChatResponse;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.StopReason;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * One AI action and its follow-ups in the AI panel: the first request (built within the data level, consent, generate →
 * verify → repair) and then any number of follow-ups that continue the same conversation with the same extractor and
 * verifier. The transcript is replayed unchanged (append-only, see {@link VerifiedGeneration}). When the user took the
 * result over (inserted, applied, opened) and edited it since, a follow-up carries the current version, so the user
 * can refine their own edits too. FX thread, except the generation itself.
 */
final class AiConversation {

    /** Shows a finished turn in the view; may call {@link #placed} when the result is taken over. */
    @FunctionalInterface
    interface Presenter {
        void present(AiConversation conversation, VerifiedGeneration.Outcome outcome);
    }

    private final AiActions.Setup setup;
    private final String actionId;
    private final AiProposalView view;
    private final AiActions.Snapshot snapshot;
    private final CandidateExtractor extractor;
    private final AiActions.VerifierFactory verifierFactory;
    private final Presenter presenter;
    private final List<File> extraFiles;

    /** Why a follow-up is not possible now ({@code null} = possible); asked when the user sends one. */
    private Supplier<String> followUpBlocker = () -> null;

    private String system;
    private Path schema;
    private List<ChatMessage> transcript = List.of();
    private int turn;
    private boolean running;
    private CancelToken cancel = new CancelToken();

    /** The text of the result the user took over, and where it is now (console input, editor tab). */
    private String placedText;
    private Supplier<String> placedNow;
    /** The placed result's text when the running (or last) turn started — what an apply may replace. */
    private String artifactAtTurnStart;

    AiConversation(AiActions.Setup setup, String actionId, AiProposalView view, AiActions.Snapshot snapshot,
                   CandidateExtractor extractor, AiActions.VerifierFactory verifierFactory, Presenter presenter,
                   List<File> extraFiles) {
        this.setup = setup;
        this.actionId = actionId;
        this.view = view;
        this.snapshot = snapshot;
        this.extractor = extractor;
        this.verifierFactory = verifierFactory;
        this.presenter = presenter;
        this.extraFiles = extraFiles == null ? List.of() : extraFiles;
    }

    AiProposalView view() {
        return view;
    }

    AiActions.Snapshot snapshot() {
        return snapshot;
    }

    /** The number of answered requests so far (1 after the first). */
    int turn() {
        return turn;
    }

    boolean isRunning() {
        return running;
    }

    /** Whether the panel offers a follow-up: a first answer exists and nothing is running. */
    boolean canFollowUp() {
        return !running && !transcript.isEmpty();
    }

    void setFollowUpBlocker(Supplier<String> blocker) {
        this.followUpBlocker = blocker == null ? () -> null : blocker;
    }

    /**
     * The user took a result over: {@code text} is what was placed, {@code now} reads it where it lives, so a later
     * follow-up can tell whether the user changed it.
     */
    void placed(String text, Supplier<String> now) {
        this.placedText = text;
        this.placedNow = now;
    }

    /** What the user made of the placed result, when it differs from what was placed; else {@code null}. */
    String editedArtifact() {
        if (placedText == null || placedNow == null) {
            return null;
        }
        String now = placedNow.get();
        if (now == null || now.isBlank() || now.strip().equals(placedText.strip())) {
            return null;
        }
        return now;
    }

    /**
     * The placed result's text when this turn's request was made, or {@code null}; an apply that would replace a
     * version the user changed since is refused against it.
     */
    String artifactAtTurnStart() {
        return artifactAtTurnStart;
    }

    // ---------------------------------------------------------------- the first request

    /** Builds the first request off the FX thread, asks for consent and runs it. */
    void start(AiActions.ContextFactory contextFactory) {
        long started = System.nanoTime();
        startTurn();
        view.running(I18n.t("AiActions.preparing"), this::cancel);
        CancelToken token = cancel;
        FxtGui.executorService.submit(() -> {
            Path schemaFile = AiActions.schemaOf(setup.host(), snapshot);
            AiContext context;
            try {
                context = contextFactory.build(setup.builder(), schemaFile);
            } catch (RuntimeException e) {
                Platform.runLater(() -> endWithoutAnswer(I18n.t("AiActions.failed", e.getClass().getSimpleName())));
                return;
            }
            Platform.runLater(() -> {
                if (token.isCancelled() || !current()) {
                    running = false;
                    return;
                }
                this.schema = schemaFile;
                this.system = context.systemPrompt();
                send(context, List.of(ChatMessage.user(context.userText())), started);
            });
        });
    }

    // ---------------------------------------------------------------- follow-ups

    /**
     * A follow-up in the user's words: continues the conversation; the result is verified like the first one.
     *
     * @return whether the follow-up was sent (refused when nothing to continue, blocked or not confirmed)
     */
    boolean followUp(String text) {
        if (text == null || text.isBlank() || !canFollowUp()) {
            return false;
        }
        String blocked = followUpBlocker.get();
        if (blocked != null) {
            view.notice(blocked);
            return false;
        }
        long started = System.nanoTime();
        String message = ContextBuilder.followUpMessage(text, editedArtifact());
        AiContext context = new AiContext(system, message, setup.builder().level(), List.of());
        List<ChatMessage> messages = new ArrayList<>(transcript);
        messages.add(ChatMessage.user(message));
        startTurn();
        view.followUpStarted(text);
        send(context, messages, started);
        return true;
    }

    // ---------------------------------------------------------------- the run

    private void startTurn() {
        running = true;
        cancel = new CancelToken();
        artifactAtTurnStart = placedNow == null ? null : placedNow.get();
    }

    private void cancel() {
        cancel.cancel();
    }

    /** Consent (FX), then the generation on a worker. */
    private void send(AiContext context, List<ChatMessage> messages, long started) {
        if (!AiConsent.confirm(setup.config(), setup.local(), context)) {
            UsageEvents.aiAction(actionId, kind(), "cancelled", 0, turn + 1, started);
            endWithoutAnswer(I18n.t("AiActions.notSent"));
            return;
        }
        view.running(I18n.t("AiActions.asking", setup.config().name()), this::cancel);
        ChatRequest request = new ChatRequest(system, messages, List.of(), 0, null);
        CancelToken token = cancel;
        int thisTurn = turn + 1;
        FxtGui.executorService.submit(() -> generate(request, token, thisTurn, started));
    }

    private void generate(ChatRequest request, CancelToken token, int thisTurn, long started) {
        ChatProvider provider = setup.provider();
        try {
            VerifiedGeneration.Outcome outcome;
            if (extractor == null) {
                ChatResponse response = provider.chat(request, delta -> Platform.runLater(() -> {
                    if (current()) {
                        view.streaming(delta);
                    }
                }), token);
                List<ChatMessage> all = new ArrayList<>(request.messages());
                all.add(response.message());
                outcome = new VerifiedGeneration.Outcome(response.stop() == StopReason.REFUSED
                        ? VerifiedGeneration.Status.REFUSED : VerifiedGeneration.Status.VERIFIED, null, null, 0,
                        response.text(), List.copyOf(all));
            } else {
                List<File> files = new ArrayList<>();
                if (snapshot.path() != null) {
                    files.add(snapshot.path().toFile());
                }
                if (schema != null) {
                    files.add(schema.toFile());
                }
                files.addAll(extraFiles.stream().filter(f -> f != null).toList());
                ToolContext toolContext = InAppToolContext.forFiles(files, AiActions.projectFolders(),
                        AiSettings.current().toolLimits());
                Verifier verifier = verifierFactory.create(toolContext, schema);
                outcome = new VerifiedGeneration().run(provider, request, extractor, verifier, AiActions.MAX_REPAIRS,
                        (round, phase) -> {
                            String key = phase == VerifiedGeneration.Phase.GENERATING ? "AiActions.round.generating"
                                    : "AiActions.round.checking";
                            Platform.runLater(() -> {
                                if (current()) {
                                    view.running(I18n.t(key, round + 1), this::cancel);
                                }
                            });
                        }, token);
            }
            VerifiedGeneration.Outcome result = outcome;
            UsageEvents.aiAction(actionId, kind(), AiActions.outcomeOf(result.status()), result.repairRounds(),
                    thisTurn, started);
            Platform.runLater(() -> finish(result));
        } catch (ProviderException e) {
            boolean cancelled = e.kind() == ProviderException.Kind.CANCELLED;
            UsageEvents.aiAction(actionId, kind(), cancelled ? "cancelled" : "failed", 0, thisTurn, started);
            Platform.runLater(() -> endWithoutAnswer(cancelled ? I18n.t("AiActions.cancelled") : e.getMessage()));
        } catch (RuntimeException e) {
            UsageEvents.aiAction(actionId, kind(), "failed", 0, thisTurn, started);
            Platform.runLater(() -> endWithoutAnswer(I18n.t("AiActions.failed", e.getClass().getSimpleName())));
        }
    }

    /** A turn ended with an answer: the transcript grows, unless the provider declined. */
    private void finish(VerifiedGeneration.Outcome outcome) {
        running = false;
        if (!current()) {
            return;
        }
        if (outcome.status() == VerifiedGeneration.Status.REFUSED) {
            view.failed(I18n.t("AiActions.refused"));
            return;
        }
        transcript = outcome.transcript();
        turn++;
        presenter.present(this, outcome);
    }

    /** A turn ended without an answer: the conversation stays as it was (a follow-up continues the last answer). */
    private void endWithoutAnswer(String message) {
        running = false;
        if (current()) {
            view.failed(message);
        }
    }

    /** Whether the panel still shows this conversation (a newer action or Discard replaced it). */
    private boolean current() {
        return view.conversation() == this;
    }

    private String kind() {
        return UsageEvents.aiProviderKind(setup.config(), setup.local());
    }

    // ---------------------------------------------------------------- for tests

    /** The messages the next follow-up continues. */
    List<ChatMessage> transcript() {
        return transcript;
    }
}
