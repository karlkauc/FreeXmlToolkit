package org.fxt.freexmltoolkit.service.ai.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatMessage;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.ChatResponse;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.StopReason;
import org.fxt.freexmltoolkit.service.ai.provider.Usage;

/** A provider that answers with scripted texts in order and records the requests. Test code. */
public final class ScriptedProvider implements ChatProvider {

    private final List<String> answers;
    private final List<ChatRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private int next;
    private StopReason stop = StopReason.END;
    private Runnable beforeAnswer = () -> { };

    public ScriptedProvider(String... answers) {
        this.answers = List.of(answers);
    }

    public ScriptedProvider stopReason(StopReason reason) {
        this.stop = reason;
        return this;
    }

    public ScriptedProvider beforeAnswer(Runnable action) {
        this.beforeAnswer = action;
        return this;
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public synchronized ChatResponse chat(ChatRequest request, Consumer<String> onTextDelta, CancelToken cancel)
            throws ProviderException {
        requests.add(request);
        beforeAnswer.run();
        if (cancel != null) {
            cancel.throwIfCancelled();
        }
        String text = answers.get(Math.min(next++, answers.size() - 1));
        if (onTextDelta != null) {
            onTextDelta.accept(text);
        }
        return new ChatResponse(text, List.of(), stop, Usage.UNKNOWN, "scripted", ChatMessage.assistant(text));
    }

    @Override
    public List<String> listModels(CancelToken cancel) {
        return List.of("scripted");
    }
}
