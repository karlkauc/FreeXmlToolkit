/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit;

import fr.brouillard.oss.cssfx.CSSFX;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ThreadPoolManager;
import org.fxt.freexmltoolkit.service.UsageTrackingService;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main application class for FreeXMLToolkit - Universal Toolkit for XML.
 *
 * <p>This JavaFX application provides a comprehensive suite of XML tools including:
 * <ul>
 *   <li>XML editing with syntax highlighting and validation</li>
 *   <li>XSD schema visualization and documentation generation</li>
 *   <li>XSLT transformations with preview capabilities</li>
 *   <li>XML digital signature creation and validation</li>
 *   <li>PDF generation from XML/XSL-FO documents</li>
 * </ul>
 *
 * <p>The application features a modern tab-based interface built with JavaFX and AtlantaFX theming.
 * It includes advanced XML editing capabilities with IntelliSense, schema-based auto-completion,
 * and real-time validation feedback.
 *
 *
 * @author Karl Kauc
 * @version 1.0
 * @since 2024
 */
public class FxtGui extends Application {

    static {
        configureLogging();
    }

    private final static Logger logger = LogManager.getLogger(FxtGui.class);

    private static final java.util.concurrent.atomic.AtomicInteger threadCounter = new java.util.concurrent.atomic.AtomicInteger(1);

