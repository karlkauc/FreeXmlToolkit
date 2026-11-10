package org.fxt.freexmltoolkit.service.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.fxt.freexmltoolkit.service.ai.context.ContextBuilder;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatMessage;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.ChatResponse;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.StopReason;

/**
 * Generate → verify → repair (decision D8): asks the model, takes the artefact out of the answer, checks it with the
 * application's engines, and on failure sends the facts back (as data) for another round, at most {@code maxRepairs}
 * times. The conversation is append-only — earlier turns go back unchanged (Anthropic thinking blocks require it).
 * A refusal ends the run at once.
 */
public final class VerifiedGeneration {

    public enum Status { VERIFIED, FAILED, REFUSED, CANCELLED }

    /**
     * @param candidate    the last candidate (verified or not), or {@code null} when the model gave none
     * @param verification its verification, or {@code null}
     * @param answer       the model's last answer as text (shown next to the proposal)
     */
    public record Outcome(Status status, String candidate, Verification verification, int repairRounds, String answer,
                          List<ChatMessage> transcript) {
    }

    /** Progress for the UI. */
    public interface Listener {
        void round(int round, Phase phase);

        default void text(String delta) {
        }
    }

    public enum Phase { GENERATING, CHECKING }

    public Outcome run(ChatProvider provider, ChatRequest first, CandidateExtractor extractor, Verifier verifier,
                       int maxRepairs, Listener progress, CancelToken cancel) throws ProviderException {
        Listener listener = progress != null ? progress : (r, p) -> { };
        CancelToken token = cancel == null ? CancelToken.none() : cancel;
        List<ChatMessage> transcript = new ArrayList<>(first.messages());
        ChatRequest request = first;
        String lastCandidate = null;
        Verification lastVerification = null;
        String lastAnswer = "";
        for (int round = 0; round <= maxRepairs; round++) {
            token.throwIfCancelled();
            listener.round(round, Phase.GENERATING);
            Consumer<String> deltas = listener::text;
            ChatResponse response = provider.chat(request, deltas, token);
            transcript.add(response.message());
            lastAnswer = response.text();
            if (response.stop() == StopReason.REFUSED) {
                return new Outcome(Status.REFUSED, lastCandidate, lastVerification, round, lastAnswer,
                        List.copyOf(transcript));
            }
            CandidateExtractor.Extraction extraction = extractor.extract(response.text());
            String feedback;
            if (!extraction.ok()) {
                lastVerification = Verification.failed("Answer format", extraction.problem());
                feedback = extraction.problem();
            } else {
                token.throwIfCancelled();
                listener.round(round, Phase.CHECKING);
                lastCandidate = extraction.candidate();
                lastVerification = verifier.verify(lastCandidate, token);
                if (lastVerification.status() != Verification.Status.FAILED) {
                    return new Outcome(lastVerification.verified() ? Status.VERIFIED : Status.FAILED, lastCandidate,
                            lastVerification, round, lastAnswer, List.copyOf(transcript));
                }
                feedback = lastVerification.feedbackForModel();
            }
            if (round == maxRepairs) {
                break;
            }
            ChatMessage repair = ChatMessage.user(ContextBuilder.repairMessage(feedback));
            transcript.add(repair);
            request = request.plus(List.of(response.message(), repair));
        }
        return new Outcome(Status.FAILED, lastCandidate, lastVerification, maxRepairs, lastAnswer,
                List.copyOf(transcript));
    }
}
