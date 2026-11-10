package org.fxt.freexmltoolkit.service.ai.context;

import java.util.List;

/**
 * What one request sends: the instructions and the data block (document and schema facts, delimited, see D12).
 *
 * @param dropped the parts left out to stay within the budget, for the preview
 */
public record AiContext(String systemPrompt, String userText, DataLevel level, List<String> dropped) {

    /** Characters of the whole request. */
    public int characters() {
        return systemPrompt.length() + userText.length();
    }

    /** The request as the send preview shows it. */
    public String preview() {
        return "SYSTEM\n" + systemPrompt + "\n\nUSER\n" + userText;
    }
}
