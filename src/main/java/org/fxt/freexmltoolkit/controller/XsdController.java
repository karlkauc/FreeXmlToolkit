package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;

import java.lang.invoke.MethodHandles;

public class XsdController {

    @Inject
    XmlService xmlService;

    static GraphicsContext graphicsContext;

    @FXML
    Canvas canvas;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }


    @FXML
    private void initialize() {
        graphicsContext = canvas.getGraphicsContext2D();

        logger.debug("height: {}", canvas.getGraphicsContext2D().getCanvas().getHeight());
        logger.debug("width: {}", canvas.getGraphicsContext2D().getCanvas().getWidth());

        canvas.setHeight(200);
        canvas.setWidth(200);
        canvas.setStyle("-fx-background-color: red;");
        canvas.getGraphicsContext2D().fillText("text", 0, 0);


    }

}
