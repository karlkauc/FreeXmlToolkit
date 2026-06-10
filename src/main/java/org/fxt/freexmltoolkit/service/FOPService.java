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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import java.io.IOException;
import java.net.URI;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.PDFSettings;

/**
 * Service class for creating PDF files from XML and XSL files using Apache FOP.
 */
public class FOPService {

    /**
     * Creates a new FOPService instance.
     */
    public FOPService() {
        // Default constructor
    }

    private static final Logger logger = LogManager.getLogger(FOPService.class);

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

            final FopFactory fopFactory = createSecureFopFactory(new File(".").toURI());
            FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
            if (!pdfSettings.producer().isEmpty()) {
                foUserAgent.setProducer(pdfSettings.producer());
            }
            if (!pdfSettings.author().isEmpty()) {
                foUserAgent.setAuthor(pdfSettings.author());
            }
            if (!pdfSettings.creator().isEmpty()) {
                foUserAgent.setCreator(pdfSettings.creator());
            }
            if (!pdfSettings.title().isEmpty()) {
                foUserAgent.setTitle(pdfSettings.title());
            }
            if (!pdfSettings.keywords().isEmpty()) {
                foUserAgent.setKeywords(pdfSettings.keywords());
            }

            Files.createDirectories(pdfOutput.toPath().getParent());

            // Use try-with-resources to ensure OutputStream is always closed
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(pdfOutput))) {
                Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, out);

                TransformerFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureTransformerFactory();
                Transformer transformer = factory.newTransformer(new StreamSource(xslFile));

                for (Map.Entry<String, String> entry : defaultParameter.entrySet()) {
                    logger.debug("Set default parameter: '{}' - '{}'", entry.getKey(), entry.getValue());
                    transformer.setParameter(entry.getKey(), entry.getValue());
                }

                for (Map.Entry<String, String> entry : pdfSettings.customParameter().entrySet()) {
                    logger.debug("Set individual parameter: '{}' - '{}'", entry.getKey(), entry.getValue());
                    transformer.setParameter(entry.getKey(), entry.getValue());
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
     * Creates a {@link FopFactory} whose resource resolver blocks references to network protocols
     * (http/https/ftp).
     *
     * <p>This prevents a malicious FO document (or embedded SVG) from using
     * {@code <fo:external-graphic src="http://...">} or {@code xlink:href} to perform SSRF / reach
     * internal endpoints, or to exfiltrate data by forcing the renderer to fetch attacker-controlled
     * URLs. Local resources (e.g. logos referenced by relative/file paths) continue to resolve via
     * FOP's default resolver. SVG is rendered by FOP's static Batik bridge, which does not execute
     * ECMAScript, and its external references are subject to the same resolver.
     *
     * @param baseUri the base URI used to resolve relative resource references
     * @return a hardened FopFactory
     */
    private FopFactory createSecureFopFactory(URI baseUri) {
        final ResourceResolver defaultResolver = ResourceResolverFactory.createDefaultResourceResolver();
        ResourceResolver restrictedResolver = new ResourceResolver() {
            @Override
            public Resource getResource(URI uri) throws IOException {
                if (isRemoteUri(uri)) {
                    logger.warn("SECURITY: blocked remote resource reference in FO/SVG: {}", uri);
                    throw new IOException("Blocked remote resource reference: " + uri);
                }
                return defaultResolver.getResource(uri);
            }

            @Override
            public OutputStream getOutputStream(URI uri) throws IOException {
                if (isRemoteUri(uri)) {
                    logger.warn("SECURITY: blocked remote output URI in FO processing: {}", uri);
                    throw new IOException("Blocked remote output reference: " + uri);
                }
                return defaultResolver.getOutputStream(uri);
            }
        };
        return new FopFactoryBuilder(baseUri, restrictedResolver).build();
    }

    /**
     * Determines whether a URI uses a network protocol that must be blocked during FO processing.
     *
     * @param uri the URI to test (may be {@code null})
     * @return true for http/https/ftp
     */
    private static boolean isRemoteUri(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase();
        return scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp");
    }

    /**
     * Sets the default parameters for the PDF transformation.
     */
    private void setDefaultParameter() {
        defaultParameter = new HashMap<>();
        defaultParameter.put("currentDate", new Date().toString());
        defaultParameter.put("author", System.getProperty("user.name"));
    }

    /**
     * Creates a PDF file with automatic metadata from ExportMetadataService.
     *
     * @param xmlFile   the XML file to transform
     * @param xslFile   the XSL file to use for transformation
     * @param pdfOutput the output PDF file
     * @param title     the PDF document title
     * @return the created PDF file
     * @throws FOPServiceException if PDF generation fails
     */
    public File createPdfFileWithAutoMetadata(File xmlFile, File xslFile, File pdfOutput, String title) throws FOPServiceException {
        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);

        String author = metadataService.getUserName();
        if (author == null) {
            author = "";
        }

        String company = metadataService.getUserCompany();
        String keywords = company != null ? company : "";

        // PDFSettings(customParameter, producer, author, creator, creationDate, title, keywords)
        PDFSettings settings = new PDFSettings(
                new HashMap<>(),                             // customParameter
                metadataService.getAppNameWithVersion(),    // producer
                author,                                      // author
                metadataService.getAppName(),               // creator
                metadataService.getTimestamp(),             // creationDate
                title != null ? title : "",                 // title
                keywords                                     // keywords
        );

        return createPdfFile(xmlFile, xslFile, pdfOutput, settings);
    }

    /**
     * Applies metadata from ExportMetadataService to FOUserAgent.
     *
     * @param foUserAgent the FOP user agent
     * @param title       the document title
     */
    public static void applyMetadataToFOUserAgent(FOUserAgent foUserAgent, String title) {
        if (foUserAgent == null) {
            return;
        }

        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
        metadataService.setPdfMetadata(foUserAgent, title);
    }
}
