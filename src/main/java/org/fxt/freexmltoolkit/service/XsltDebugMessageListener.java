package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.MessageListener2;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.transform.SourceLocator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom MessageListener2 implementation for capturing xsl:message output.
 * Collects messages generated during XSLT transformation for debugging purposes.
 *
 * <p>This listener is attached to the Saxon transformer to capture all output
 * from &lt;xsl:message&gt; instructions in the stylesheet.</p>
 *
 * <p>Note: Saxon 12.x uses MessageListener2 interface (deprecated but still functional).</p>
 */
@SuppressWarnings("deprecation")
public class XsltDebugMessageListener implements MessageListener2 {

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
     * This is the MessageListener2 interface method for Saxon 12.x.
     *
     * @param content   An XML document node representing the message content
     * @param errorCode A QName containing the error code supplied to xsl:message (may be null)
     * @param terminate True if terminate="yes" was specified
     * @param locator   Object providing the location of the xsl:message instruction
     */
    @Override
    public void message(XdmNode content, QName errorCode, boolean terminate, SourceLocator locator) {
        try {
            // Extract message content
            String messageText = content != null ? content.getStringValue() : "";

            // Get location info
            String location = extractLocation(locator);
            int lineNumber = locator != null ? locator.getLineNumber() : -1;

            // Determine level
            String level = terminate ? "TERMINATE" : "INFO";

            // Get error code if present
            if (errorCode != null) {
                level = "ERROR [" + errorCode.getLocalName() + "]";
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
