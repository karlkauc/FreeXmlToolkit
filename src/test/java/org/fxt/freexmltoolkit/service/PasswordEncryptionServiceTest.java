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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordEncryptionService.
 *
 * Tests the PasswordEncryptionService implementation to ensure proper encryption,
 * decryption, and password property detection.
 */
class PasswordEncryptionServiceTest {

    private final PasswordEncryptionService service = PasswordEncryptionServiceImpl.getInstance();

    @Test
    @DisplayName("Encrypts and decrypts password correctly")
    void testEncryptDecrypt() throws EncryptionException {
        String password = "MySecretPassword123!";
        String encrypted = service.encrypt(password);

        assertTrue(service.isEncrypted(encrypted), "Encrypted value should have ENC( prefix");
        String decrypted = service.decrypt(encrypted);
        assertEquals(password, decrypted, "Decrypted password should match original");
    }

    @Test
    @DisplayName("Creates unique encryptions for same password (different IVs)")
    void testUniqueEncryptions() throws EncryptionException {
        String password = "TestPassword";
        String enc1 = service.encrypt(password);
        String enc2 = service.encrypt(password);

        assertNotEquals(enc1, enc2, "Two encryptions of same password should be different (random IV)");
        assertEquals(password, service.decrypt(enc1), "First encryption should decrypt correctly");
        assertEquals(password, service.decrypt(enc2), "Second encryption should decrypt correctly");
    }

    @Test
    @DisplayName("Handles empty password correctly")
    void testEmptyPassword() throws EncryptionException {
        assertEquals("", service.encrypt(""), "Empty password should return empty string");
        assertEquals("", service.decrypt(""), "Empty value should return empty string");
    }

    @Test
    @DisplayName("Detects password property keys correctly")
    void testIsPasswordProperty() {
        assertTrue(service.isPasswordProperty("http.proxy.password"), "Should detect http proxy password");
        assertTrue(service.isPasswordProperty("https.proxy.password"), "Should detect https proxy password");
        assertTrue(service.isPasswordProperty("user.password"), "Should detect generic password key");
        assertTrue(service.isPasswordProperty("PASSWORD"), "Should be case-insensitive");
        assertTrue(service.isPasswordProperty("my.DATABASE.password"), "Should detect password in middle");

        assertFalse(service.isPasswordProperty("http.proxy.user"), "Should not detect user property");
        assertFalse(service.isPasswordProperty("http.proxy.host"), "Should not detect host property");
        assertFalse(service.isPasswordProperty("username"), "Should not detect username without password");
    }

    @Test
    @DisplayName("Detects encrypted values correctly")
    void testIsEncrypted() {
        assertTrue(service.isEncrypted("ENC(abc123)"), "Should detect ENC( prefix");
        assertTrue(service.isEncrypted("ENC(very:long:encrypted:value)"), "Should detect with multiple colons");

        assertFalse(service.isEncrypted("plain text password"), "Should detect plain text");
        assertFalse(service.isEncrypted(""), "Should handle empty string");
        assertFalse(service.isEncrypted("enc(lowercase)"), "Should be case-sensitive");
    }

    @Test
    @DisplayName("Handles invalid encrypted format gracefully")
    void testDecryptInvalidFormat() {
        assertEquals("", service.decrypt("ENC(invalid)"), "Invalid format should return empty string");
        assertEquals("", service.decrypt("ENC()"), "Empty encrypted value should return empty string");
        assertEquals("", service.decrypt("ENC(invalid:format)"), "Incomplete format should return empty string");
    }

    @Test
    @DisplayName("Handles null gracefully")
    void testNullHandling() throws EncryptionException {
        assertFalse(service.isEncrypted(null), "null should not be detected as encrypted");
        assertFalse(service.isPasswordProperty(null), "null should not be detected as password property");
        assertEquals("", service.decrypt(null), "Decrypting null should return empty string");

        assertNull(service.encrypt(null), "Encrypting null should return null");
    }

    @Test
    @DisplayName("Encrypts and decrypts special characters")
    void testSpecialCharacters() throws EncryptionException {
        String password = "P@ssw0rd!@#$%^&*()_+-=[]{}|;':\",./<>?";
        String encrypted = service.encrypt(password);
        String decrypted = service.decrypt(encrypted);
        assertEquals(password, decrypted, "Special characters should survive encryption/decryption");
    }

    @Test
    @DisplayName("Encrypts and decrypts Unicode characters")
    void testUnicodeCharacters() throws EncryptionException {
        String password = "密碼123パスワード🔒中文";
        String encrypted = service.encrypt(password);
        String decrypted = service.decrypt(encrypted);
        assertEquals(password, decrypted, "Unicode characters should survive encryption/decryption");
    }

    @Test
    @DisplayName("Handles very long passwords")
    void testLongPassword() throws EncryptionException {
        String password = "a".repeat(1000); // 1000 character password
        String encrypted = service.encrypt(password);
        String decrypted = service.decrypt(encrypted);
        assertEquals(password, decrypted, "Long passwords should be encrypted/decrypted correctly");
    }

    @Test
    @DisplayName("Detects non-password properties correctly")
    void testNonPasswordProperties() {
        assertFalse(service.isPasswordProperty("http.proxy.host"), "Host should not be password property");
        assertFalse(service.isPasswordProperty("http.proxy.port"), "Port should not be password property");
        assertFalse(service.isPasswordProperty("http.proxy.user"), "User should not be password property");
        assertFalse(service.isPasswordProperty("customTempFolder"), "Folder should not be password property");
        assertFalse(service.isPasswordProperty("font.size"), "Font size should not be password property");
    }
}
