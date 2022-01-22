package org.fxt.freexmltoolkit.controller;

import com.google.inject.Inject;
import javafx.fxml.FXML;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.MyFileChooserTree;
import org.fxt.freexmltoolkit.service.XmlService;

import java.lang.invoke.MethodHandles;

public class XsdController {

    @Inject
    XmlService xmlService;

    @FXML
    MyFileChooserTree myFileChooserTree;

    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        myFileChooserTree.setNewItem("/Users/karlkauc/IdeaProjects/XMLTEST");
    }
}
