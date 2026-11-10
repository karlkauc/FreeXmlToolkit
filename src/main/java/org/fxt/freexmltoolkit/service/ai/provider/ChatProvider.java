package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.List;
import java.util.function.Consumer;

/** A language model behind one of the two supported protocols. */
public interface ChatProvider {

    /**
     * Sends a request and waits for the whole answer, passing text to {@code onTextDelta} as it streams in.
     *
     * @throws ProviderException with a user-safe message; {@link ProviderException.Kind#CANCELLED} after
     *                           {@link CancelToken#cancel()}
     */
    ChatResponse chat(ChatRequest request, Consumer<String> onTextDelta, CancelToken cancel) throws ProviderException;

    /** The models the server offers (empty when it has no model list). */
    List<String> listModels(CancelToken cancel) throws ProviderException;
}
