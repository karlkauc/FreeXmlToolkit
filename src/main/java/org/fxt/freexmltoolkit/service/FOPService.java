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

public class FOPService {

    private final static Logger logger = LogManager.getLogger(FOPService.class);

    HashMap<String, String> defaultParameter;

    public void createPdfFile(File xmlFile, File xslFile, File pdfOutput, PDFSettings pdfSettings) {

        assert xmlFile.exists();
        assert xslFile.exists();

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
            // foUserAgent.setSubject("subject");

            Files.createDirectories(pdfOutput.toPath().getParent());

            OutputStream out = new FileOutputStream(pdfOutput);
            out = new BufferedOutputStream(out);
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
            out.close();
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error(e.getStackTrace());
            logger.error(e);
        }
    }

    private void setDefaultParameter() {
        defaultParameter = new HashMap<>();
        defaultParameter.put("currentDate", new Date().toString());
        defaultParameter.put("author", System.getProperty("user.name"));
    }

}
