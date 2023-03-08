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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Authenticator;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ConnectionServiceImpl implements ConnectionService {

    private final static Logger logger = LogManager.getLogger(ConnectionService.class);

    private static final ConnectionServiceImpl instance = new ConnectionServiceImpl();

    public static ConnectionServiceImpl getInstance() {
        return instance;
    }

    private ConnectionServiceImpl() {
    }


    @Override
    public boolean testConnection() {
        return testConnection("https://www.github.com");
    }

    @Override
    public boolean testConnection(String URL) {
        var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .proxy(ProxySelector.getDefault())
                .authenticator(Authenticator.getDefault())
                .build();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            logger.debug("HTTP Status Code: {}", response.statusCode());

            if (response.statusCode() == 200 && !response.body().isEmpty()) {
                logger.error("OK CODE: {}", response.statusCode());
                logger.error("RESPONSE: {}", response.body());
                return true;
            } else {
                logger.error("FEHLER CODE: {}", response.statusCode());
                logger.error("RESPONSE: {}", response.body());
            }
        } catch (IOException | InterruptedException exception) {
            logger.error("ERROR IN RESPONSE: {}", exception.getMessage());
            logger.error(exception.getStackTrace());
            return false;
        }

        return false;
    }
}
