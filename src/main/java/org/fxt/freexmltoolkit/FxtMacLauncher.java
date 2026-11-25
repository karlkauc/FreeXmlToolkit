package org.fxt.freexmltoolkit;

/**
 * Main class of the macOS package (jpackage {@code --main-class}); Windows, Linux and development
 * runs keep {@link FxtGui}.
 *
 * <p>When the main class extends {@code javafx.application.Application}, the Java launcher
 * ({@code LauncherHelper.FXHelper}) starts the JavaFX toolkit <em>before</em> it calls
 * {@code main}. On macOS the toolkit then enters {@code [NSApp run]} and AppKit delivers the
 * "open document" event of a Finder double-click / "Open With" at once, before any of
 * {@link FxtGui#main}'s setup has run (in particular {@code MacGlassOpenUrls}, which has to
 * take that event over). This class does not extend {@code Application}, so {@link FxtGui#main}
 * runs first and starts the toolkit itself with {@code Application.launch}.
 */
public final class FxtMacLauncher {

    private FxtMacLauncher() {
    }

    /**
     * Starts the application.
     *
     * @param args command-line arguments, passed on unchanged
     */
    public static void main(String[] args) {
        FxtGui.main(args);
    }
}
