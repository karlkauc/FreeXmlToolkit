package org.fxt.freexmltoolkit.service.ai.context;

/**
 * A validation problem as the context builder needs it (UI-free; built from the editor's {@code ValidationProblem}).
 *
 * @param line   1-based, 0 when unknown
 * @param column 1-based, 0 when unknown
 */
public record ValidationProblemFacts(String source, String severity, int line, int column, String message,
                                     String rule, String context) {
}
