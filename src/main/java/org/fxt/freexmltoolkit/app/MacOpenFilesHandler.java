package org.fxt.freexmltoolkit.app;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.util.StartupFileOpener;

import com.sun.glass.ui.Application;

/**
 * Receives macOS "open document" events (Finder "Open With", double-click on an associated
 * file, a file dropped on the Dock icon) and routes them into {@link StartupFileOpener}.
 *
 * <p>With JavaFX the native application delegate belongs to Glass, so the AWT
 * {@code Desktop.setOpenFileHandler} never fires. Glass reports the files to its
 * {@link Application.EventHandler}, whose JavaFX default ignores them. This class wraps that
 * handler: every callback is forwarded to it unchanged, open-file events are additionally
 * handed to the editor. Glass re-sends the files the application was launched with when a new
 * handler is set, so the cold start is covered too.
 *
 * <p>The only class referencing {@code com.sun.glass.ui}; it needs
 * {@code --add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED}.
 */
public final class MacOpenFilesHandler {

    private static final Logger logger = LogManager.getLogger(MacOpenFilesHandler.class);

    private MacOpenFilesHandler() {
    }

    /**
     * Installs the handler on macOS; does nothing elsewhere. Must run on the JavaFX
     * application thread after the toolkit has started (e.g. in {@code Application.start}).
     * Never throws: without it, files opened from Finder are just not loaded.
     */
    public static void install() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return;
        }
        try {
            Application app = Application.GetApplication();
            app.setEventHandler(new DelegatingHandler(app.getEventHandler(), StartupFileOpener::enqueue));
            logger.info("macOS open-files handler installed");
        } catch (Exception | LinkageError e) {
            logger.warn("Could not install the macOS open-files handler: {}", e.toString());
        }
    }

    /**
     * Converts Glass file names to paths, skipping unparseable entries. Pure.
     */
    static List<Path> toPaths(String[] files) {
        List<Path> paths = new ArrayList<>();
        if (files == null) {
            return paths;
        }
        for (String file : files) {
            try {
                if (file != null && !file.isBlank()) {
                    paths.add(Path.of(file));
                }
            } catch (InvalidPathException e) {
                logger.debug("Ignoring unparseable open-file path: {}", file);
            }
        }
        return paths;
    }

    /**
     * Forwards every callback to the previous handler and passes opened files to
     * {@code onOpenFiles}.
     */
    static final class DelegatingHandler extends Application.EventHandler {

        private final Application.EventHandler delegate;
        private final Consumer<List<Path>> onOpenFiles;

        DelegatingHandler(Application.EventHandler delegate, Consumer<List<Path>> onOpenFiles) {
            this.delegate = delegate != null ? delegate : new Application.EventHandler();
            this.onOpenFiles = onOpenFiles;
        }

        @Override
        public void handleOpenFilesAction(Application app, long time, String[] files) {
            delegate.handleOpenFilesAction(app, time, files);
            List<Path> paths = toPaths(files);
            if (!paths.isEmpty()) {
                logger.info("macOS asked to open {} file(s)", paths.size());
                onOpenFiles.accept(paths);
            }
        }

        @Override
        public void handleWillFinishLaunchingAction(Application app, long time) {
            delegate.handleWillFinishLaunchingAction(app, time);
        }

        @Override
        public void handleDidFinishLaunchingAction(Application app, long time) {
            delegate.handleDidFinishLaunchingAction(app, time);
        }

        @Override
        public void handleWillBecomeActiveAction(Application app, long time) {
            delegate.handleWillBecomeActiveAction(app, time);
        }

        @Override
        public void handleDidBecomeActiveAction(Application app, long time) {
            delegate.handleDidBecomeActiveAction(app, time);
        }

        @Override
        public void handleWillResignActiveAction(Application app, long time) {
            delegate.handleWillResignActiveAction(app, time);
        }

        @Override
        public void handleDidResignActiveAction(Application app, long time) {
            delegate.handleDidResignActiveAction(app, time);
        }

        @Override
        public void handleDidReceiveMemoryWarning(Application app, long time) {
            delegate.handleDidReceiveMemoryWarning(app, time);
        }

        @Override
        public void handleWillHideAction(Application app, long time) {
            delegate.handleWillHideAction(app, time);
        }

        @Override
        public void handleDidHideAction(Application app, long time) {
            delegate.handleDidHideAction(app, time);
        }

        @Override
        public void handleWillUnhideAction(Application app, long time) {
            delegate.handleWillUnhideAction(app, time);
        }

        @Override
        public void handleDidUnhideAction(Application app, long time) {
            delegate.handleDidUnhideAction(app, time);
        }

        @Override
        public void handleQuitAction(Application app, long time) {
            delegate.handleQuitAction(app, time);
        }

        @Override
        public void handlePreferencesChanged(Map<String, Object> preferences) {
            delegate.handlePreferencesChanged(preferences);
        }
    }
}
