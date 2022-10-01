package org.fxt.freexmltoolkit;

import com.google.inject.Guice;
import com.google.inject.Injector;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.Main2Controller;
import org.fxt.freexmltoolkit.service.ModuleBindings;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FxtGui extends Application {

    private final static Logger logger = LogManager.getLogger(FxtGui.class);


    public static ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    final KeyCombination safeFileKey = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);

    final static String APP_ICON_PATH = "img/logo.png";

    Main2Controller main2Controller;

    @Override
    public void start(Stage primaryStage) {
        try {
            final Injector injector = Guice.createInjector(new ModuleBindings());

            FXMLLoader fxmlLoader = injector.getInstance(FXMLLoader.class);
            InputStream fxmlInputStream = ClassLoader.getSystemResourceAsStream("pages/main2.fxml");
            Parent root = fxmlLoader.load(fxmlInputStream);
            main2Controller = fxmlLoader.getController();

            var scene = new Scene(root, 1024, 768);
            scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
                if (safeFileKey.match(e)) {
                    System.out.println("SAVE PRESSED");
                    // mainController.saveFile();
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

            if (new File("C:\\Data\\src\\FreeXmlToolkit\\src\\main\\resources\\css\\mainTheme.css").exists()) {
                scene.getStylesheets().add("C:\\Data\\src\\FreeXmlToolkit\\src\\main\\resources\\css\\mainTheme.css");
            }
            if (new File("/Users/karlkauc/IdeaProjects/FreeXmlToolkit/src/main/resources/css/mainTheme.css").exists()) {
                logger.debug("File exists.");
                scene.getStylesheets().add("/Users/karlkauc/IdeaProjects/FreeXmlToolkit/src/main/resources/css/mainTheme.css");
            } else {
                logger.debug("File do not exists");
            }

            CSSFX.start();

            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        executorService.shutdown();
        main2Controller.scheduler.shutdown();
        try {
            if (!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        try {
            if (!main2Controller.scheduler.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                main2Controller.scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            main2Controller.scheduler.shutdownNow();
        }

    }

    public static void main(String[] args) {
        launch();
    }

}
