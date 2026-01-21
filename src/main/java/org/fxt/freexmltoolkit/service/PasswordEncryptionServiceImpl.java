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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Enumeration;

/**
 * Implementation of PasswordEncryptionService using AES-256-GCM with machine-specific key derivation.
 *
 * <p><strong>Security Approach:</strong>
 * <ul>
 *   <li><strong>Algorithm:</strong> AES-256-GCM (authenticated encryption)</li>
 *   <li><strong>Key Derivation:</strong> PBKDF2-SHA256, 100,000 iterations from machine hardware IDs</li>
 *   <li><strong>Salt:</strong> Random 16 bytes per encryption, stored with ciphertext</li>
 *   <li><strong>IV:</strong> Random 12 bytes per encryption, stored with ciphertext</li>
 *   <li><strong>GCM Tag:</strong> Detects any tampering with ciphertext</li>
 * </ul>
 *
 * <p><strong>Machine-Specific Security:</strong>
 * The master encryption key is derived from hardware identifiers (MAC address, hostname, user home).
 * This ensures that encrypted passwords only work on the machine they were created on.
 * If the properties file is copied to another machine, decryption will fail.
 *
 * <p><strong>Key Caching:</strong>
 * The master key is derived once per session and cached in memory. Per-encryption keys are
 * derived from the master key, providing both security and performance.
 *
 * @since 1.2.3
 */
public class PasswordEncryptionServiceImpl implements PasswordEncryptionService {

    private static final Logger logger = LogManager.getLogger(PasswordEncryptionServiceImpl.class);
    private static final PasswordEncryptionServiceImpl instance = new PasswordEncryptionServiceImpl();

    // Encryption constants
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int SALT_LENGTH = 16; // bytes
    private static final int MASTER_KEY_ITERATIONS = 100_000;
    private static final int ENCRYPTION_KEY_ITERATIONS = 10_000;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ENCRYPTION_PREFIX = "ENC(";
    private static final String ENCRYPTION_SUFFIX = ")";

    // Cached master key (derived once per session)
    private SecretKey encryptionKey;
    private boolean keyDerivationFailed = false;

    /**
     * Private default constructor for singleton pattern.
     *
     * <p>This constructor initializes the service instance without deriving the encryption key.
     * The encryption key is derived lazily on first use via {@link #ensureKeyInitialized()}.
     *
     * <p>Use {@link #getInstance()} to obtain the singleton instance.
     */
    private PasswordEncryptionServiceImpl() {
        // Default constructor - encryption key is derived lazily on first use
    }

    /**
     * Returns the singleton instance of PasswordEncryptionServiceImpl.
     *
     * @return the singleton instance
     */
    public static PasswordEncryptionServiceImpl getInstance() {
        return instance;
    }

