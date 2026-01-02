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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PropertiesService with password encryption.
 *
 * Tests that passwords are properly encrypted when saved and decrypted when loaded.
 */
class PropertiesServicePasswordEncryptionTest {

    private final PasswordEncryptionService encryptionService = PasswordEncryptionServiceImpl.getInstance();
    private final File testPropertiesFile = new File("test-encryption-props.properties");

    @BeforeEach
    void setup() {
        // Clean up any previous test files
        if (testPropertiesFile.exists()) {
            testPropertiesFile.delete();
        }
    }

    @Test
    @DisplayName("Passwords are encrypted when saved to file")
    void testPasswordsEncryptedOnSave() throws Exception {
        // Create properties with plain text password
        Properties props = new Properties();
        props.setProperty("http.proxy.password", "MySecretPassword");
        props.setProperty("http.proxy.user", "testuser"); // Non-password property

        // Save using encryption
        savePropertiesWithEncryption(props);

        // Read raw file - password should be encrypted
        Properties rawProps = new Properties();
        try (FileInputStream fis = new FileInputStream(testPropertiesFile)) {
            rawProps.load(fis);
        }

        String savedPassword = rawProps.getProperty("http.proxy.password");
        assertTrue(encryptionService.isEncrypted(savedPassword),
                "Password should be encrypted in saved file");
        assertNotEquals("MySecretPassword", savedPassword,
                "Plain text password should not be in file");
        assertEquals("testuser", rawProps.getProperty("http.proxy.user"),
                "Non-password properties should not be encrypted");
    }

    @Test
    @DisplayName("Passwords are decrypted when loaded from file")
    void testPasswordsDecryptedOnLoad() throws Exception {
        // Create properties with plain text password and save
        Properties props = new Properties();
        props.setProperty("http.proxy.password", "TestPassword123");
        props.setProperty("https.proxy.password", "AnotherSecret");
        props.setProperty("http.proxy.user", "myuser");

        savePropertiesWithEncryption(props);

        // Load using decryption
        Properties loaded = loadPropertiesWithDecryption();

        // Passwords should be decrypted in memory
        assertEquals("TestPassword123", loaded.getProperty("http.proxy.password"),
                "HTTP proxy password should be decrypted");
        assertEquals("AnotherSecret", loaded.getProperty("https.proxy.password"),
                "HTTPS proxy password should be decrypted");
        assertEquals("myuser", loaded.getProperty("http.proxy.user"),
                "User properties should remain unchanged");
    }

    @Test
    @DisplayName("Handles multiple password properties")
    void testMultiplePasswordProperties() throws Exception {
        // Create properties with multiple password fields
        Properties props = new Properties();
        props.setProperty("http.proxy.password", "HttpSecret1");
        props.setProperty("https.proxy.password", "HttpsSecret2");
        props.setProperty("database.password", "DbSecret3");
        props.setProperty("http.proxy.user", "user1");

        savePropertiesWithEncryption(props);

        Properties loaded = loadPropertiesWithDecryption();

        assertEquals("HttpSecret1", loaded.getProperty("http.proxy.password"));
        assertEquals("HttpsSecret2", loaded.getProperty("https.proxy.password"));
        assertEquals("DbSecret3", loaded.getProperty("database.password"));
        assertEquals("user1", loaded.getProperty("http.proxy.user"));
    }

