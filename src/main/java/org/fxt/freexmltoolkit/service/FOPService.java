/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.PDFSettings;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;

/**
 * Service class for creating PDF files from XML and XSL files using Apache FOP.
 */
public class FOPService {

    private final static Logger logger = LogManager.getLogger(FOPService.class);

    HashMap<String, String> defaultParameter;

    /**
     * Creates a PDF file from the given XML and XSL files.
     *
     * @param xmlFile     the XML file to transform
     * @param xslFile     the XSL file to use for transformation
     * @param pdfOutput   the output PDF file
     * @param pdfSettings the settings for the PDF file
     * @return the created PDF file
     * @throws FOPServiceException if PDF generation fails due to malformed input, transformation errors, or I/O issues
     */
    public File createPdfFile(File xmlFile, File xslFile, File pdfOutput, PDFSettings pdfSettings) throws FOPServiceException {

        if (!xmlFile.exists()) {
            throw new FOPServiceException("XML file does not exist: " + xmlFile.getAbsolutePath());
        }
        if (!xslFile.exists()) {
            throw new FOPServiceException("XSL file does not exist: " + xslFile.getAbsolutePath());
        }

        setDefaultParameter();
        try {
            logger.debug("XML Input: {}", xmlFile);
            logger.debug("Stylesheet: {}", xslFile);
            logger.debug("PDF Output: {}", pdfOutput);
            logger.debug("Transforming...");

            final FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());
            FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
            if (!pdfSettings.producer().isEmpty()) foUserAgent.setProducer(pdfSettings.producer());
            if (!pdfSettings.author().isEmpty()) foUserAgent.setAuthor(pdfSettings.author());
            if (!pdfSettings.creator().isEmpty()) foUserAgent.setCreator(pdfSettings.creator());
            if (!pdfSettings.title().isEmpty()) foUserAgent.setTitle(pdfSettings.title());
            if (!pdfSettings.keywords().isEmpty()) foUserAgent.setKeywords(pdfSettings.keywords());

            Files.createDirectories(pdfOutput.toPath().getParent());

            // Use try-with-resources to ensure OutputStream is always closed
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(pdfOutput))) {
                Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, out);

                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer(new StreamSource(xslFile));

                for (String key : defaultParameter.keySet()) {
                    logger.debug("Set default parameter: '{}' - '{}'", key, defaultParameter.get(key));
                    transformer.setParameter(key, defaultParameter.get(key));
                }

                for (String key : pdfSettings.customParameter().keySet()) {
                    logger.debug("Set individual parameter: '{}' - '{}'", key, pdfSettings.customParameter().get(key));
                    transformer.setParameter(key, pdfSettings.customParameter().get(key));
                }
                Source src = new StreamSource(xmlFile);
                Result res = new SAXResult(fop.getDefaultHandler());
                transformer.transform(src, res);
            } // OutputStream is automatically closed here, even if exception occurs

            logger.debug("PDF generation completed successfully: {}", pdfOutput);
        } catch (javax.xml.transform.TransformerException e) {
            logger.error("XSLT transformation failed", e);
            throw new FOPServiceException("Failed to transform XML using XSL stylesheet: " + e.getMessage(), e);
        } catch (org.apache.fop.apps.FOPException e) {
            logger.error("FOP processing failed", e);
            throw new FOPServiceException("FOP processing error: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            logger.error("I/O error during PDF generation", e);
            throw new FOPServiceException("I/O error while creating PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during PDF generation", e);
            throw new FOPServiceException("Unexpected error during PDF generation: " + e.getMessage(), e);
        }
        return pdfOutput;
    }

    /**
     * Sets the default parameters for the PDF transformation.
     */
    private void setDefaultParameter() {
        defaultParameter = new HashMap<>();
        defaultParameter.put("currentDate", new Date().toString());
        defaultParameter.put("author", System.getProperty("user.name"));
    }

}