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

package org.fxt.freexmltoolkit.controller;

import java.awt.Desktop;
import java.net.URI;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ConnectionService;

/**
 * Controller for the Help tab providing access to documentation and external resources.
 *
 * <p>Uses Java's HTTP stack (via {@link ConnectionService}) to fetch web content,
 * bypassing WebEngine's built-in networking which cannot handle NTLM proxy authentication
 * in corporate environments. Content is displayed via {@code engine.loadContent()}.
 */
public class HelpController {

    private static final Logger logger = LogManager.getLogger(HelpController.class);

    private static final String URL_FXT_DOC = "https://karlkauc.github.io/FreeXmlToolkit";
    private static final String URL_FUNDSXML = "http://www.fundsxml.org";
    private static final String URL_MIGRATION = "https://fundsxml.github.io/";

    @FXML
    AnchorPane anchorPane;

    @FXML
    WebView viewFXTDoc, viewFundsXMLSite, viewMigrationGuide;

    @FXML
    Tab tabFXTDoc, tabFundsXMlSite, tabMigrationGuide;

    @FXML
    public void initialize() {
        logger.info("Initializing HelpController with Java HTTP bridge");

        loadViaJavaHttp(viewFXTDoc.getEngine(), URL_FXT_DOC, "FXT Documentation");
        loadViaJavaHttp(viewFundsXMLSite.getEngine(), URL_FUNDSXML, "FundsXML Webseite");
        loadViaJavaHttp(viewMigrationGuide.getEngine(), URL_MIGRATION, "FundsXML4 Schema Documentation");

        interceptNavigation(viewFXTDoc.getEngine(), "FXT Documentation");
        interceptNavigation(viewFundsXMLSite.getEngine(), "FundsXML Webseite");
        interceptNavigation(viewMigrationGuide.getEngine(), "FundsXML4 Schema Documentation");
    }

    /**
     * Fetches HTML content via Java's HTTP stack and displays it in the WebEngine.
     * This bypasses WebEngine's native networking, which cannot authenticate with NTLM proxies.
     */
    private void loadViaJavaHttp(WebEngine engine, String url, String description) {
        Platform.runLater(() -> engine.loadContent(
                "<html><body style=\"font-family: sans-serif; padding: 20px;\">"
                        + "<p>Loading " + escapeHtml(description) + "...</p></body></html>"));

        FxtGui.executorService.submit(() -> {
            try {
                ConnectionService connectionService = ServiceRegistry.get(ConnectionService.class);
                String html = connectionService.getTextContentFromURL(URI.create(url));

                // Inject <base> tag so relative URLs (CSS, images, links) resolve correctly
                html = injectBaseTag(html, url);

                final String finalHtml = html;
                Platform.runLater(() -> engine.loadContent(finalHtml));
                logger.debug("Successfully loaded {} via Java HTTP", description);
            } catch (Exception e) {
                logger.error("Failed to load {}: {}", description, e.getMessage(), e);
                Platform.runLater(() -> engine.loadContent(
                        "<html><body style=\"font-family: sans-serif; padding: 20px;\">"
                                + "<h3>Could not load " + escapeHtml(description) + "</h3>"
                                + "<p>" + escapeHtml(e.getMessage()) + "</p>"
                                + "<p><a href=\"" + escapeHtml(url) + "\">Open in browser</a></p>"
                                + "</body></html>"));
            }
        });
    }

    /**
     * Injects a {@code <base href>} tag into the HTML so that relative URLs resolve
     * against the original page URL.
     */
    private String injectBaseTag(String html, String url) {
        String baseTag = "<base href=\"" + escapeHtml(url) + "\">";
        if (html.contains("<head")) {
            return html.replaceFirst("(<head[^>]*>)", "$1" + baseTag);
        } else if (html.contains("<html")) {
            return html.replaceFirst("(<html[^>]*>)", "$1<head>" + baseTag + "</head>");
        }
        return "<head>" + baseTag + "</head>" + html;
    }

    /**
     * Intercepts WebEngine navigation so that clicked links are loaded via Java HTTP
     * instead of WebEngine's native networking.
     */
    private void interceptNavigation(WebEngine engine, String description) {
        engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (newUrl != null && !newUrl.isEmpty()
                    && !newUrl.startsWith("about:")
                    && !newUrl.equals(oldUrl)) {
                Platform.runLater(() -> loadViaJavaHttp(engine, newUrl, description));
            }
        });
    }

    // --- "Open in Browser" button handlers ---

    @FXML
    public void openFxtDocInBrowser() {
        openInBrowser(URL_FXT_DOC);
    }

    @FXML
    public void openFundsXmlInBrowser() {
        openInBrowser(URL_FUNDSXML);
    }

    @FXML
    public void openMigrationGuideInBrowser() {
        openInBrowser(URL_MIGRATION);
    }

    // --- Reload button handlers ---

    @FXML
    public void reloadFxtDoc() {
        loadViaJavaHttp(viewFXTDoc.getEngine(), URL_FXT_DOC, "FXT Documentation");
    }

    @FXML
    public void reloadFundsXml() {
        loadViaJavaHttp(viewFundsXMLSite.getEngine(), URL_FUNDSXML, "FundsXML Webseite");
    }

    @FXML
    public void reloadMigrationGuide() {
        loadViaJavaHttp(viewMigrationGuide.getEngine(), URL_MIGRATION, "FundsXML4 Schema Documentation");
    }

    /**
     * Opens the given URL in the user's default system browser.
     */
    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            logger.error("Failed to open browser for {}: {}", url, e.getMessage());
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
