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

package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConnectionResult")
class ConnectionResultTest {

    @Test
    @DisplayName("Creates record with all values")
    void createsRecordWithAllValues() throws Exception {
        URI url = new URI("https://example.com/api");
        Integer httpStatus = 200;
        Long duration = 150L;
        String[] resultHeader = {"Content-Type: application/json", "Content-Length: 100"};
        String resultBody = "{\"status\": \"ok\"}";

        ConnectionResult result = new ConnectionResult(url, httpStatus, duration, resultHeader, resultBody);

        assertEquals(url, result.url());
        assertEquals(200, result.httpStatus());
        assertEquals(150L, result.duration());
        assertArrayEquals(resultHeader, result.resultHeader());
        assertEquals("{\"status\": \"ok\"}", result.resultBody());
    }

    @Test
    @DisplayName("Handles null values")
    void handlesNullValues() {
        ConnectionResult result = new ConnectionResult(null, null, null, null, null);

        assertNull(result.url());
        assertNull(result.httpStatus());
        assertNull(result.duration());
        assertNull(result.resultHeader());
        assertNull(result.resultBody());
    }

    @Test
    @DisplayName("Equal records are equal")
    void equalRecordsAreEqual() throws Exception {
        URI url = new URI("https://example.com");
        String[] headers = {"Header: value"};

        ConnectionResult result1 = new ConnectionResult(url, 200, 100L, headers, "body");
        ConnectionResult result2 = new ConnectionResult(url, 200, 100L, headers, "body");

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    @DisplayName("Different status codes are not equal")
    void differentStatusNotEqual() throws Exception {
        URI url = new URI("https://example.com");

        ConnectionResult result1 = new ConnectionResult(url, 200, 100L, null, null);
        ConnectionResult result2 = new ConnectionResult(url, 404, 100L, null, null);

        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("Different URLs are not equal")
    void differentUrlsNotEqual() throws Exception {
        URI url1 = new URI("https://example1.com");
        URI url2 = new URI("https://example2.com");

        ConnectionResult result1 = new ConnectionResult(url1, 200, 100L, null, null);
        ConnectionResult result2 = new ConnectionResult(url2, 200, 100L, null, null);

        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("Can represent error response")
    void canRepresentErrorResponse() throws Exception {
        URI url = new URI("https://example.com/notfound");

        ConnectionResult errorResult = new ConnectionResult(url, 404, 50L, null, "Not Found");

        assertEquals(404, errorResult.httpStatus());
        assertEquals("Not Found", errorResult.resultBody());
    }

    @Test
    @DisplayName("Can represent timeout or connection failure")
    void canRepresentConnectionFailure() throws Exception {
        URI url = new URI("https://unreachable.example.com");

        // Represents a connection that was attempted but failed
        ConnectionResult failedResult = new ConnectionResult(url, null, 30000L, null, null);

        assertEquals(url, failedResult.url());
        assertNull(failedResult.httpStatus());
        assertEquals(30000L, failedResult.duration());
    }
}
