package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

public class XmlServiceImpl implements XmlService {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    private String currentXML;

    @Override
    public String getCurrentXml() {
        if (currentXML != null) {
            logger.debug("get Current XML Content {}", currentXML.length());
        }
        else {
            logger.debug("get current XML - NULL");
        }

        return currentXML;
    }

    @Override
    public void setCurrentXml(String currentXml) {
        logger.debug("set XML Content {}", currentXml.length());
        this.currentXML = currentXml;
    }
}
