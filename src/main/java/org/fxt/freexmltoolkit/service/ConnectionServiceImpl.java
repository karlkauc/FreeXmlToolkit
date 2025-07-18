/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ConnectionResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Implementation of the ConnectionService interface.
 * Provides methods for executing HTTP requests and retrieving text content from URLs.
 */
public class ConnectionServiceImpl implements ConnectionService {

    private final static Logger logger = LogManager.getLogger(ConnectionService.class);
    private static final ConnectionServiceImpl instance = new ConnectionServiceImpl();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    private ConnectionServiceImpl() {
    }

    /**
     * Returns the singleton instance of ConnectionServiceImpl.
     *
     * @return the singleton instance
     */
    public static ConnectionServiceImpl getInstance() {
        return instance;
    }

    /**
     * Retrieves the text content from the specified URL.
     *
     * @param uri the URI of the target URL
     * @return a String containing the text content of the URL
     */
    @Override
    public String getTextContentFromURL(URI uri) {
        return executeHttpRequest(uri).resultBody();
    }

    /**
     * Executes an HTTP request to the specified URL.
     *
     * @param url the URI of the target URL
     * @return a ConnectionResult object containing the details of the HTTP response
     */
    @Override
    public ConnectionResult executeHttpRequest(URI url) {
        var props = propertiesService.loadProperties();
        return buildConnectionWithProperties(url, props);
    }

    @Override
    public ConnectionResult testHttpRequest(URI url, Properties testProperties) {
        return buildConnectionWithProperties(url, testProperties);
    }

    /**
     * Builds and executes an HTTP request based on a given set of properties.
     * @param url The target URL.
     * @param props The properties to use for configuration (e.g., proxy settings).
     * @return The result of the connection attempt.
     */
    private ConnectionResult buildConnectionWithProperties(URI url, Properties props) {
        boolean useManualProxy = Boolean.parseBoolean(props.getProperty("manualProxy", "false"));
        String proxyHost = props.getProperty("http.proxy.host", "");
        String proxyPortStr = props.getProperty("http.proxy.port", "");
        String proxyUser = props.getProperty("http.proxy.user", "");
        String proxyPass = props.getProperty("http.proxy.password", "");

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        HttpHost proxy = null;

        // Proxy nur konfigurieren, wenn die manuelle Konfiguration aktiv ist und Host/Port angegeben sind
        if (useManualProxy && !proxyHost.isBlank() && !proxyPortStr.isBlank()) {
            try {
                int proxyPort = Integer.parseInt(proxyPortStr);
                proxy = new HttpHost(proxyHost, proxyPort);

                // Anmeldeinformationen nur setzen, wenn auch ein Benutzername vorhanden ist
                if (!proxyUser.isBlank()) {
                    // Setzt sowohl Standard- als auch NTLM-Credentials für den Proxy
                    Credentials credentials = new UsernamePasswordCredentials(proxyUser, proxyPass.toCharArray());
                    credentialsProvider.setCredentials(new AuthScope(proxy), credentials);

                    Credentials ntlmCredentials = new NTCredentials(proxyUser, proxyPass.toCharArray(), null, null);
                    credentialsProvider.setCredentials(new AuthScope(proxy), ntlmCredentials);
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid proxy port number provided: '{}'", proxyPortStr, e);
                return new ConnectionResult(url, 0, 0L, new String[0], "Invalid proxy port: " + proxyPortStr);
            }
        }

        long start = System.currentTimeMillis();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setProxy(proxy) // Ist null, wenn kein Proxy konfiguriert ist, was korrekt ist
                .setConnectionManager(new BasicHttpClientConnectionManager())
                .build()) {
            HttpGet httpGet = new HttpGet(url);

            return httpClient.execute(httpGet, response -> {
                String[] headers = Arrays.stream(response.getHeaders())
                        .map(header -> header.getName() + ":" + header.getValue())
                        .toArray(String[]::new);

                String text = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

                return new ConnectionResult(
                        url,
                        response.getCode(),
                        System.currentTimeMillis() - start,
                        headers,
                        text);
            });
        } catch (IOException e) {
            logger.error("HTTP request failed: {}", e.getMessage());
            return new ConnectionResult(url, 0, 0L, new String[0], e.getMessage());
        }
    }

}