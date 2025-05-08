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
        var properties = propertiesService.loadProperties();

        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
        // System.setProperty("javax.net.debug", "all");

        final var httpProxyHost = properties.get("http.proxy.host").toString();
        final var httpProxyPort = properties.get("http.proxy.port").toString();
        final var httpProxyUser = properties.get("http.proxy.user").toString();
        final var httpProxyPassword = properties.get("http.proxy.password").toString();

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        HttpHost proxy = null;

        if (httpProxyHost != null && !httpProxyHost.isEmpty() &&
                httpProxyPort != null && !httpProxyPort.isEmpty()) {
            proxy = new HttpHost(httpProxyHost, Integer.parseInt(httpProxyPort));

            if (httpProxyUser != null && !httpProxyUser.isEmpty() &&
                    httpProxyPassword != null && !httpProxyPassword.isEmpty()) {

                Credentials credentials = new UsernamePasswordCredentials(httpProxyUser, httpProxyPassword.toCharArray());
                credentialsProvider.setCredentials(new AuthScope(null, -1), credentials);

                // Credentials ntlmCredentials = new NTCredentials(httpProxyUser, httpProxyPassword.toCharArray(), null, null);
                // credentialsProvider.setCredentials(new AuthScope(null, -1), ntlmCredentials);
                // credentialsProvider.setCredentials(new AuthScope(httpProxyHost, Integer.parseInt(httpProxyPort)), ntlmCredentials);
            } else {
                credentialsProvider.setCredentials(new AuthScope(null, -1), null);
            }
        }
        long start = System.currentTimeMillis();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setProxy(proxy)
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
            logger.error(e.getMessage());
            return new ConnectionResult(url, 0, 0L, new String[0], e.getMessage());
        }
    }
}