    @Test
    @DisplayName("Handles mixed encrypted and plain text passwords")
    void testMixedEncryptedAndPlainPasswords() throws Exception {
        // Create file with plain text passwords
        Properties initial = new Properties();
        initial.setProperty("http.proxy.password", "PlainPassword");
        initial.setProperty("https.proxy.password", "AnotherPlain");
        try (FileOutputStream fos = new FileOutputStream(testPropertiesFile)) {
            initial.store(fos, null);
        }

        // Load and modify (will encrypt on save)
        Properties loaded = loadPropertiesWithDecryption();
        loaded.setProperty("database.password", "NewPassword");
        savePropertiesWithEncryption(loaded);

        // Verify all are encrypted
        Properties rawProps = new Properties();
        try (FileInputStream fis = new FileInputStream(testPropertiesFile)) {
            rawProps.load(fis);
        }

        assertTrue(encryptionService.isEncrypted(rawProps.getProperty("http.proxy.password")),
                "Original password should be encrypted");
        assertTrue(encryptionService.isEncrypted(rawProps.getProperty("https.proxy.password")),
                "Original password should be encrypted");
        assertTrue(encryptionService.isEncrypted(rawProps.getProperty("database.password")),
                "New password should be encrypted");
    }

    @Test
    @DisplayName("Handles empty passwords")
    void testEmptyPasswords() throws Exception {
        // Create properties with empty password
        Properties props = new Properties();
        props.setProperty("http.proxy.password", "");
        props.setProperty("https.proxy.password", "");
        props.setProperty("http.proxy.user", "");

        savePropertiesWithEncryption(props);

        Properties loaded = loadPropertiesWithDecryption();

        assertEquals("", loaded.getProperty("http.proxy.password"),
                "Empty password should remain empty");
        assertEquals("", loaded.getProperty("https.proxy.password"),
                "Empty password should remain empty");
    }

    @Test
    @DisplayName("Handles special characters in passwords")
    void testSpecialCharactersInPasswords() throws Exception {
        String specialPassword = "P@ssw0rd!@#$%^&*()_+-=";
        Properties props = new Properties();
        props.setProperty("http.proxy.password", specialPassword);

        savePropertiesWithEncryption(props);
        Properties loaded = loadPropertiesWithDecryption();

        assertEquals(specialPassword, loaded.getProperty("http.proxy.password"),
                "Special characters should survive encryption/decryption");
    }

    @Test
    @DisplayName("Handles Unicode passwords")
    void testUnicodePasswordsInProperties() throws Exception {
        String unicodePassword = "密碼Pass🔒";
        Properties props = new Properties();
        props.setProperty("http.proxy.password", unicodePassword);

        savePropertiesWithEncryption(props);
        Properties loaded = loadPropertiesWithDecryption();

        assertEquals(unicodePassword, loaded.getProperty("http.proxy.password"),
                "Unicode characters should survive encryption/decryption");
    }

    /**
     * Helper method to save properties with password encryption.
     * Simulates what PropertiesServiceImpl.saveProperties() does.
     */
    private void savePropertiesWithEncryption(Properties props) throws Exception {
        Properties saveProps = new Properties();
        saveProps.putAll(props);

        // Encrypt all password properties
        for (String key : saveProps.stringPropertyNames()) {
            if (encryptionService.isPasswordProperty(key)) {
                String value = saveProps.getProperty(key);
                if (!encryptionService.isEncrypted(value) && !value.isEmpty()) {
                    try {
                        String encrypted = encryptionService.encrypt(value);
                        saveProps.setProperty(key, encrypted);
                    } catch (EncryptionException e) {
                        // Log but continue
                        System.err.println("Failed to encrypt property: " + key);
                    }
                }
            }
        }

        try (FileOutputStream fos = new FileOutputStream(testPropertiesFile)) {
            saveProps.store(fos, null);
        }
    }

    /**
     * Helper method to load properties with password decryption.
     * Simulates what PropertiesServiceImpl.loadProperties() does.
     */
    private Properties loadPropertiesWithDecryption() throws Exception {
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(testPropertiesFile)) {
            props.load(fis);
        }

        // Decrypt all password properties
        for (String key : props.stringPropertyNames()) {
            if (encryptionService.isPasswordProperty(key)) {
                String value = props.getProperty(key);
                if (encryptionService.isEncrypted(value)) {
                    String decrypted = encryptionService.decrypt(value);
                    props.setProperty(key, decrypted);
                }
            }
        }

        return props;
    }
}
