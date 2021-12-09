package org.fxt.freexmltoolkit;

import com.pixelduke.control.Ribbon;
import com.pixelduke.control.ribbon.RibbonGroup;
import com.pixelduke.control.ribbon.RibbonTab;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;



public class FxtGui extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Hello World!");

        Ribbon ribbon = new Ribbon();

        RibbonTab ribbonTabHome = new RibbonTab("Home");

        RibbonGroup ribbonGroupFile = new RibbonGroup();
        ribbonGroupFile.setTitle("File");

        Button buttonNew = new Button();
        buttonNew.setText("Open File");
        buttonNew.setContentDisplay(ContentDisplay.TOP);
        var i = new ImageView(new Image(getClass().getResourceAsStream("img/icons8-datei-100.png"), 16, 16, true, true));
        i.maxHeight(20);
        buttonNew.setGraphic(i);

        Button buttonSave = new Button();
        buttonSave.setText("Save File");
        buttonSave.setContentDisplay(ContentDisplay.TOP);
        var i2 = new ImageView(new Image(getClass().getResourceAsStream("img/icons8-save-file-128.png"), 16, 16, true, true));
        i2.maxHeight(20);
        buttonSave.setGraphic(i2);

        ribbonGroupFile.getNodes().add(buttonNew);
        ribbonGroupFile.getNodes().add(buttonSave);

        ribbonTabHome.getRibbonGroups().add(ribbonGroupFile);


        RibbonTab ribbonTabXML = new RibbonTab("XML");
        RibbonGroup ribbonGroupXml = new RibbonGroup();
        ribbonGroupXml.setTitle("basic XML");

        Button xmlPrettyPrint = new Button("pretty Print");
        xmlPrettyPrint.setContentDisplay(ContentDisplay.TOP);
        var i3 = new ImageView(new Image(getClass().getResourceAsStream("img/icons8-save-file-128.png"), 16, 16, true, true));
        i3.maxHeight(20);
        xmlPrettyPrint.setGraphic(i3);
        ribbonGroupXml.getNodes().add(xmlPrettyPrint);

        Button xmlValidateSchema = new Button("validate schema");
        xmlValidateSchema.setContentDisplay(ContentDisplay.TOP);
        var i4 = new ImageView(new Image(getClass().getResourceAsStream("img/icons8-save-file-128.png"), 16, 16, true, true));
        i4.maxHeight(20);
        xmlValidateSchema.setGraphic(i4);
        ribbonGroupXml.getNodes().add(xmlValidateSchema);

        Button xmlXquery = new Button("xquery");
        xmlXquery.setContentDisplay(ContentDisplay.TOP);
        var i5 = new ImageView(new Image(getClass().getResourceAsStream("img/icons8-save-file-128.png"), 16, 16, true, true));
        i5.maxHeight(20);
        xmlXquery.setGraphic(i5);
        ribbonGroupXml.getNodes().add(xmlXquery);

        ribbonTabXML.getRibbonGroups().add(ribbonGroupXml);


        RibbonTab ribbonTabXSD = new RibbonTab("XSD");


        RibbonTab ribbonTabXSLT = new RibbonTab("XSLT");

        RibbonTab ribbonTabFOP = new RibbonTab("FOP");


        ribbon.getTabs().add(ribbonTabHome);
        ribbon.getTabs().add(ribbonTabXML);
        ribbon.getTabs().add(ribbonTabXSD);
        ribbon.getTabs().add(ribbonTabXSLT);
        ribbon.getTabs().add(ribbonTabFOP);




        WebView browser = new WebView();
        WebEngine webEngine = browser.getEngine();

        System.out.println("webEngine = " + webEngine.getUserAgent());
        String url = "file:/C:/Data/src/XMLTEST/output.html";
        webEngine.load(url);

        /*
        File file = new File("C:/test/a.html");
        URL url= file.toURI().toURL();

        // file:/C:/test/a.html
        webEngine.load(url.toString());
         */

        VBox root = new VBox();
        root.getChildren().add(ribbon);
        root.getChildren().add(browser);

        var scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(FxtGui.class.getResource("ribbon_fxt.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

    }

    public static void main(String[] args) {
        System.setProperty("http.proxy","proxy-eb1.s-mxs.net");
        System.setProperty("http.proxyPort","8080");
        launch();
    }



}
