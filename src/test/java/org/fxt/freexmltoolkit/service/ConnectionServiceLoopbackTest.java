package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.Proxy;
import java.net.URI;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** A local AI provider (Ollama, LM Studio) must be reached directly even when a manual proxy is configured. */
class ConnectionServiceLoopbackTest {

    private static ProxySettings manualProxy() {
        Properties p = new Properties();
        p.setProperty("manualProxy", "true");
        p.setProperty("http.proxy.host", "proxy.corp");
        p.setProperty("http.proxy.port", "3128");
        return ProxySettings.from(p);
    }

    @Test
    void loopbackGoesDirect() {
        ProxySettings settings = manualProxy();
        assertEquals(Proxy.NO_PROXY, ConnectionServiceImpl.proxyFor(settings, URI.create("http://localhost:11434/v1")));
        assertEquals(Proxy.NO_PROXY, ConnectionServiceImpl.proxyFor(settings, URI.create("http://127.0.0.1:1234/v1")));
        assertEquals(Proxy.NO_PROXY, ConnectionServiceImpl.proxyFor(settings, URI.create("http://[::1]:8080/v1")));
        assertNotEquals(Proxy.NO_PROXY, ConnectionServiceImpl.proxyFor(settings, URI.create("https://api.openai.com/v1")));
    }
}
