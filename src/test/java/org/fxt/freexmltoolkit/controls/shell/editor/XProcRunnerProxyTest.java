package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Map;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** XML Calabash's own HTTP client (p:http-request) gets the proxy from the HTTP service. */
class XProcRunnerProxyTest {

    @AfterEach
    void reset() {
        ServiceRegistry.reset();
    }

    @Test
    void calabashIsBuiltWithTheServiceProxies() {
        ServiceRegistry.reset();
        ServiceRegistry.register(ConnectionService.class, new ConnectionService() {
            @Override
            public java.net.Proxy resolveProxy() {
                return null;
            }

            @Override
            public ConnectionResult executeHttpRequest(URI url) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getTextContentFromURL(URI uri) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ConnectionResult testHttpRequest(URI url, java.util.Properties testProperties) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, String> xprocProxies() {
                return Map.of("http", "http://proxy.corp:3128", "https", "http://proxy.corp:3128");
            }
        });

        var calabash = XProcRunner.newCalabash();

        assertEquals("http://proxy.corp:3128", calabash.getConfig().getProxies().get("http"));
        assertEquals("http://proxy.corp:3128", calabash.getConfig().getProxies().get("https"));
    }
}
