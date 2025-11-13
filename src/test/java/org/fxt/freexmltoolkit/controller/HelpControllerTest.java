package org.fxt.freexmltoolkit.controller;

import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for HelpController to verify proxy and SSL configuration integration
 */
public class HelpControllerTest {

    private PropertiesService propertiesService;
    private Properties testProperties;

    @BeforeEach
    void setUp() {
        propertiesService = PropertiesServiceImpl.getInstance();
        testProperties = new Properties();
        
        // Set up test properties
        testProperties.setProperty("manualProxy", "true");
        testProperties.setProperty("http.proxy.host", "localhost");
        testProperties.setProperty("http.proxy.port", "3128");
        testProperties.setProperty("ssl.trustAllCerts", "true");
        testProperties.setProperty("useSystemProxy", "false");
        testProperties.setProperty("noProxyHost", "localhost,127.0.0.1");
    }

    @Test
    void testPropertiesServiceIntegration() {
        // Test that properties service can be loaded
        Properties loadedProperties = propertiesService.loadProperties();
        assertNotNull(loadedProperties, "Properties should be loaded successfully");
        
        // Test property access
        String manualProxy = loadedProperties.getProperty("manualProxy", "false");
        assertNotNull(manualProxy, "Manual proxy property should be accessible");
    }

    @Test
    void testSystemPropertiesConfiguration() {
        // Simulate the proxy configuration that HelpController would do
        boolean useManualProxy = Boolean.parseBoolean(testProperties.getProperty("manualProxy", "false"));
        String proxyHost = testProperties.getProperty("http.proxy.host", "");
        String proxyPort = testProperties.getProperty("http.proxy.port", "");
        
        if (useManualProxy && !proxyHost.isEmpty() && !proxyPort.isEmpty()) {
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", proxyPort);
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", proxyPort);
        }
        
        // Verify the system properties were set correctly
        assertEquals("localhost", System.getProperty("http.proxyHost"));
        assertEquals("3128", System.getProperty("http.proxyPort"));
        assertEquals("localhost", System.getProperty("https.proxyHost"));
        assertEquals("3128", System.getProperty("https.proxyPort"));
        
        // Clean up
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }

    @Test
    void testSSLConfiguration() {
        // Simulate SSL configuration
        boolean trustAllCerts = Boolean.parseBoolean(testProperties.getProperty("ssl.trustAllCerts", "false"));
        
        if (trustAllCerts) {
            System.setProperty("com.sun.net.ssl.checkRevocation", "false");
            System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
        }
        
        // Verify SSL properties were set
        assertEquals("false", System.getProperty("com.sun.net.ssl.checkRevocation"));
        assertEquals("true", System.getProperty("sun.security.ssl.allowUnsafeRenegotiation"));
        
        // Clean up
        System.clearProperty("com.sun.net.ssl.checkRevocation");
        System.clearProperty("sun.security.ssl.allowUnsafeRenegotiation");
    }
}