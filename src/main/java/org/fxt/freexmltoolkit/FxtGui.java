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
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FxtGui extends Application {

    private final static Logger logger = LogManager.getLogger(FxtGui.class);

    public static ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    final static String APP_ICON_PATH = "img/logo.png";

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    MainController mainController;

    StopWatch startWatch = new StopWatch();

    @Override
    public void start(Stage primaryStage) {
        startWatch.start();

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

            primaryStage.show();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void stop() {
        logger.debug("stopping Application");
        executorService.shutdown();
        mainController.scheduler.shutdown();

        shutdownExecutor(executorService);
        shutdownExecutor(mainController.scheduler);
        shutdownExecutor(mainController.service);

        mainController.shutdown();

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
    }

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

    public static void main(String[] args) {
        // Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        launch();
    }
}
