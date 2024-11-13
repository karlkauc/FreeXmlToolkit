package org.fxt.freexmltoolkit;

import fr.brouillard.oss.cssfx.CSSFX;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.XmlEditor;

import java.io.File;

public class GuiTest extends Application {

    @Override
    public void start(Stage primaryStage) {

        try {
            CSSFX.start();

            TabPane tabPane = new TabPane();
            XmlEditor xmlEditor = new XmlEditor();

            xmlEditor.setXmlFile(new File("src/test/resources/test01.xml"));
            xmlEditor.refresh();
            tabPane.getTabs().add(xmlEditor);
            var scene = new Scene(tabPane, 1024, 768);
            scene.getStylesheets().addAll("/scss/xml-highlighting.css", "/css/app.css");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
