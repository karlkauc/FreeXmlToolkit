package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

/** The JDK's bare proxy sign-in errors become a hint where to fix them; other errors keep their message. */
class NetworkErrorsTest {

    @Test
    void refusedNtlmSignInPointsToTheNetworkSettings() {
        String text = NetworkErrors.unreachable("GLEIF", new IOException("Authentication failure"));
        assertTrue(text.startsWith("GLEIF could not be reached: the proxy refused the sign-in"), text);
        assertTrue(text.contains("Settings → Network & Security"), text);
    }

    @Test
    void wrappedErrorsAreRecognisedToo() {
        var wrapped = new IOException("request failed", new IOException("Authentication failure"));
        assertTrue(NetworkErrors.describe(wrapped).contains("refused the sign-in"));
    }

    @Test
    void tunnel407AsksForCredentials() {
        String text = NetworkErrors.describe(new IOException(
                "Unable to tunnel through proxy. Proxy returns \"HTTP/1.1 407 Proxy Authentication Required\""));
        assertTrue(text.contains("requires a sign-in (HTTP 407)"), text);
    }

    @Test
    void otherErrorsKeepTheirMessage() {
        assertEquals("api.gleif.org", NetworkErrors.describe(new UnknownHostException("api.gleif.org")));
        assertEquals("java.io.IOException", NetworkErrors.describe(new IOException()));
        assertEquals("ESMA FIRDS could not be reached: offline",
                NetworkErrors.unreachable("ESMA FIRDS", new IOException("offline")));
    }
}
