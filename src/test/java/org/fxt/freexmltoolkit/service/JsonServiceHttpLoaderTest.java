package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.networknt.schema.AbsoluteIri;

/** Remote JSON Schemas and $refs are fetched through the one HTTP service. */
class JsonServiceHttpLoaderTest {

    private final List<URI> fetched = new CopyOnWriteArrayList<>();

    private void registerService(int status) {
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
            public BinaryResponse fetchBinary(URI uri) {
                fetched.add(uri);
                return new BinaryResponse(uri, status, Map.of(), "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    @AfterEach
    void reset() {
        ServiceRegistry.reset();
    }

    @Test
    void httpSchemasComeFromTheService() throws Exception {
        registerService(200);
        var source = JsonService.HTTP_SCHEMA_LOADER.getResource(AbsoluteIri.of("https://schemas.example/a.json"));
        try (InputStream in = source.getInputStream()) {
            assertEquals("{\"type\":\"string\"}", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(List.of(URI.create("https://schemas.example/a.json")), fetched);
    }

    @Test
    void anHttpErrorIsAnIoError() {
        registerService(404);
        var source = JsonService.HTTP_SCHEMA_LOADER.getResource(AbsoluteIri.of("http://schemas.example/missing.json"));
        assertThrows(java.io.IOException.class, source::getInputStream);
    }

    @Test
    void otherSchemesAreLeftToTheLibrary() {
        assertNull(JsonService.HTTP_SCHEMA_LOADER.getResource(AbsoluteIri.of("file:///tmp/a.json")));
        assertNull(JsonService.HTTP_SCHEMA_LOADER.getResource(AbsoluteIri.of("classpath:a.json")));
    }
}
