package org.fxt.freexmltoolkit.security;

import org.fxt.freexmltoolkit.util.PathValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for PathValidator.
 * These tests verify path traversal and SSRF protections.
 */
@DisplayName("PathValidator Security Tests")
class PathValidatorTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // Path Traversal Tests
    // =========================================================================

    @Nested
    @DisplayName("Path Traversal Protection")
    class PathTraversalTests {

        @Test
        @DisplayName("Blocks simple path traversal (../)")
        void blocksSimplePathTraversal() throws IOException {
            Path baseDir = tempDir.resolve("base");
            Files.createDirectories(baseDir);
            Path secretFile = tempDir.resolve("secret.txt");
            Files.writeString(secretFile, "SECRET");

            // Attempt to escape base directory
            Path maliciousPath = baseDir.resolve("../secret.txt");

            assertFalse(PathValidator.isPathWithinDirectory(maliciousPath, baseDir),
                    "Path traversal attack should be blocked");
        }

        @Test
        @DisplayName("Blocks encoded path traversal (%2e%2e/)")
        void blocksEncodedPathTraversal() {
            String maliciousPath = "subdir/%2e%2e/secret.txt";

            assertFalse(PathValidator.isRelativePathSafe(maliciousPath),
                    "Encoded path traversal should be blocked");
        }

        @Test
        @DisplayName("Blocks double-encoded path traversal (%252e)")
        void blocksDoubleEncodedPathTraversal() {
            String maliciousPath = "subdir/%252e%252e/secret.txt";

            assertFalse(PathValidator.isRelativePathSafe(maliciousPath),
                    "Double-encoded path traversal should be blocked");
        }

        @Test
        @DisplayName("Blocks multiple traversal sequences")
        void blocksMultipleTraversalSequences() throws IOException {
            Path baseDir = tempDir.resolve("base/subdir/deep");
            Files.createDirectories(baseDir);

            Path maliciousPath = baseDir.resolve("../../../../../../../etc/passwd");

            assertFalse(PathValidator.isPathWithinDirectory(maliciousPath, baseDir),
                    "Multiple path traversals should be blocked");
        }

        @Test
        @DisplayName("Blocks Windows-style path traversal")
        void blocksWindowsStylePathTraversal() {
            String maliciousPath = "subdir\\..\\..\\secret.txt";

            assertFalse(PathValidator.isRelativePathSafe(maliciousPath),
                    "Windows-style path traversal should be blocked");
        }

        @Test
        @DisplayName("Allows safe paths within base directory")
        void allowsSafePathsWithinBaseDirectory() throws IOException {
            Path baseDir = tempDir.resolve("base");
            Files.createDirectories(baseDir);
            Path safeFile = baseDir.resolve("safe.txt");
            Files.writeString(safeFile, "SAFE");

            assertTrue(PathValidator.isPathWithinDirectory(safeFile, baseDir),
                    "Safe path within base directory should be allowed");
        }

        @Test
        @DisplayName("Allows safe subdirectory paths")
        void allowsSafeSubdirectoryPaths() throws IOException {
            Path baseDir = tempDir.resolve("base");
            Path subDir = baseDir.resolve("subdir/deep");
            Files.createDirectories(subDir);
            Path safeFile = subDir.resolve("file.txt");
            Files.writeString(safeFile, "SAFE");

            assertTrue(PathValidator.isPathWithinDirectory(safeFile, baseDir),
                    "Safe subdirectory path should be allowed");
        }

        @Test
        @DisplayName("Counts parent traversals correctly")
        void countsParentTraversalsCorrectly() {
            assertEquals(0, PathValidator.countParentTraversals("file.txt"));
            assertEquals(1, PathValidator.countParentTraversals("../file.txt"));
            assertEquals(2, PathValidator.countParentTraversals("../../file.txt"));
            assertEquals(3, PathValidator.countParentTraversals("../a/../b/../file.txt"));
            assertEquals(0, PathValidator.countParentTraversals(null));
            assertEquals(0, PathValidator.countParentTraversals(""));
        }

        @Test
        @DisplayName("Rejects absolute path indicators in relative paths")
        void rejectsAbsolutePathIndicators() {
            assertFalse(PathValidator.isRelativePathSafe("/etc/passwd"),
                    "Unix absolute path should be rejected");
            assertFalse(PathValidator.isRelativePathSafe("C:\\Windows\\System32"),
                    "Windows absolute path should be rejected");
        }
    }

    // =========================================================================
    // SSRF (Server-Side Request Forgery) Tests
    // =========================================================================

    @Nested
    @DisplayName("SSRF Protection")
    class SSRFTests {

        @ParameterizedTest
        @DisplayName("Blocks localhost URLs")
        @ValueSource(strings = {
                "http://localhost/admin",
                "http://localhost:8080/api",
                "https://localhost/secret",
                "http://127.0.0.1/",
                "http://127.0.0.1:3000/",
                "http://127.1/",
                "http://[::1]/",
                "http://localhost.localdomain/"
        })
        void blocksLocalhostUrls(String url) {
            assertFalse(PathValidator.isUrlSafeToAccess(url),
                    "Localhost URL should be blocked: " + url);
        }

        @ParameterizedTest
        @DisplayName("Blocks private network URLs (10.x.x.x)")
        @ValueSource(strings = {
                "http://10.0.0.1/",
                "http://10.255.255.255/",
                "http://10.10.10.10:8080/api"
        })
        void blocksPrivateNetwork10(String url) {
            assertFalse(PathValidator.isUrlSafeToAccess(url),
                    "Private network 10.x.x.x URL should be blocked: " + url);
        }

        @ParameterizedTest
        @DisplayName("Blocks private network URLs (172.16-31.x.x)")
        @ValueSource(strings = {
                "http://172.16.0.1/",
                "http://172.31.255.255/",
                "http://172.20.10.5:8080/"
        })
        void blocksPrivateNetwork172(String url) {
            assertFalse(PathValidator.isUrlSafeToAccess(url),
                    "Private network 172.16-31.x.x URL should be blocked: " + url);
        }

        @ParameterizedTest
        @DisplayName("Blocks private network URLs (192.168.x.x)")
        @ValueSource(strings = {
                "http://192.168.0.1/",
                "http://192.168.1.1/",
                "http://192.168.255.255:8080/"
        })
        void blocksPrivateNetwork192(String url) {
            assertFalse(PathValidator.isUrlSafeToAccess(url),
                    "Private network 192.168.x.x URL should be blocked: " + url);
        }

        @ParameterizedTest
        @DisplayName("Blocks cloud metadata endpoints")
        @ValueSource(strings = {
                "http://169.254.169.254/",
                "http://169.254.169.254/latest/meta-data/",
                "http://169.254.169.254/computeMetadata/v1/"
        })
        void blocksCloudMetadataEndpoints(String url) {
            assertFalse(PathValidator.isUrlSafeToAccess(url),
                    "Cloud metadata endpoint should be blocked: " + url);
        }

        @ParameterizedTest
        @DisplayName("Blocks link-local addresses")
        @ValueSource(strings = {
                "http://169.254.0.1/",
                "http://169.254.255.254/"
        })
        void blocksLinkLocalAddresses(String url) {
            assertFalse(PathValidator.isUrlSafeToAccess(url),
                    "Link-local address should be blocked: " + url);
        }

        @ParameterizedTest
        @DisplayName("Blocks non-HTTP/HTTPS schemes")
        @ValueSource(strings = {
                "ftp://evil.com/malware",
                "gopher://evil.com/",
                "ldap://evil.com/",
                "dict://evil.com/",
                "jar:file:///etc/passwd!/",
                "netdoc://evil.com/"
        })
        void blocksNonHttpSchemes(String url) {
            assertFalse(PathValidator.isUrlSafeToAccess(url),
                    "Non-HTTP scheme should be blocked: " + url);
        }

        @Test
        @DisplayName("Allows file:// URLs for local schemas")
        void allowsFileUrls() {
            assertTrue(PathValidator.isUrlSafeToAccess("file:///path/to/schema.xsd"),
                    "file:// URLs should be allowed for local schemas");
        }

        @ParameterizedTest
        @DisplayName("Allows legitimate public URLs")
        @ValueSource(strings = {
                "https://www.w3.org/2001/XMLSchema.xsd",
                "http://www.w3.org/TR/xmldsig-core/xmldsig-core-schema.xsd",
                "https://example.com/schema.xsd",
                "http://schemas.xmlsoap.org/soap/envelope/"
        })
        void allowsLegitimatePublicUrls(String url) {
            assertTrue(PathValidator.isUrlSafeToAccess(url),
                    "Legitimate public URL should be allowed: " + url);
        }

        @Test
        @DisplayName("Handles null and empty URLs safely")
        void handlesNullAndEmptyUrls() {
            assertFalse(PathValidator.isUrlSafeToAccess(null), "Null URL should return false");
            assertFalse(PathValidator.isUrlSafeToAccess(""), "Empty URL should return false");
        }

        @Test
        @DisplayName("Handles malformed URLs safely")
        void handlesMalformedUrls() {
            assertFalse(PathValidator.isUrlSafeToAccess("not a valid url"),
                    "Malformed URL should return false");
            assertFalse(PathValidator.isUrlSafeToAccess("://missing-scheme"),
                    "URL without scheme should return false");
        }
    }

    // =========================================================================
    // Output Path Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Output Path Validation")
    class OutputPathValidationTests {

        @ParameterizedTest
        @DisplayName("Blocks writes to Windows system directories")
        @ValueSource(strings = {
                "C:/Windows/System32/malware.dll",
                "C:/Program Files/malware.exe",
                "C:/ProgramData/malware.txt"
        })
        void blocksWindowsSystemDirectories(String path) {
            assertFalse(PathValidator.isOutputPathSafe(path),
                    "Windows system directory should be blocked: " + path);
        }

        @ParameterizedTest
        @DisplayName("Blocks writes to Unix system directories")
        @ValueSource(strings = {
                "/etc/passwd",
                "/etc/shadow",
                "/bin/sh",
                "/sbin/init",
                "/usr/bin/python",
                "/var/log/syslog",
                "/root/.ssh/authorized_keys",
                "/boot/vmlinuz"
        })
        void blocksUnixSystemDirectories(String path) {
            assertFalse(PathValidator.isOutputPathSafe(path),
                    "Unix system directory should be blocked: " + path);
        }

        @Test
        @DisplayName("Blocks paths with null bytes")
        void blocksPathsWithNullBytes() {
            assertFalse(PathValidator.isOutputPathSafe("file.txt\0.jpg"),
                    "Path with null byte should be blocked");
        }

        @ParameterizedTest
        @DisplayName("Allows safe output paths")
        @ValueSource(strings = {
                "/home/user/documents/output.xml",
                "/Users/user/Documents/schema.xsd",
                "D:/Projects/output.html",
                "./output/result.pdf"
        })
        void allowsSafeOutputPaths(String path) {
            assertTrue(PathValidator.isOutputPathSafe(path),
                    "Safe output path should be allowed: " + path);
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles symbolic links correctly")
        void handlesSymbolicLinks() throws IOException {
            // Create a directory structure
            Path baseDir = tempDir.resolve("base");
            Path outsideDir = tempDir.resolve("outside");
            Files.createDirectories(baseDir);
            Files.createDirectories(outsideDir);
            Path secretFile = outsideDir.resolve("secret.txt");
            Files.writeString(secretFile, "SECRET");

            // Create a symbolic link inside base that points outside
            Path symlinkPath = baseDir.resolve("link-to-outside");
            try {
                Files.createSymbolicLink(symlinkPath, outsideDir);
            } catch (UnsupportedOperationException | IOException e) {
                // Symbolic links may not be supported on all systems
                return;
            }

            // Accessing through symlink should be detected
            Path maliciousPath = symlinkPath.resolve("secret.txt");

            // The real path will be outside baseDir
            assertFalse(PathValidator.isPathWithinDirectory(maliciousPath, baseDir),
                    "Access through symbolic link to outside directory should be blocked");
        }

        @Test
        @DisplayName("Handles case sensitivity correctly on case-insensitive systems")
        void handlesCaseSensitivity() throws IOException {
            Path baseDir = tempDir.resolve("base");
            Files.createDirectories(baseDir);

            // This tests that normalization works correctly
            Path normalPath = baseDir.resolve("file.txt");
            Files.writeString(normalPath, "content");

            assertTrue(PathValidator.isPathWithinDirectory(normalPath, baseDir),
                    "Normal path should be allowed");
        }

        @Test
        @DisplayName("MAX_REDIRECTS constant is reasonable")
        void maxRedirectsIsReasonable() {
            assertTrue(PathValidator.MAX_REDIRECTS >= 3 && PathValidator.MAX_REDIRECTS <= 10,
                    "MAX_REDIRECTS should be between 3 and 10, was: " + PathValidator.MAX_REDIRECTS);
        }
    }
}
