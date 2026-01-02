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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Authenticator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProxyAuthenticator.
 *
 * Tests the ProxyAuthenticator class to ensure proper credential handling
 * and integration with Java's authentication framework.
 */
class ProxyAuthenticatorTest {

    @Test
    @DisplayName("Creates authenticator with valid credentials")
    void testCreatesAuthenticatorWithCredentials() {
        ProxyAuthenticator authenticator = new ProxyAuthenticator("testuser", "testpass");
        assertNotNull(authenticator, "Authenticator should not be null");
    }

    @Test
    @DisplayName("Creates authenticator with null password")
    void testCreatesAuthenticatorWithNullPassword() {
        ProxyAuthenticator authenticator = new ProxyAuthenticator("testuser", null);
        assertNotNull(authenticator, "Authenticator should not be null even with null password");
    }

    @Test
    @DisplayName("Creates authenticator with empty strings")
    void testCreatesAuthenticatorWithEmptyStrings() {
        ProxyAuthenticator authenticator = new ProxyAuthenticator("", "");
        assertNotNull(authenticator, "Authenticator should not be null with empty strings");
    }

    @Test
    @DisplayName("Can be installed as default authenticator")
    void testCanBeInstalledAsDefault() {
        ProxyAuthenticator authenticator = new ProxyAuthenticator("testuser", "testpass");
        Authenticator.setDefault(authenticator);

        Authenticator installed = Authenticator.getDefault();
        assertNotNull(installed, "Default authenticator should be set");
        assertTrue(installed instanceof ProxyAuthenticator, "Should be ProxyAuthenticator instance");

        // Cleanup
        authenticator.clear();
        Authenticator.setDefault(null);
    }

    @Test
    @DisplayName("Clear method executes without exception")
    void testClearDoesNotThrowException() {
        ProxyAuthenticator authenticator = new ProxyAuthenticator("testuser", "testpass");
        assertDoesNotThrow(() -> authenticator.clear(), "Clear should not throw exception");
    }

    @Test
    @DisplayName("Clear method can be called multiple times safely")
    void testClearCanBeCalledMultipleTimes() {
        ProxyAuthenticator authenticator = new ProxyAuthenticator("testuser", "testpass");
        assertDoesNotThrow(() -> {
            authenticator.clear();
            authenticator.clear();
            authenticator.clear();
        }, "Multiple clear calls should not throw exception");
    }

    @Test
    @DisplayName("Multiple authenticators can be created")
    void testMultipleAuthenticatorsCanBeCreated() {
        ProxyAuthenticator auth1 = new ProxyAuthenticator("user1", "pass1");
        ProxyAuthenticator auth2 = new ProxyAuthenticator("user2", "pass2");

        assertNotNull(auth1, "First authenticator should not be null");
        assertNotNull(auth2, "Second authenticator should not be null");
        assertNotSame(auth1, auth2, "Should be different instances");

        // Cleanup
        auth1.clear();
        auth2.clear();
    }
}