    /**
     * Encrypts a password value using AES-256-GCM with machine-specific key.
     *
     * @param plainText the password to encrypt
     * @return encrypted value in format: ENC(base64-salt:base64-iv:base64-ciphertext)
     * @throws EncryptionException if encryption fails
     */
    @Override
    public String encrypt(String plainText) throws EncryptionException {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            ensureKeyInitialized();

            // Generate random salt and IV
            byte[] salt = generateRandomBytes(SALT_LENGTH);
            byte[] iv = generateRandomBytes(GCM_IV_LENGTH);

            // Derive key from master key + salt
            SecretKey derivedKey = deriveKeyFromSalt(salt);

            // Encrypt with AES-GCM
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey, gcmSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Format: ENC(base64-salt:base64-iv:base64-ciphertext)
            String encoded = Base64.getEncoder().encodeToString(salt) + ":" +
                    Base64.getEncoder().encodeToString(iv) + ":" +
                    Base64.getEncoder().encodeToString(cipherText);

            logger.debug("Password encrypted successfully");
            return ENCRYPTION_PREFIX + encoded + ENCRYPTION_SUFFIX;

        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt password", e);
        }
    }

    /**
     * Decrypts an encrypted password value using AES-256-GCM.
     *
     * <p>If decryption fails (wrong machine, corrupted data), returns empty string
     * to allow graceful degradation and prompt user re-entry.
     *
     * @param encryptedValue the encrypted value from properties file
     * @return the decrypted password, or empty string if decryption fails
     */
    @Override
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return "";
        }

        if (!isEncrypted(encryptedValue)) {
            return encryptedValue; // Plain text, return as-is
        }

        try {
            ensureKeyInitialized();

            // Parse: ENC(base64-salt:base64-iv:base64-ciphertext)
            String encoded = encryptedValue.substring(4, encryptedValue.length() - 1);
            String[] parts = encoded.split(":");

            if (parts.length != 3) {
                logger.warn("Invalid encrypted format - expected 3 parts, got {}", parts.length);
                return "";
            }

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);

            // Derive same key from salt
            SecretKey derivedKey = deriveKeyFromSalt(salt);

            // Decrypt with AES-GCM
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, gcmSpec);
            byte[] plainText = cipher.doFinal(cipherText);

            logger.debug("Password decrypted successfully");
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.warn("Decryption failed - data may be corrupted or from different machine: {}", e.getMessage());
            return ""; // Return empty string on failure (prompts user re-entry)
        }
    }

    /**
     * Checks if a property value is encrypted.
     *
     * @param value the property value to check
     * @return true if value starts with "ENC(", false otherwise
     */
    @Override
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX);
    }

    /**
     * Checks if a property key represents a password field.
     *
     * @param key the property key to check
     * @return true if key contains "password" (case-insensitive), false otherwise
     */
    @Override
    public boolean isPasswordProperty(String key) {
        return key != null && key.toLowerCase().contains("password");
    }

    /**
     * Ensures the encryption key is initialized.
     * Derives key on first call, cached for subsequent calls.
     *
     * @throws EncryptionException if key derivation fails
     */
    private void ensureKeyInitialized() throws EncryptionException {
        if (encryptionKey == null && !keyDerivationFailed) {
            deriveEncryptionKey();
        }

        if (keyDerivationFailed) {
            throw new EncryptionException("Key derivation previously failed");
        }
    }

    /**
     * Derives the master encryption key from machine hardware identifiers.
     * This key is cached and reused for all encryptions in the session.
     *
     * @throws EncryptionException if key derivation fails
     */
    private void deriveEncryptionKey() throws EncryptionException {
        try {
            String machineId = getMachineIdentifier();
            String keyMaterial = machineId + "FreeXmlToolkit-2024-PasswordEncryption-v1";

            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            KeySpec spec = new PBEKeySpec(
                    keyMaterial.toCharArray(),
                    "FreeXmlToolkit-2024".getBytes(StandardCharsets.UTF_8),
                    MASTER_KEY_ITERATIONS,
                    KEY_SIZE
            );

            SecretKey tmp = factory.generateSecret(spec);
            this.encryptionKey = new SecretKeySpec(tmp.getEncoded(), 0, tmp.getEncoded().length, "AES");

            logger.info("Master encryption key derived successfully");

        } catch (Exception e) {
            keyDerivationFailed = true;
            throw new EncryptionException("Failed to derive encryption key", e);
        }
    }

    /**
     * Derives a per-encryption key from the master key and salt.
     * This provides key rotation while maintaining security.
     *
     * @param salt the salt to use for key derivation
     * @return the derived key
     * @throws Exception if key derivation fails
     */
    private SecretKey deriveKeyFromSalt(byte[] salt) throws Exception {
        ensureKeyInitialized();

        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        String masterKeyBase64 = Base64.getEncoder().encodeToString(encryptionKey.getEncoded());
        KeySpec spec = new PBEKeySpec(
                masterKeyBase64.toCharArray(),
                salt,
                ENCRYPTION_KEY_ITERATIONS,
                KEY_SIZE
        );

        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), 0, tmp.getEncoded().length, "AES");
    }

    /**
     * Gets the machine identifier from hardware components.
     * Uses MAC address, hostname, and user home in priority order.
     * Falls back to application constant if no hardware IDs found.
     *
     * @return the machine identifier string
     */
    private String getMachineIdentifier() {
        StringBuilder identifier = new StringBuilder();

        // 1. MAC Address (most stable)
        String macAddress = getMacAddress();
        if (macAddress != null && !macAddress.isEmpty()) {
            identifier.append(macAddress);
        }

        // 2. Computer hostname
        String computerName = getComputerName();
        if (computerName != null && !computerName.isEmpty()) {
            if (identifier.length() > 0) {
                identifier.append(":");
            }
            identifier.append(computerName);
        }

        // 3. User home directory
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isEmpty()) {
            if (identifier.length() > 0) {
                identifier.append(":");
            }
            identifier.append(userHome);
        }

        // Fallback
        if (identifier.length() == 0) {
            logger.warn("No hardware identifiers found, using application fallback");
            identifier.append("FALLBACK-FreeXmlToolkit-2024");
        }

        return identifier.toString();
    }

    /**
     * Gets the MAC address of the first non-loopback, non-virtual network interface.
     *
     * @return MAC address as hex string, or null if not found
     */
    private String getMacAddress() {
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();
                byte[] mac = network.getHardwareAddress();

                if (mac != null && !network.isLoopback() && !network.isVirtual()) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    logger.debug("Retrieved MAC address from interface: {}", network.getName());
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve MAC address: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Gets the computer/hostname name.
     *
     * @return hostname, or null if not available
     */
    private String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            logger.debug("Could not retrieve computer name: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generates random bytes using SecureRandom.
     *
     * @param length the number of random bytes to generate
     * @return array of random bytes
     */
    private byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
