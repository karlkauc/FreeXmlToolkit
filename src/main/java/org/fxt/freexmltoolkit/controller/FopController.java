package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.layout.GridPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Base64;

public class FopController {

    @Inject
    XmlService xmlService;

    @FXML
    GridPane settings;

    @FXML
    WebView pdfView;

    WebEngine engine;
    private MainController parentController;

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @FXML
    private void initialize() {
        logger.debug("BIN IM FOP CONTROLLER");
        engine = pdfView.getEngine();

        String url = getClass().getResource("/web/viewer.html").toExternalForm();
        // connect CSS styles to customize pdf.js appearance
        // engine.setUserStyleSheetLocation(getClass().getResource("/web.css").toExternalForm());

        engine.setJavaScriptEnabled(true);
        engine.load(url);

        engine.getLoadWorker()
                .stateProperty()
                .addListener((observable, oldValue, newValue) -> {
                    // to debug JS code by showing console.log() calls in IDE console
                    // JSObject window = (JSObject) engine.executeScript("window");
                    // window.setMember("java", new JSLogListener());
                    engine.executeScript("window");
                    engine.executeScript("console.log = function(message){ java.log(message); };");

                    // this pdf file will be opened on application startup
                    if (newValue == Worker.State.SUCCEEDED) {
                        try {
                            // readFileToByteArray() comes from commons-io library
                            byte[] data = FileUtils.readFileToByteArray(new File("test.pdf"));
                            String base64 = Base64.getEncoder().encodeToString(data);
                            // call JS function from Java code
                            engine.executeScript("openFileFromBase64('" + base64 + "')");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

        try {
            byte[] data = FileUtils.readFileToByteArray(new File("test.pdf"));
            String base64 = Base64.getEncoder().encodeToString(data);
            engine.executeScript("openFileFromBase64('" + base64 + "')");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // this file will be opened on button click
        /*
        btn.setOnAction(actionEvent -> {
            try {
                byte[] data = FileUtils.readFileToByteArray(new File("/path/to/another/file"));
                String base64 = Base64.getEncoder().encodeToString(data);
                engine.executeScript("openFileFromBase64('" + base64 + "')");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

         */
    }

}

