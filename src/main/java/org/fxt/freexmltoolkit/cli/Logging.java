package org.fxt.freexmltoolkit.cli;

/**
 * Log setup of the command line: Log4j reads {@code log4j2-cli.xml} (warnings to standard error,
 * nothing on standard output) instead of the application's configuration. Must run before the
 * first logger is created, so this class has no logger of its own.
 */
final class Logging {

    static final String CONFIG = "log4j2-cli.xml";

    private Logging() {
    }

    /** Selects the command-line configuration unless one was given explicitly. */
    static void configure() {
        if (System.getProperty("log4j2.configurationFile") == null
                && System.getProperty("log4j.configurationFile") == null) {
            System.setProperty("log4j2.configurationFile", CONFIG);
        }
        // AWT (fonts for PDF output and diagrams) must not look for a display.
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    /** Lowers the log level to DEBUG for {@code --verbose}. */
    static void verbose() {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.DEBUG);
    }
}
