package org.fxt.freexmltoolkit.app;

import java.awt.Taskbar;
import java.awt.Toolkit;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sets the macOS Dock icon through AWT.
 *
 * <p>Purely cosmetic, so it must never abort the start: initializing the AWT toolkit can
 * throw an {@link java.awt.AWTError} (for example when an assistive technology registered
 * in {@code .accessibility.properties} cannot be loaded). Windows and Linux take their
 * icon from the JavaFX stage and never touch AWT here.
 */
public final class TaskbarIcon {

    private static final Logger logger = LogManager.getLogger(TaskbarIcon.class);

    private TaskbarIcon() {
    }

    /**
     * Applies the icon on macOS; does nothing elsewhere. Never throws.
     *
     * @param icon the icon resource, may be {@code null}
     */
    public static void apply(URL icon) {
        apply(System.getProperty("os.name", ""), () -> {
            if (icon != null) {
                Taskbar.getTaskbar().setIconImage(Toolkit.getDefaultToolkit().getImage(icon));
            }
        });
    }

    /**
     * @param osName  the {@code os.name} system property
     * @param awtCall the AWT call that sets the icon
     * @return whether the icon was set
     */
    static boolean apply(String osName, Runnable awtCall) {
        if (osName == null || !osName.toLowerCase().contains("mac")) {
            return false;
        }
        try {
            awtCall.run();
            return true;
        } catch (Exception | LinkageError | java.awt.AWTError e) {
            logger.warn("Could not set the taskbar icon: {}", e.toString());
            return false;
        }
    }
}
