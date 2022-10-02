package org.fxt.freexmltoolkit.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SignatureController {
    private MainController parentController;

    private final static Logger logger = LogManager.getLogger(SignatureController.class);

    public void setParentController(MainController parentController) {
        this.parentController = parentController;
    }

}
