package org.fxt.freexmltoolkit;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;


public class FxtGui extends Application {

    @Override
    public void start(Stage primaryStage) {


        try {
            Parent root = FXMLLoader.load(getClass().getResource("main.fxml"));
            var scene = new Scene(root, 800, 600);

            primaryStage.setScene(scene);
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
