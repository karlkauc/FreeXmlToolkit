package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.transform.SourceLocator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Custom Message handler for capturing xsl:message output.
 * Collects messages generated during XSLT transformation for debugging purposes.
 *
 * <p>This handler is attached to the Saxon transformer via
 * {@link net.sf.saxon.s9api.AbstractXsltTransformer#setMessageHandler(Consumer)}
 * to capture all output from &lt;xsl:message&gt; instructions in the stylesheet.</p>
 */
public class XsltDebugMessageListener implements Consumer<Message> {

    private static final Logger logger = LogManager.getLogger(XsltDebugMessageListener.class);

    // Maximum number of messages to collect to prevent memory issues
    private static final int MAX_MESSAGES = 1000;

    // Collected messages
    private final List<XsltTransformationResult.TransformationMessage> messages =
            Collections.synchronizedList(new ArrayList<>());

    // Warnings (messages with terminate="yes" or error-level messages)
    private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());

    // Flag to track if transformation was terminated by xsl:message
    private boolean terminatedByMessage = false;
    private String terminationMessage = null;

    /**
     * Called when an xsl:message instruction is executed.
     *
     * @param message the Message object containing content, error code, terminate flag, and location
     */
    @Override
    public void accept(Message message) {
        try {
            // Extract message content
            String messageText = message.getContent() != null ? message.getContent().getStringValue() : "";

            // Get location info
            SourceLocator locator = message.getLocation();
            String location = extractLocation(locator);
            int lineNumber = locator != null ? locator.getLineNumber() : -1;

            // Determine level
            boolean terminate = message.isTerminate();
            String level = terminate ? "TERMINATE" : "INFO";

            // Get error code if present
            if (message.getErrorCode() != null) {
                level = "ERROR [" + message.getErrorCode().getLocalName() + "]";
            }

            // Create and store message
            if (messages.size() < MAX_MESSAGES) {
                XsltTransformationResult.TransformationMessage msg =
                        new XsltTransformationResult.TransformationMessage(level, messageText, lineNumber, location);
                messages.add(msg);
            }

            // Track warnings (terminate messages)
            if (terminate) {
                terminatedByMessage = true;
                terminationMessage = messageText;
                warnings.add("TERMINATE at " + location + ": " + messageText);
                logger.warn("XSLT transformation terminated by message at {}: {}", location, messageText);
            } else {
                logger.debug("XSLT message at {}: {}", location, messageText);
            }

        } catch (Exception e) {
            logger.debug("Error processing xsl:message: {}", e.getMessage());
        }
    }

    /**
     * Extracts a human-readable location string from a SourceLocator.
     */
    private String extractLocation(SourceLocator locator) {
        if (locator == null) {
            return "unknown location";
        }

        StringBuilder location = new StringBuilder();

        // Add system ID (file path) if available
        String systemId = locator.getSystemId();
        if (systemId != null && !systemId.isEmpty()) {
            // Extract filename from path
            int lastSlash = Math.max(systemId.lastIndexOf('/'), systemId.lastIndexOf('\\'));
            String filename = lastSlash >= 0 ? systemId.substring(lastSlash + 1) : systemId;
            location.append(filename);
        } else {
            location.append("stylesheet");
        }

        // Add line number
        int lineNumber = locator.getLineNumber();
        if (lineNumber > 0) {
            location.append(":").append(lineNumber);
        }

        // Add column number if available
        int columnNumber = locator.getColumnNumber();
        if (columnNumber > 0) {
            location.append(":").append(columnNumber);
        }

        return location.toString();
    }

    // ========== Getter Methods ==========

    /**
     * Returns all collected messages.
     */
    public List<XsltTransformationResult.TransformationMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    /**
     * Returns warnings (terminate messages).
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * Returns true if the transformation was terminated by an xsl:message.
     */
    public boolean wasTerminatedByMessage() {
        return terminatedByMessage;
    }

    /**
     * Returns the termination message, if any.
     */
    public String getTerminationMessage() {
        return terminationMessage;
    }

    /**
     * Returns the number of messages collected.
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * Clears all collected messages.
     */
    public void clear() {
        messages.clear();
        warnings.clear();
        terminatedByMessage = false;
        terminationMessage = null;
    }
}
