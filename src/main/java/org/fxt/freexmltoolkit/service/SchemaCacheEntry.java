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
         */
        public static HttpInfo of(int statusCode, long downloadDurationMs) {
            return new HttpInfo(statusCode, null, null, null, null, downloadDurationMs);
        }
    }

    /**
     * Schema-specific information extracted from the XSD file.
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
         */
        public static SchemaInfo empty() {
            return new SchemaInfo(null, null, List.of(), List.of(), List.of());
        }

        /**
         * Returns a copy with the specified targetNamespace.
         */
        public SchemaInfo withTargetNamespace(String namespace) {
            return new SchemaInfo(namespace, xsdVersion, imports, includes, redefines);
        }

        /**
         * Returns a copy with the specified XSD version.
         */
        public SchemaInfo withXsdVersion(String version) {
            return new SchemaInfo(targetNamespace, version, imports, includes, redefines);
        }
    }

    /**
     * Usage statistics for the cached schema.
     */
    public record UsageInfo(
            Instant lastAccessTimestamp,
            long accessCount,
            List<String> referencedBy
    ) {
        /**
         * Creates initial usage info for a newly cached schema.
         */
        public static UsageInfo initial() {
            return new UsageInfo(Instant.now(), 1, new ArrayList<>());
        }

        /**
         * Returns a copy with incremented access count and updated timestamp.
         */
        public UsageInfo withAccessRecorded() {
            return new UsageInfo(Instant.now(), accessCount + 1, referencedBy);
        }

        /**
         * Returns a copy with an additional referencing schema.
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

        public Builder localFilename(String localFilename) {
            this.localFilename = localFilename;
            return this;
        }

        public Builder remoteUrl(String remoteUrl) {
            this.remoteUrl = remoteUrl;
            return this;
        }

        public Builder downloadTimestamp(Instant downloadTimestamp) {
            this.downloadTimestamp = downloadTimestamp;
            return this;
        }

        public Builder fileSizeBytes(long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
            return this;
        }

        public Builder md5Hash(String md5Hash) {
            this.md5Hash = md5Hash;
            return this;
        }

        public Builder sha256Hash(String sha256Hash) {
            this.sha256Hash = sha256Hash;
            return this;
        }

        public Builder http(HttpInfo http) {
            this.http = http;
            return this;
        }

        public Builder schema(SchemaInfo schema) {
            this.schema = schema;
            return this;
        }

        public Builder usage(UsageInfo usage) {
            this.usage = usage;
            return this;
        }

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
     * Creates a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
}
