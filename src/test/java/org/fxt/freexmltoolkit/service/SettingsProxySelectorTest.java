package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

class SettingsProxySelectorTest {

    private static final Proxy SYSTEM_PROXY =
            new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("pac.corp", 8080));

    /** Stands in for the system selector (PAC/WPAD). */
    private static final ProxySelector SYSTEM = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(SYSTEM_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    };

    private static SettingsProxySelector selector(ProxySettings.Mode mode) {
        return new SettingsProxySelector(SYSTEM, new ProxySettings(mode, "proxy.corp", 3128,
                List.of("*.corp"), "", "", "", true, 1000, 1000));
    }

    @Test
    void eachModeDecides() {
        URI github = URI.create("https://github.com/x");
        assertEquals(List.of(Proxy.NO_PROXY), selector(ProxySettings.Mode.NONE).select(github));
        assertEquals(List.of(SYSTEM_PROXY), selector(ProxySettings.Mode.SYSTEM).select(github));
        Proxy manual = selector(ProxySettings.Mode.MANUAL).select(github).getFirst();
        assertEquals("proxy.corp", ((InetSocketAddress) manual.address()).getHostString());
    }

    @Test
    void exceptionsAndLocalAddressesGoDirectInEveryMode() {
        for (ProxySettings.Mode mode : ProxySettings.Mode.values()) {
            assertEquals(List.of(Proxy.NO_PROXY), selector(mode).select(URI.create("http://wiki.corp/a")), mode.name());
            assertEquals(List.of(Proxy.NO_PROXY), selector(mode).select(URI.create("http://localhost:8080/")), mode.name());
        }
    }

    @Test
    void otherSchemesStayWithTheSystemSelector() {
        assertEquals(List.of(SYSTEM_PROXY), selector(ProxySettings.Mode.NONE).select(URI.create("socket://host:1")));
    }
}
