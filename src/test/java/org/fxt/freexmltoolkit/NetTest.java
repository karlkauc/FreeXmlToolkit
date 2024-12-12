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

package org.fxt.freexmltoolkit;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class NetTest {

    private final static Logger logger = LogManager.getLogger(NetTest.class);

    @Test
    // @Disabled("Currently not testable")
    public void testService() throws NoSuchAlgorithmException {
        ConnectionService connectionService = ConnectionServiceImpl.getInstance();
        connectionService.testConnection();
    }

    @Test
    public void t() {
        try {
            System.setProperty("java.net.useSystemProxies", "true");
            List<Proxy> l = ProxySelector.getDefault().select(
                    new URI("http://www.google.com/"));

            for (Proxy proxy : l) {
                System.out.println("proxy type: " + proxy.type());
                InetSocketAddress addr = (InetSocketAddress) proxy.address();

                if (addr == null) {
                    System.out.println("No Proxy");
                } else {
                    System.out.println("proxy hostname: " + addr.getHostName());
                    System.out.println("proxy port: " + addr.getPort());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testNew() {
        var properties = PropertiesServiceImpl.getInstance().loadProperties();
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
        // System.setProperty("javax.net.debug", "all");

        final var httpProxyHost = properties.get("http.proxy.host").toString();
        final var httpProxyPort = properties.get("http.proxy.port").toString();
        final var httpProxyUser = properties.get("http.proxy.user").toString();
        final var httpProxyPassword = properties.get("http.proxy.password").toString();
        final var proxyDomain = "I0013";

        Credentials ntlmCredentials = new NTCredentials(httpProxyUser, httpProxyPassword.toCharArray(), null, proxyDomain);
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(httpProxyHost, Integer.parseInt(httpProxyPort)), ntlmCredentials);

        HttpHost proxy = new HttpHost(httpProxyHost, Integer.parseInt(httpProxyPort));
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setProxy(proxy)
                .setConnectionManager(connManager)
                .build()) {

            HttpGet httpGet = new HttpGet("https://www.github.com");
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                logger.error("Response Code: {}", response.getCode());

                if (response.getCode() == 200) {
                    try (InputStream inputStream = response.getEntity().getContent();
                         FileOutputStream outputStream = new FileOutputStream("index.html")) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    throw new HttpResponseException(response.getCode(), response.getReasonPhrase());
                }
                EntityUtils.consume(response.getEntity());
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
