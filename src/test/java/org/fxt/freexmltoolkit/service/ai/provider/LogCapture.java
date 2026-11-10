package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/** Captures every log line (all levels) of the application while it is open; for "nothing secret is logged" tests. */
public final class LogCapture extends AbstractAppender implements AutoCloseable {

    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private final Level previousLevel;

    private LogCapture() {
        super("ai-log-capture-" + System.nanoTime(), null, null, true, Property.EMPTY_ARRAY);
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig root = config.getRootLogger();
        previousLevel = root.getLevel();
        start();
        root.addAppender(this, Level.ALL, null);
        root.setLevel(Level.ALL);
        context.updateLoggers();
    }

    public static LogCapture capture() {
        return new LogCapture();
    }

    @Override
    public void append(LogEvent event) {
        StringBuilder line = new StringBuilder(event.getLevel() + " " + event.getMessage().getFormattedMessage());
        if (event.getThrown() != null) {
            line.append(' ').append(event.getThrown());
        }
        lines.add(line.toString());
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    /** Whether any captured line contains {@code text}. */
    public boolean contains(String text) {
        return lines().stream().anyMatch(l -> l.contains(text));
    }

    @Override
    public void close() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig root = context.getConfiguration().getRootLogger();
        root.removeAppender(getName());
        root.setLevel(previousLevel);
        context.updateLoggers();
        stop();
    }
}
