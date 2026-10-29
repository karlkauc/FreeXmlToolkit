package org.fxt.freexmltoolkit.service.telemetry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/**
 * Forwards every logged exception to the anonymous error telemetry: a {@code WARN} or
 * {@code ERROR} log event that carries a throwable becomes
 * {@link Telemetry#reportLogged(Throwable, String)} with {@code where = log.<Logger>} (ERROR) or
 * {@code logwarn.<Logger>} (WARN). The log message itself is never sent — the report consists
 * of the exception classes and code locations only ({@link ErrorSignature}).
 *
 * <p>This is the safety net for the many {@code catch (Exception e) { logger.error(..., e); }}
 * blocks: whatever the application logs as a failure is counted, without touching each site.
 * Events of the telemetry package's own loggers are skipped (no recursion), and so is a
 * throwable that was already reported explicitly. Warnings count only for the application's
 * own loggers; libraries (FOP, POI, HTTP client, …) warn about expected conditions.
 *
 * <p>Installed programmatically ({@link #install()}) as the first thing in {@code main}, so the
 * log4j2 configuration needs no plugin scanning. Reports made before the real service is
 * installed are buffered by {@link Telemetry}.
 */
public final class TelemetryLogAppender extends AbstractAppender {

    static final String NAME = "TelemetryErrors";
    private static final String APP_PACKAGE = "org.fxt.freexmltoolkit";
    private static final String OWN_PACKAGE = "org.fxt.freexmltoolkit.service.telemetry";

    private TelemetryLogAppender() {
        super(NAME, null, null, true, Property.EMPTY_ARRAY);
    }

    /** Adds the appender to the root logger and to every non-additive logger. Idempotent, never throws. */
    public static synchronized void install() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false); // NOPMD - the app's context, not owned here
            Configuration configuration = context.getConfiguration();
            if (configuration.getAppender(NAME) != null) {
                return;
            }
            TelemetryLogAppender appender = new TelemetryLogAppender();
            appender.start();
            configuration.addAppender(appender);
            configuration.getRootLogger().addAppender(appender, Level.WARN, null);
            for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                if (!loggerConfig.isAdditive() && loggerConfig != configuration.getRootLogger()) {
                    loggerConfig.addAppender(appender, Level.WARN, null);
                }
            }
            context.updateLoggers();
        } catch (Throwable t) {
            // the bridge is best effort: logging must keep working without it
            LogManager.getLogger(TelemetryLogAppender.class).debug("Telemetry log bridge not installed: {}", t.toString());
        }
    }

    /** Removes the appender again (tests). Never throws. */
    public static synchronized void uninstall() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false); // NOPMD - the app's context, not owned here
            Configuration configuration = context.getConfiguration();
            if (configuration.getAppender(NAME) == null) {
                return;
            }
            configuration.getRootLogger().removeAppender(NAME);
            for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                loggerConfig.removeAppender(NAME);
            }
            configuration.getAppenders().remove(NAME);
            context.updateLoggers();
        } catch (Throwable ignored) {
            // best effort
        }
    }

    @Override
    public void append(LogEvent event) {
        Throwable thrown = event.getThrown();
        if (thrown == null) {
            return;
        }
        Level level = event.getLevel();
        if (level == null || !level.isMoreSpecificThan(Level.WARN)) {
            return;
        }
        String loggerName = event.getLoggerName();
        if (loggerName != null && loggerName.startsWith(OWN_PACKAGE)) {
            return;
        }
        boolean error = level.isMoreSpecificThan(Level.ERROR);
        if (!error && (loggerName == null || !loggerName.startsWith(APP_PACKAGE))) {
            return; // libraries warn about expected conditions (fonts, network); only their errors count
        }
        String prefix = error ? "log." : "logwarn.";
        Telemetry.reportLogged(thrown, prefix + simpleName(loggerName));
    }

    /** {@code org.fxt.freexmltoolkit.service.XmlServiceImpl} → {@code XmlServiceImpl}. */
    static String simpleName(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return "unknown";
        }
        int dot = loggerName.lastIndexOf('.');
        String name = dot >= 0 ? loggerName.substring(dot + 1) : loggerName;
        return name.isEmpty() ? "unknown" : name;
    }
}