    public static ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            runnable -> {
                Thread t = new Thread(runnable);
                t.setDaemon(true);
                t.setName("FxtGui-Worker-" + threadCounter.getAndIncrement());
                return t;
            }
    );

    final static String APP_ICON_PATH = "img/logo.png";

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    MainController mainController;

    StopWatch startWatch = new StopWatch();

    /**
     * Initializes the service registry for dependency injection.
     *
     * <p>This method is called by JavaFX before {@link #start(Stage)}
     * and sets up the application-wide service registry.
     *
     * @throws Exception if initialization fails
     */
    @Override
    public void init() throws Exception {
        super.init();
        logger.info("Initializing service registry...");
        ServiceRegistry.initialize();
        logger.info("Service registry initialization complete");
    }

    /**
     * Configures Log4j2 to use external log4j2.xml file if available in app directory.
     * 
     * <p>This method checks for a log4j2.xml file in the application directory and
     * reconfigures the LoggerContext to use it. This allows packaged applications
     * to use a custom logging configuration outside the JAR file.
     * 
     * <p>Search order:
     * <ol>
     *   <li>log4j2.xml in current working directory</li>
     *   <li>log4j2.xml in application jar directory (for jpackage apps)</li>
     *   <li>Falls back to embedded log4j2.xml in resources</li>
     * </ol>
     */
    private static void configureLogging() {
        try {
            // Try current working directory first
            File externalConfigFile = new File("log4j2.xml");
            
            // If not found, try the directory where the application jar is located
            if (!externalConfigFile.exists()) {
                // Get the directory of the current jar/application
                String jarPath = FxtGui.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                File jarDir = new File(jarPath).getParentFile();
                externalConfigFile = new File(jarDir, "log4j2.xml");
            }
            
            if (externalConfigFile.exists()) {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                context.setConfigLocation(externalConfigFile.toURI());
                context.reconfigure();
                System.out.println("Using external log4j2.xml configuration: " + externalConfigFile.getAbsolutePath());
            } else {
                System.out.println("External log4j2.xml not found, using embedded configuration");
            }
        } catch (Exception e) {
            System.err.println("Failed to configure external logging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initializes and starts the JavaFX application.
     *
     * <p>This method performs the following initialization steps:
     * <ul>
     *   <li>Loads custom fonts (Roboto family)</li>
     *   <li>Loads the main FXML layout and initializes controllers</li>
     *   <li>Sets up platform-specific taskbar icons (macOS and Windows)</li>
     *   <li>Configures CSS hot-reloading for development</li>
     *   <li>Sets up the primary stage with maximized window</li>
     *   <li>Configures application shutdown behavior</li>
     * </ul>
     *
     * @param primaryStage the primary stage for this application, onto which
     *                     the application scene can be set
     * @throws IOException if the FXML file cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) {
        startWatch.start();
        loadFonts();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
            Parent root = loader.load();
            mainController = loader.getController();

            var scene = new Scene(root, 1024, 768);

            // MAC Taskbar Image
            try {
                final Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
                final Taskbar taskbar = Taskbar.getTaskbar();
                taskbar.setIconImage(defaultToolkit.getImage(FxtGui.class.getClassLoader().getResource(APP_ICON_PATH)));
            } catch (UnsupportedOperationException ignore) {
            }

            // Icon in Taskbar WINDOWS
            try {
                primaryStage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(FxtGui.class.getResourceAsStream("/" + APP_ICON_PATH))));
            } catch (Exception ignore) {
            }

            CSSFX.start();

            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);

            primaryStage.setOnCloseRequest(t -> {
                Platform.exit();
            });

            primaryStage.show();

            // Start usage tracking session
            try {
                UsageTrackingService trackingService = ServiceRegistry.get(UsageTrackingService.class);
                if (trackingService != null) {
                    trackingService.startSession();
                }
            } catch (Exception e) {
                logger.debug("Usage tracking not available: {}", e.getMessage());
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Loads custom Roboto font family from embedded resources.
     *
     * <p>Attempts to load all variants of the Roboto font including:
     * Regular, Bold, Italic, Light, Thin, Medium, Black, and their italic variants.
     * Font loading failures are logged as warnings but do not prevent application startup.
     *
     * <p>Fonts are loaded at 10pt size and become available system-wide within the application.
     */
    private void loadFonts() {
        String[] fonts = {
                "Roboto-Regular", "Roboto-Bold", "Roboto-Italic", "Roboto-Light",
                "Roboto-Thin", "Roboto-Medium", "Roboto-Black", "Roboto-BoldItalic",
                "Roboto-LightItalic", "Roboto-MediumItalic", "Roboto-ThinItalic", "Roboto-BlackItalic"
        };
        for (String font : fonts) {
            try {
                Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
            } catch (Exception e) {
                logger.warn("Could not load font: {}.ttf", font, e);
            }
        }
    }


    /**
     * Performs application shutdown and cleanup operations.
     *
     * <p>This method is called when the JavaFX application is being stopped.
     * It performs the following cleanup operations:
     * <ul>
     *   <li>Shuts down the global executor service</li>
     *   <li>Shuts down scheduler and service executors from MainController</li>
     *   <li>Calls shutdown on the MainController for controller-specific cleanup</li>
     *   <li>Records application runtime duration</li>
     *   <li>Saves runtime statistics to application properties</li>
     * </ul>
     *
     * <p>Executor services are given time to complete current tasks before forced shutdown.
     *
     * @see #shutdownExecutor(ExecutorService)
     */
    @Override
    public void stop() {
        logger.debug("stopping Application");
        executorService.shutdown();
        mainController.scheduler.shutdown();

        shutdownExecutor(executorService);
        shutdownExecutor(mainController.scheduler);
        shutdownExecutor(mainController.service);

        // Shutdown centralized thread pool manager
        try {
            ThreadPoolManager.getInstance().shutdown();
            logger.debug("ThreadPoolManager shut down successfully");
        } catch (Exception e) {
            logger.warn("Error shutting down ThreadPoolManager", e);
        }

        mainController.shutdown();

        // End usage tracking session
        try {
            UsageTrackingService trackingService = ServiceRegistry.get(UsageTrackingService.class);
            if (trackingService != null) {
                trackingService.endSession();
            }
        } catch (Exception e) {
            logger.debug("Usage tracking end session failed: {}", e.getMessage());
        }

        startWatch.stop();
        var currentDuration = startWatch.getDuration(); // / 1000;

        var prop = propertiesService.loadProperties();
        if (prop == null) {
            propertiesService.createDefaultProperties();
            prop = propertiesService.loadProperties();
        }

        // 1. Lesen Sie den Wert sicher aus. Wenn "usageDuration" nicht existiert,
        //    wird der Standardwert "0" verwendet. Das verhindert 'null'.
        String usageDurationStr = prop.getProperty("usageDuration", "0");

        // 2. Jetzt ist die Umwandlung in eine Zahl sicher.
        var oldSeconds = Integer.parseInt(usageDurationStr);
        var newSeconds = oldSeconds + currentDuration.getSeconds();

        prop.setProperty("usageDuration", String.valueOf(newSeconds));
        propertiesService.saveProperties(prop);
        logger.debug("Duration: {}", currentDuration);
        logger.debug("Duration overall: {}", newSeconds);

        // Force exit to ensure JVM terminates even if non-daemon threads remain
        // (e.g., from JavaFX WebView/WebKit or other third-party libraries)
        logger.info("Forcing JVM exit after successful cleanup");
        System.exit(0);
    }

    /**
     * Safely shuts down an ExecutorService with graceful timeout handling.
     *
     * <p>This method implements a two-phase shutdown process:
     * <ol>
     *   <li>Initiates an orderly shutdown allowing currently executing tasks to complete</li>
     *   <li>Waits up to 800 milliseconds for tasks to finish</li>
     *   <li>Forces immediate shutdown if tasks don't complete within the timeout</li>
     * </ol>
     *
     * <p>If the shutdown is interrupted, the method will immediately force shutdown
     * and restore the interrupted status of the current thread.
     *
     * @param executor the ExecutorService to shutdown, must not be null
     */
    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.info("Forcing executor shutdown");
            executor.shutdownNow();
        }
    }

    static void main(String[] args) {
        launch();
    }
}
