/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

package org.fxt.freexmltoolkit.controls.v2.common.utilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Unified backup utility for file operations across serializers.
 *
 * <p>Provides a centralized way to create timestamped backups of files,
 * supporting both separate backup directories and inline backups.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class BackupUtility {
    private static final Logger logger = LogManager.getLogger(BackupUtility.class);
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private BackupUtility() {
        // Utility class - no instantiation
    }

    /**
     * Creates a timestamped backup of a file.
     *
     * <p>The backup directory is determined by PropertiesService settings:
     * - If backup uses separate directory, creates backup in configured backup directory
     * - Otherwise, backups are created in the same directory as the original file</p>
     *
     * @param filePath the file to backup
     * @return the path to the backup file, or null if file doesn't exist
     * @throws IOException if backup creation fails
     */
    public static Path createBackup(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            logger.warn("Cannot create backup: file does not exist: {}", filePath);
            return null;
        }

        // Determine backup directory based on settings
        Path backupDir;
        PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);

        if (propertiesService.isBackupUseSeparateDirectory()) {
            backupDir = Path.of(propertiesService.getBackupDirectory());
            // Auto-create the backup directory if it doesn't exist
            Files.createDirectories(backupDir);
            logger.debug("Using separate backup directory: {}", backupDir);
        } else {
            backupDir = filePath.getParent();
        }

        // Create backup filename with timestamp
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
        String fileName = filePath.getFileName().toString();
        String backupFileName = fileName.replaceFirst("(\\.[^.]+)$", "_backup_" + timestamp + "$1");

        Path backupPath = backupDir.resolve(backupFileName);
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Created backup: {}", backupPath);
        return backupPath;
    }

    /**
     * Creates a timestamped backup of a file from a string path.
     *
     * @param filePath the file path as string
     * @return the path to the backup file, or null if file doesn't exist
     * @throws IOException if backup creation fails
     */
    public static Path createBackup(String filePath) throws IOException {
        return createBackup(Path.of(filePath));
    }
}
