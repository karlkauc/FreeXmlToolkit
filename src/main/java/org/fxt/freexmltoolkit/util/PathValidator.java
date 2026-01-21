package org.fxt.freexmltoolkit.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;

/**
 * Utility class for validating file paths and URLs to prevent security vulnerabilities.
 *
 * <p>This class provides methods to prevent:
 * <ul>
 *   <li>Path traversal attacks (e.g., using ".." to escape directories)</li>
 *   <li>SSRF (Server-Side Request Forgery) attacks</li>
 *   <li>Access to private/internal network addresses</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // Validate a resolved path stays within base directory
 * if (PathValidator.isPathWithinDirectory(resolvedPath, baseDir)) {
 *     // Safe to use resolvedPath
 * }
 *
 * // Validate URL is safe to access (not internal)
 * if (PathValidator.isUrlSafeToAccess(schemaLocation)) {
 *     // Safe to fetch URL
 * }
 * }</pre>
 *
 * @author FreeXmlToolkit Security Team
 * @since 2.0
 */
public final class PathValidator {

    private static final Logger logger = LogManager.getLogger(PathValidator.class);

    /**
     * Maximum number of redirects to follow for URL fetching.
     */
    public static final int MAX_REDIRECTS = 5;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private PathValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that a resolved path is within the specified base directory.
     *
     * <p>This method prevents path traversal attacks by ensuring that the
     * canonical form of the resolved path starts with the canonical form
     * of the base directory.
     *
     * @param resolvedPath the path to validate (may contain ".." sequences)
     * @param baseDir the base directory that the path must stay within
     * @return true if the resolved path is within the base directory, false otherwise
     */
    public static boolean isPathWithinDirectory(File resolvedPath, File baseDir) {
        try {
            String canonicalResolved = resolvedPath.getCanonicalPath();
            String canonicalBase = baseDir.getCanonicalPath();

            boolean isWithin = canonicalResolved.startsWith(canonicalBase + File.separator) ||
                              canonicalResolved.equals(canonicalBase);

            if (!isWithin) {
                logger.warn("Path traversal attempt detected: {} is outside base directory {}",
                    resolvedPath, baseDir);
            }

            return isWithin;
        } catch (IOException e) {
            logger.error("Failed to canonicalize paths for security check: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a resolved path is within the specified base directory.
     *
     * <p>This method uses NIO Path API for validation.
     *
     * @param resolvedPath the path to validate (may contain ".." sequences)
     * @param baseDir the base directory that the path must stay within
     * @return true if the resolved path is within the base directory, false otherwise
     */
    public static boolean isPathWithinDirectory(Path resolvedPath, Path baseDir) {
        try {
            Path canonicalResolved = resolvedPath.toRealPath();
            Path canonicalBase = baseDir.toRealPath();

            boolean isWithin = canonicalResolved.startsWith(canonicalBase);

            if (!isWithin) {
                logger.warn("Path traversal attempt detected: {} is outside base directory {}",
                    resolvedPath, baseDir);
            }

            return isWithin;
        } catch (IOException e) {
            // File might not exist yet - use normalized paths instead
            try {
                Path normalizedResolved = resolvedPath.toAbsolutePath().normalize();
                Path normalizedBase = baseDir.toAbsolutePath().normalize();

                boolean isWithin = normalizedResolved.startsWith(normalizedBase);

                if (!isWithin) {
                    logger.warn("Path traversal attempt detected: {} is outside base directory {}",
                        resolvedPath, baseDir);
                }

                return isWithin;
            } catch (Exception e2) {
                logger.error("Failed to validate paths: {}", e2.getMessage());
                return false;
            }
        }
    }

    /**
     * Validates a relative path for path traversal sequences.
     *
     * <p>This method checks if the path contains suspicious sequences that could
     * be used for path traversal attacks, before the path is resolved.
     *
     * @param relativePath the relative path to validate
     * @return true if the path appears safe, false if it contains suspicious sequences
     */
    public static boolean isRelativePathSafe(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }

        // Normalize separators
        String normalized = relativePath.replace('\\', '/');

        // Check for absolute path indicators
        if (normalized.startsWith("/") || normalized.matches("^[a-zA-Z]:.*")) {
            logger.warn("Relative path contains absolute path indicators: {}", relativePath);
            return false;
        }

        // Check for path traversal sequences
        if (normalized.contains("..")) {
            logger.warn("Relative path contains traversal sequences: {}", relativePath);
            return false;
        }

        // Check for encoded traversal sequences
        String lowerCase = relativePath.toLowerCase();
        if (lowerCase.contains("%2e%2e") || lowerCase.contains("%252e")) {
            logger.warn("Relative path contains encoded traversal sequences: {}", relativePath);
            return false;
        }

        return true;
    }

    /**
     * Validates that a URL is safe to access (not targeting internal networks).
     *
     * <p>This method prevents SSRF attacks by checking if the URL points to:
     * <ul>
     *   <li>Localhost (127.0.0.0/8, ::1)</li>
     *   <li>Private networks (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)</li>
     *   <li>Link-local addresses (169.254.0.0/16, fe80::/10)</li>
     *   <li>Metadata endpoints (169.254.169.254)</li>
     * </ul>
     *
     * @param urlString the URL to validate
     * @return true if the URL is safe to access, false if it targets internal resources
     */
    public static boolean isUrlSafeToAccess(String urlString) {
        if (urlString == null || urlString.isEmpty()) {
            return false;
        }

        try {
            URI uri = new URI(urlString);
            String scheme = uri.getScheme();

            // Only allow HTTP/HTTPS
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                // Allow file: URLs for local schemas
                if (scheme != null && scheme.equalsIgnoreCase("file")) {
                    return true;
                }
                logger.warn("URL has unsafe scheme: {}", scheme);
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                logger.warn("URL has no host: {}", urlString);
                return false;
            }

            return isHostSafeToAccess(host);
        } catch (URISyntaxException e) {
            logger.warn("Invalid URL: {}", urlString);
            return false;
        }
    }

    /**
     * Validates that a host is safe to access (not an internal address).
     *
     * @param host the hostname to validate
     * @return true if the host is safe to access, false if it's an internal address
     */
    public static boolean isHostSafeToAccess(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }

        // Check for obvious localhost references
        String lowerHost = host.toLowerCase();
        if (lowerHost.equals("localhost") || lowerHost.equals("localhost.localdomain")) {
            logger.warn("URL targets localhost: {}", host);
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            return isAddressSafeToAccess(address);
        } catch (UnknownHostException e) {
            // Could not resolve - might be safe, allow it but log
            logger.debug("Could not resolve host for security check: {}", host);
            return true;
        }
    }

