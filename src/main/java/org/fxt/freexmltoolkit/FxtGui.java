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

import atlantafx.base.theme.PrimerLight;
import fr.brouillard.oss.cssfx.CSSFX;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.lemminx.XMLServerLauncher;
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

    final KeyCombination safeFileKey = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);

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
            scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
                if (safeFileKey.match(e)) {
                    System.out.println("SAVE PRESSED");
                    e.consume();
                }
            });

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

            // ScenicView.show(primaryStage.getScene());
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
        try {
            if (!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        try {
            if (!mainController.scheduler.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                mainController.scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            mainController.scheduler.shutdownNow();
        }

        startWatch.stop();
        var currentDuration = startWatch.getDuration(); // / 1000;

        var prop = propertiesService.loadProperties();
        if (prop == null) {
            propertiesService.createDefaultProperties();
            prop = propertiesService.loadProperties();
        }

        var oldSeconds = Integer.parseInt(prop.getProperty("usageDuration"));
        var newSeconds = oldSeconds + currentDuration.getSeconds();
        prop.setProperty("usageDuration", String.valueOf(newSeconds));
        propertiesService.saveProperties(prop);
        logger.debug("Duration: {}", currentDuration);
        logger.debug("Duration overall: {}", newSeconds);

    }

    public static void main(String[] args) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        // startLemMinXServer();
        launch();
    }

    private static void startLemMinXServer() {
        logger.debug("Starting LemMinX Server");
        System.setProperty("LEMMINX_DEBUG", "true");
        new Thread(() -> {
            try {
                XMLServerLauncher.main(new String[]{});
            } catch (Exception e) {
                logger.debug("Error starting LemMinX Server: {}", e.getMessage());
            }
        }).start();
    }
}
