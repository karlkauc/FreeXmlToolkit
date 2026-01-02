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

/**
 * Service for encrypting and decrypting password properties using AES-256-GCM.
 *
 * <p>Provides machine-specific encryption to prevent properties file portability.
 * Encrypted passwords can only be decrypted on the machine they were created on.
 *
 * <p><strong>Encryption Format:</strong>
 * Encrypted values are prefixed with "ENC(" and follow the pattern:
 * {@code ENC(base64-salt:base64-iv:base64-ciphertext)}
 *
 * <p><strong>Security Features:</strong>
 * <ul>
 *   <li>AES-256-GCM authenticated encryption</li>
 *   <li>Machine-specific key derivation using hardware identifiers</li>
 *   <li>PBKDF2-SHA256 with 100,000 iterations</li>
 *   <li>Random salt and IV per encryption</li>
 *   <li>GCM authentication tag detects tampering</li>
 * </ul>
 *
 * @since 1.2.3
 */
public interface PasswordEncryptionService {

    /**
     * Encrypts a password value for storage in properties files.
     *
     * <p>The returned encrypted value is prefixed with "ENC(" to distinguish
     * it from plain text passwords.
     *
     * @param plainText the password to encrypt
     * @return encrypted value in format: {@code ENC(base64-salt:base64-iv:base64-ciphertext)}
     * @throws EncryptionException if encryption fails
     */
    String encrypt(String plainText) throws EncryptionException;

    /**
     * Decrypts an encrypted password value from properties files.
     *
     * <p>If the value is not encrypted (doesn't start with "ENC("), it is
     * returned unchanged. If decryption fails (wrong machine, corrupted data),
     * returns an empty string to prompt user re-entry.
     *
     * @param encryptedValue the encrypted value from properties file
     * @return the decrypted password, or empty string if decryption fails
     */
    String decrypt(String encryptedValue);

    /**
     * Checks if a property value is encrypted.
     *
     * <p>Returns true if the value starts with "ENC(" prefix.
     *
     * @param value the property value to check
     * @return true if value is encrypted, false otherwise
     */
    boolean isEncrypted(String value);

    /**
     * Checks if a property key represents a password field.
     *
     * <p>Returns true if the key contains "password" (case-insensitive).
     *
     * @param key the property key to check
     * @return true if key is a password property, false otherwise
     */
    boolean isPasswordProperty(String key);
}
