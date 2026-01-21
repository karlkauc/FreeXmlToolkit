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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents metadata for a cached schema file.
 *
 * <p>This record contains comprehensive information about a cached XSD file including:
 * <ul>
 *   <li>Basic file information (filename, URL, hashes, size)</li>
 *   <li>HTTP response details (status code, headers)</li>
 *   <li>Schema-specific information (namespace, version, imports)</li>
 *   <li>Usage statistics (access count, last access time)</li>
 * </ul>
 *
 * @param localFilename The local filename
 * @param remoteUrl The remote URL
 * @param downloadTimestamp The download timestamp
 * @param fileSizeBytes The file size in bytes
 * @param md5Hash The MD5 hash of the file
 * @param sha256Hash The SHA-256 hash of the file
 * @param http HTTP response information
 * @param schema Schema-specific information
 * @param usage Usage statistics
 */
public record SchemaCacheEntry(
        String localFilename,
        String remoteUrl,
        Instant downloadTimestamp,
        long fileSizeBytes,
        String md5Hash,
        String sha256Hash,
        HttpInfo http,
        SchemaInfo schema,
        UsageInfo usage
) {

    /**
     * HTTP response information captured during download.
     * @param statusCode The HTTP status code
     * @param contentType The Content-Type header
     * @param lastModified The Last-Modified header
     * @param etag The ETag header
     * @param contentLength The Content-Length header
     * @param downloadDurationMs The download duration in milliseconds
     */
    public record HttpInfo(
            int statusCode,
            String contentType,
            String lastModified,
            String etag,
            Long contentLength,
            long downloadDurationMs
    ) {
        /**
         * Creates HttpInfo with default values for optional fields.
         *
         * <p>This factory method creates an HttpInfo instance with only the required
         * status code and download duration, setting all optional fields to null.
         *
         * @param statusCode the HTTP status code received from the server
         * @param downloadDurationMs the time taken to download the schema in milliseconds
         * @return a new HttpInfo instance with default values for optional fields
         */
        public static HttpInfo of(int statusCode, long downloadDurationMs) {
            return new HttpInfo(statusCode, null, null, null, null, downloadDurationMs);
        }
    }

    /**
     * Schema-specific information extracted from the XSD file.
     * @param targetNamespace The target namespace
     * @param xsdVersion The XSD version (1.0 or 1.1)
     * @param imports List of imported schema locations
     * @param includes List of included schema locations
     * @param redefines List of redefined schema locations
     */
    public record SchemaInfo(
            String targetNamespace,
            String xsdVersion,
            List<String> imports,
            List<String> includes,
            List<String> redefines
    ) {
        /**
         * Creates an empty SchemaInfo for cases where parsing failed.
         *
         * <p>This factory method creates a SchemaInfo instance with all fields set to
         * null or empty lists, suitable for cases where schema parsing was unsuccessful.
         *
         * @return a new SchemaInfo instance with empty/null values
         */
        public static SchemaInfo empty() {
            return new SchemaInfo(null, null, List.of(), List.of(), List.of());
        }

        /**
         * Returns a copy of this SchemaInfo with the specified target namespace.
         *
         * <p>Creates a new SchemaInfo instance with the same values as this instance,
         * except for the target namespace which is replaced with the provided value.
         *
         * @param namespace the new target namespace to set
         * @return a new SchemaInfo instance with the updated target namespace
         */
        public SchemaInfo withTargetNamespace(String namespace) {
            return new SchemaInfo(namespace, xsdVersion, imports, includes, redefines);
        }

        /**
         * Returns a copy of this SchemaInfo with the specified XSD version.
         *
         * <p>Creates a new SchemaInfo instance with the same values as this instance,
         * except for the XSD version which is replaced with the provided value.
         *
         * @param version the new XSD version to set (typically "1.0" or "1.1")
         * @return a new SchemaInfo instance with the updated XSD version
         */
        public SchemaInfo withXsdVersion(String version) {
            return new SchemaInfo(targetNamespace, version, imports, includes, redefines);
        }
    }

    /**
     * Usage statistics for the cached schema.
     * @param lastAccessTimestamp The timestamp of the last access
     * @param accessCount The total number of accesses
     * @param referencedBy List of URLs that reference this schema
     */
    public record UsageInfo(
            Instant lastAccessTimestamp,
            long accessCount,
            List<String> referencedBy
    ) {
        /**
         * Creates initial usage info for a newly cached schema.
         *
         * <p>This factory method creates a UsageInfo instance suitable for a newly
         * cached schema, with the current timestamp, an access count of 1, and an
         * empty list of referencing schemas.
         *
         * @return a new UsageInfo instance with initial values
         */
        public static UsageInfo initial() {
            return new UsageInfo(Instant.now(), 1, new ArrayList<>());
        }

        /**
         * Returns a copy of this UsageInfo with incremented access count and updated timestamp.
         *
         * <p>Creates a new UsageInfo instance with the current timestamp as the last access time,
         * the access count incremented by one, and the same list of referencing schemas.
         *
         * @return a new UsageInfo instance with updated access statistics
         */
        public UsageInfo withAccessRecorded() {
            return new UsageInfo(Instant.now(), accessCount + 1, referencedBy);
        }

        /**
         * Returns a copy of this UsageInfo with an additional referencing schema.
         *
         * <p>Creates a new UsageInfo instance with the same timestamp and access count,
         * but with the specified schema URL added to the list of referencing schemas.
         * If the schema URL is already in the list, it will not be added again.
         *
         * @param schemaUrl the URL of the schema that references this cached schema
         * @return a new UsageInfo instance with the updated reference list
         */
        public UsageInfo withReferencedBy(String schemaUrl) {
            List<String> newReferences = new ArrayList<>(referencedBy != null ? referencedBy : List.of());
            if (!newReferences.contains(schemaUrl)) {
                newReferences.add(schemaUrl);
            }
            return new UsageInfo(lastAccessTimestamp, accessCount, newReferences);
        }
    }

    /**
     * Creates a new entry with updated usage statistics.
     *
     * <p>Returns a copy of this SchemaCacheEntry with the access count incremented
     * and the last access timestamp updated to the current time. All other fields
     * remain unchanged.
     *
     * @return a new SchemaCacheEntry instance with updated usage statistics
     */
    public SchemaCacheEntry withAccessRecorded() {
        return new SchemaCacheEntry(
                localFilename,
                remoteUrl,
                downloadTimestamp,
                fileSizeBytes,
                md5Hash,
                sha256Hash,
                http,
                schema,
                usage != null ? usage.withAccessRecorded() : UsageInfo.initial()
        );
    }

    /**
     * Creates a new entry with an additional referencing schema.
     *
     * <p>Returns a copy of this SchemaCacheEntry with the specified schema URL added
     * to the list of schemas that reference this cached schema. All other fields
     * remain unchanged.
     *
     * @param schemaUrl the URL of the schema that references this cached schema
     * @return a new SchemaCacheEntry instance with the updated reference list
     */
    public SchemaCacheEntry withReferencedBy(String schemaUrl) {
        return new SchemaCacheEntry(
                localFilename,
                remoteUrl,
                downloadTimestamp,
                fileSizeBytes,
                md5Hash,
                sha256Hash,
                http,
                schema,
                usage != null ? usage.withReferencedBy(schemaUrl) : UsageInfo.initial().withReferencedBy(schemaUrl)
        );
    }

    /**
     * Builder for creating SchemaCacheEntry instances.
     *
     * <p>This builder provides a fluent API for constructing SchemaCacheEntry objects
     * with named parameters. All setter methods return the builder instance for method chaining.
     *
     * <p>Example usage:
     * <pre>{@code
     * SchemaCacheEntry entry = SchemaCacheEntry.builder()
     *     .localFilename("schema.xsd")
     *     .remoteUrl("https://example.com/schema.xsd")
     *     .downloadTimestamp(Instant.now())
     *     .fileSizeBytes(1024)
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private String localFilename;
        private String remoteUrl;
        private Instant downloadTimestamp;
        private long fileSizeBytes;
        private String md5Hash;
        private String sha256Hash;
        private HttpInfo http;
        private SchemaInfo schema;
        private UsageInfo usage;

        /**
         * Creates a new Builder instance with default values.
         *
         * <p>All fields are initially set to their default values (null for objects,
         * 0 for primitives). Use the fluent setter methods to configure the builder
         * before calling {@link #build()}.
         */
        public Builder() {
            // Default constructor - all fields initialized to default values
        }

        /**
         * Sets the local filename for the cached schema.
         *
         * @param localFilename the name of the file in the local cache
         * @return this builder instance for method chaining
         */
        public Builder localFilename(String localFilename) {
            this.localFilename = localFilename;
            return this;
        }

        /**
         * Sets the remote URL from which the schema was downloaded.
         *
         * @param remoteUrl the original URL of the schema
         * @return this builder instance for method chaining
         */
        public Builder remoteUrl(String remoteUrl) {
            this.remoteUrl = remoteUrl;
            return this;
        }

        /**
         * Sets the timestamp when the schema was downloaded.
         *
         * @param downloadTimestamp the download timestamp
         * @return this builder instance for method chaining
         */
        public Builder downloadTimestamp(Instant downloadTimestamp) {
            this.downloadTimestamp = downloadTimestamp;
            return this;
        }

        /**
         * Sets the file size of the cached schema in bytes.
         *
         * @param fileSizeBytes the file size in bytes
         * @return this builder instance for method chaining
         */
        public Builder fileSizeBytes(long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
            return this;
        }

        /**
         * Sets the MD5 hash of the cached schema file.
         *
         * @param md5Hash the MD5 hash as a hexadecimal string
         * @return this builder instance for method chaining
         */
        public Builder md5Hash(String md5Hash) {
            this.md5Hash = md5Hash;
            return this;
        }

        /**
         * Sets the SHA-256 hash of the cached schema file.
         *
         * @param sha256Hash the SHA-256 hash as a hexadecimal string
         * @return this builder instance for method chaining
         */
        public Builder sha256Hash(String sha256Hash) {
            this.sha256Hash = sha256Hash;
            return this;
        }

        /**
         * Sets the HTTP response information captured during download.
         *
         * @param http the HTTP response information
         * @return this builder instance for method chaining
         */
        public Builder http(HttpInfo http) {
            this.http = http;
            return this;
        }

        /**
         * Sets the schema-specific information extracted from the XSD file.
         *
         * @param schema the schema information
         * @return this builder instance for method chaining
         */
        public Builder schema(SchemaInfo schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Sets the usage statistics for the cached schema.
         *
         * @param usage the usage statistics
         * @return this builder instance for method chaining
         */
        public Builder usage(UsageInfo usage) {
            this.usage = usage;
            return this;
        }

        /**
         * Builds a new SchemaCacheEntry instance with the configured values.
         *
         * <p>If no usage information was provided, a default UsageInfo will be created
         * using {@link UsageInfo#initial()}.
         *
         * @return a new SchemaCacheEntry instance
         */
        public SchemaCacheEntry build() {
            return new SchemaCacheEntry(
                    localFilename,
                    remoteUrl,
                    downloadTimestamp,
                    fileSizeBytes,
                    md5Hash,
                    sha256Hash,
                    http,
                    schema,
                    usage != null ? usage : UsageInfo.initial()
            );
        }
    }

    /**
     * Creates a new builder instance for constructing SchemaCacheEntry objects.
     *
     * <p>This factory method returns a new {@link Builder} that can be used to
     * configure and create a SchemaCacheEntry using a fluent API.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
