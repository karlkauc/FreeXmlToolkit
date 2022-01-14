package org.fxt.freexmltoolkit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.BuilderFactory;
import javafx.util.Callback;
import org.fxt.freexmltoolkit.service.ModuleBindings;

import java.io.IOException;
import java.util.Objects;


public class FxtGui extends Application {

    final Injector injector = Guice.createInjector(new ModuleBindings());
    BuilderFactory builderFactory = new JavaFXBuilderFactory();
    Callback<Class<?>, Object> guiceControllerFactory = injector::getInstance;

    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/pages/main.fxml")), null, builderFactory, guiceControllerFactory);
            var scene = new Scene(root, 1024, 768);

            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.setProperty("http.proxy","proxy-eb1.s-mxs.net");
        System.setProperty("http.proxyPort","8080");
        launch();
    }

}
