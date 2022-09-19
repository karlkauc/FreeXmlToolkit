package org.fxt.freexmltoolkit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.brouillard.oss.cssfx.CSSFX;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.fxt.freexmltoolkit.controller.Main2Controller;
import org.fxt.freexmltoolkit.service.ModuleBindings;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FxtGui extends Application {

    final Injector injector = Guice.createInjector(new ModuleBindings());
    BuilderFactory builderFactory = new JavaFXBuilderFactory();
    Callback<Class<?>, Object> guiceControllerFactory = injector::getInstance;

    public static ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    final KeyCombination safeFileKey = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);

    final static String APP_ICON_PATH = "img/xml-file-format-symbol.png";

    Main2Controller main2Controller;

    @Override
    public void start(Stage primaryStage) {
        try {

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/pages/main2.fxml")), null, builderFactory, guiceControllerFactory);
            Parent root = loader.load();
            // MainController mainController = loader.getController();
            main2Controller = loader.getController();

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

            scene.getStylesheets().add("C:\\Data\\src\\FreeXmlToolkit\\src\\main\\resources\\css\\mainTheme.css");
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
        scheduler.shutdown();
        try {
            if (!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        try {
            if (!scheduler.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

    }

    public static void main(String[] args) {
        launch();
    }

}
