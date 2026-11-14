/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Copies a set of bundled schema files (which include each other by relative paths) into a folder of
 * the user's store, {@code ~/.freeXmlToolkit/<extension>/<set>/} ({@code -Dfxt.regreport.dir}
 * replaces {@code ~/.freeXmlToolkit}), so the Schema Library, validation and the documentation
 * generator can read them as files. Existing files are kept.
 */
public final class BundledSchemas {

    private static final Logger logger = LogManager.getLogger(BundledSchemas.class);

    private BundledSchemas() {
    }

    /** @return the store of an extension */
    public static Path store(String extensionId) {
        String override = System.getProperty("fxt.regreport.dir");
        Path base = override != null && !override.isBlank() ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".freeXmlToolkit");
        return base.resolve(extensionId);
    }

    /**
     * @param resourceDir the resource folder ({@code /plugins/kagmeldewesen/aifmd})
     * @param files       the file names in it
     * @return the folder holding the copies
     */
    public static Path install(String extensionId, String set, String resourceDir, List<String> files) {
        Path dir = store(extensionId).resolve(set);
        try {
            Files.createDirectories(dir);
            for (String name : files) {
                Path target = dir.resolve(name);
                if (Files.isRegularFile(target)) {
                    continue;
                }
                try (InputStream in = BundledSchemas.class.getResourceAsStream(resourceDir + "/" + name)) {
                    if (in == null) {
                        logger.warn("Bundled schema {} is missing", name);
                        continue;
                    }
                    Path temp = Files.createTempFile(dir, "schema", ".part");
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not install the bundled schemas of {}: {}", extensionId, e.getMessage());
        }
        return dir;
    }
}
