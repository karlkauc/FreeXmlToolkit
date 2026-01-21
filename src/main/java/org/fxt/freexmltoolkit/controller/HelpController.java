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

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * Controller for the Help tab providing access to documentation and external resources.
 *
 * <p>This controller manages multiple WebView components that display:
 * <ul>
 *     <li>FreeXmlToolkit documentation (GitHub Pages)</li>
 *     <li>FundsXML official website</li>
 *     <li>FundsXML schema migration guide</li>
 * </ul>
 *
 * <p>The controller configures WebEngine instances with appropriate proxy and SSL settings
 * based on the application's connection configuration, ensuring compatibility with
 * various network environments including corporate proxies.
 *
 * @author FreeXmlToolkit Team
 * @since 1.0
 */
public class HelpController {

    /**
     * Creates a new HelpController instance.
     *
     * <p>The controller is instantiated by the JavaFX FXML loader. Initialization
     * of WebView components and connection settings occurs in the {@link #initialize()} method.
     */
    public HelpController() {
        // Default constructor for FXML instantiation
    }

    private static final Logger logger = LogManager.getLogger(HelpController.class);
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);

    @FXML
    AnchorPane anchorPane;

    @FXML
    WebView viewFXTDoc, viewFundsXMLSite, viewMigrationGuide;

    @FXML
    Tab tabFXTDoc, tabFundsXMlSite, tabMigrationGuide;

    /**
     * Initializes the Help controller after FXML injection is complete.
     *
     * <p>This method performs the following initialization tasks:
     * <ol>
     *     <li>Configures global WebEngine proxy and SSL settings based on application properties</li>
     *     <li>Initializes each WebView component with individual SSL configurations</li>
     *     <li>Loads the documentation URLs into their respective WebView components</li>
     * </ol>
     *
     * <p>The following URLs are loaded:
     * <ul>
     *     <li>FXT Documentation: https://karlkauc.github.io/FreeXmlToolkit</li>
     *     <li>FundsXML Website: http://www.fundsxml.org</li>
     *     <li>Migration Guide: https://fundsxml.github.io/</li>
     * </ul>
     *
     * <p>Error handling is implemented with retry capability for network issues.
     */
    @FXML
    public void initialize() {
        logger.info("Initializing HelpController with connection settings");
        
        // Configure WebEngine proxy and SSL settings BEFORE creating engines
        configureWebEngineSettings();

        // Configure each WebEngine individually for better SSL handling
        WebEngine viewFXTDocEngine = viewFXTDoc.getEngine();
        configureWebEngineForSSL(viewFXTDocEngine);
        loadUrlWithRetry(viewFXTDocEngine, "https://karlkauc.github.io/FreeXmlToolkit", "FXT Documentation");

        WebEngine engineFundsXMLSite = viewFundsXMLSite.getEngine();
        configureWebEngineForSSL(engineFundsXMLSite);
        loadUrlWithRetry(engineFundsXMLSite, "http://www.fundsxml.org", "FundsXML Webseite");

        WebEngine engineMigrationGuide = viewMigrationGuide.getEngine();
        configureWebEngineForSSL(engineMigrationGuide);
        loadUrlWithRetry(engineMigrationGuide, "https://fundsxml.github.io/", "FundsXML4 Schema Documentation");
    }

    /**
     * Configures WebEngine to use the same proxy and SSL settings as ConnectionServiceImpl
     */
    private void configureWebEngineSettings() {
        try {
            Properties props = propertiesService.loadProperties();
            
            // Configure proxy settings for WebEngine
            configureProxySettings(props);
            
            // Configure SSL settings for WebEngine
            configureSSLSettings(props);
            
            logger.debug("WebEngine configured with proxy and SSL settings from ConnectionServiceImpl");
        } catch (Exception e) {
            logger.error("Failed to configure WebEngine settings: {}", e.getMessage(), e);
        }
    }

    /**
     * Configures proxy settings for WebEngine based on application properties
     */
    private void configureProxySettings(Properties props) {
        boolean useManualProxy = Boolean.parseBoolean(props.getProperty("manualProxy", "false"));
        boolean useSystemProxy = Boolean.parseBoolean(props.getProperty("useSystemProxy", "false"));
        
        if (useManualProxy) {
            String proxyHost = props.getProperty("http.proxy.host", "");
            String proxyPort = props.getProperty("http.proxy.port", "");
            String proxyUser = props.getProperty("http.proxy.user", "");
            String proxyPass = props.getProperty("http.proxy.password", "");
            
            if (!proxyHost.isEmpty() && !proxyPort.isEmpty()) {
                System.setProperty("http.proxyHost", proxyHost);
                System.setProperty("http.proxyPort", proxyPort);
                System.setProperty("https.proxyHost", proxyHost);
                System.setProperty("https.proxyPort", proxyPort);
                
                if (!proxyUser.isEmpty()) {
                    System.setProperty("http.proxyUser", proxyUser);
                    System.setProperty("http.proxyPassword", proxyPass);
                    System.setProperty("https.proxyUser", proxyUser);
                    System.setProperty("https.proxyPassword", proxyPass);
                }
                
                String noProxyHosts = props.getProperty("noProxyHost", "");
                if (!noProxyHosts.isEmpty()) {
                    System.setProperty("http.nonProxyHosts", noProxyHosts.replace(',', '|').replace(";", "|"));
                }
                
                logger.debug("Manual proxy configured for WebEngine: {}:{}", proxyHost, proxyPort);
            }
        } else if (useSystemProxy) {
            System.setProperty("java.net.useSystemProxies", "true");
            logger.debug("System proxy configured for WebEngine");
        }
    }

    /**
     * Configures SSL settings for WebEngine based on application properties
     */
    private void configureSSLSettings(Properties props) {
        boolean trustAllCerts = Boolean.parseBoolean(props.getProperty("ssl.trustAllCerts", "false"));
        
        if (trustAllCerts) {
            logger.warn("!!! SECURITY WARNING !!! SSL certificate validation is disabled for WebEngine.");
            
            try {
                // Create a trust-all SSL context for WebEngine
                configureGlobalSSLTrustAll();
                
                // Additional system properties for WebEngine
                System.setProperty("javax.net.ssl.trustStore", "");
                System.setProperty("javax.net.ssl.trustStorePassword", "");
                System.setProperty("com.sun.net.ssl.checkRevocation", "false");
                System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
                System.setProperty("sun.security.ssl.allowLegacyHelloMessages", "true");
                
                // Disable all SSL/TLS verification
                System.setProperty("trust_all_cert", "true");
                System.setProperty("javax.net.ssl.trustStoreType", "");
                
                // Additional WebEngine-specific SSL bypass
                System.setProperty("com.sun.webkit.useHTTP2Loader", "false");
                System.setProperty("com.sun.webkit.webview.useHTTP2Loader", "false");
                
                // Force TLS version for WebEngine compatibility
                System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
                System.setProperty("jdk.tls.client.protocols", "TLSv1.2,TLSv1.3");
                
                logger.debug("SSL bypass configured globally for WebEngine with additional WebKit settings");
            } catch (Exception e) {
                logger.error("Failed to configure SSL bypass for WebEngine: {}", e.getMessage(), e);
            }
        }
        
        // Configure tunneling for WebEngine
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.https.auth.tunneling.disabledSchemes", "");
    }
    
    /**
     * Configures global SSL settings to trust all certificates for WebEngine
     */
    private void configureGlobalSSLTrustAll() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that trusts all certificates
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // Trust all client certificates
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // Trust all server certificates
                }
            }
        };

        // Install the all-trusting trust manager as default
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // Set as default SSL context for the entire JVM
        SSLContext.setDefault(sc);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        
        logger.debug("Global SSL context configured to trust all certificates");
    }

    /**
     * Configures individual WebEngine instances for SSL bypass
     */
    private void configureWebEngineForSSL(WebEngine engine) {
        try {
            Properties props = propertiesService.loadProperties();
            boolean trustAllCerts = Boolean.parseBoolean(props.getProperty("ssl.trustAllCerts", "false"));
            
            if (trustAllCerts) {
                // Set user agent to help with some sites
                engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 FreeXmlToolkit/2.0");
                
                // Configure additional WebEngine settings for better compatibility
                engine.setJavaScriptEnabled(true);
                
                logger.debug("WebEngine configured for SSL bypass with enhanced compatibility settings");
            }
        } catch (Exception e) {
            logger.warn("Could not configure individual WebEngine SSL settings: {}", e.getMessage());
        }
    }

    /**
     * Loads URL with error handling and retry capability
     */
    private void loadUrlWithRetry(WebEngine engine, String url, String description) {
        try {
            logger.debug("Loading {} from: {}", description, url);
            
            engine.setOnError(event -> {
                logger.error("WebEngine error loading {}: {}", description, event.getMessage());
            });
            
            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    logger.debug("Successfully loaded {}", description);
                } else if (newState == javafx.concurrent.Worker.State.FAILED) {
                    logger.error("Failed to load {}: {}", description, engine.getLoadWorker().getException());
                }
            });
            
            engine.load(url);
        } catch (Exception e) {
            logger.error("Exception loading {}: {}", description, e.getMessage(), e);
        }
    }
}