    /**
     * Validates that an IP address is safe to access.
     *
     * @param address the address to validate
     * @return true if the address is safe to access, false if it's internal/private
     */
    public static boolean isAddressSafeToAccess(InetAddress address) {
        if (address == null) {
            return false;
        }

        // Check for loopback
        if (address.isLoopbackAddress()) {
            logger.warn("URL targets loopback address: {}", address.getHostAddress());
            return false;
        }

        // Check for link-local (169.254.x.x, fe80::/10)
        if (address.isLinkLocalAddress()) {
            logger.warn("URL targets link-local address: {}", address.getHostAddress());
            return false;
        }

        // Check for site-local (private networks: 10.x, 172.16-31.x, 192.168.x)
        if (address.isSiteLocalAddress()) {
            logger.warn("URL targets private network address: {}", address.getHostAddress());
            return false;
        }

        // Check for AWS/cloud metadata endpoint (169.254.169.254)
        byte[] bytes = address.getAddress();
        if (bytes.length == 4 &&
            (bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254 &&
            (bytes[2] & 0xFF) == 169 && (bytes[3] & 0xFF) == 254) {
            logger.warn("URL targets cloud metadata endpoint: {}", address.getHostAddress());
            return false;
        }

        // Check for broadcast
        if (address.isMulticastAddress()) {
            logger.warn("URL targets multicast address: {}", address.getHostAddress());
            return false;
        }

        return true;
    }

    /**
     * Validates an output file path for export operations.
     *
     * <p>This method performs basic validation to ensure the path:
     * <ul>
     *   <li>Is not null or empty</li>
     *   <li>Does not contain null bytes</li>
     *   <li>Does not target system directories</li>
     * </ul>
     *
     * @param outputPath the output path to validate
     * @return true if the path appears safe for writing, false otherwise
     */
    public static boolean isOutputPathSafe(String outputPath) {
        if (outputPath == null || outputPath.isEmpty()) {
            return false;
        }

        // Check for null bytes (can be used to truncate paths)
        if (outputPath.contains("\0")) {
            logger.warn("Output path contains null bytes: suspicious");
            return false;
        }

        // Normalize for comparison
        String normalized = outputPath.toLowerCase().replace('\\', '/');

        // Check for obvious system paths (Windows)
        if (normalized.startsWith("c:/windows/") ||
            normalized.startsWith("c:/program files/") ||
            normalized.startsWith("c:/programdata/")) {
            logger.warn("Output path targets Windows system directory");
            return false;
        }

        // Check for obvious system paths (Unix)
        if (normalized.startsWith("/etc/") ||
            normalized.startsWith("/bin/") ||
            normalized.startsWith("/sbin/") ||
            normalized.startsWith("/usr/bin/") ||
            normalized.startsWith("/usr/sbin/") ||
            normalized.startsWith("/var/") ||
            normalized.startsWith("/root/") ||
            normalized.startsWith("/boot/")) {
            logger.warn("Output path targets Unix system directory");
            return false;
        }

        return true;
    }

    /**
     * Counts the number of parent directory traversals (..) in a path.
     *
     * @param path the path to analyze
     * @return the number of parent traversal sequences
     */
    public static int countParentTraversals(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }

        String normalized = path.replace('\\', '/');
        String[] parts = normalized.split("/");

        int count = 0;
        for (String part : parts) {
            if ("..".equals(part)) {
                count++;
            }
        }

        return count;
    }
}
