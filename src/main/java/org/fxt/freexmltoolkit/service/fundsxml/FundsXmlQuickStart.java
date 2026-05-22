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

package org.fxt.freexmltoolkit.service.fundsxml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper for the "Open Quick Start" action shown after a successful FundsXML download.
 *
 * <p>Picks a representative sample-XML from the FundsXML examples cache so the user has
 * something concrete to look at instead of an empty editor. The current heuristic is
 * "smallest {@code .xml} file" — small samples are typically the most readable and
 * least intimidating starting point.
 */
public final class FundsXmlQuickStart {

    private static final Logger logger = LogManager.getLogger(FundsXmlQuickStart.class);

    private FundsXmlQuickStart() {
    }

    /**
     * Locates the smallest XML sample under the active FundsXML examples cache.
     *
     * @return the chosen sample as a {@link File}, or empty if no sample is available
     */
    public static Optional<File> findStarterSample() {
        return findStarterSample(FundsXmlCache.getInstance().getExamplesDir());
    }

    /**
     * Variant for unit tests / non-default cache locations.
     */
    public static Optional<File> findStarterSample(Path examplesDir) {
        if (examplesDir == null || !Files.isDirectory(examplesDir)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(examplesDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(FundsXmlQuickStart::isXml)
                    .min(Comparator.comparingLong(FundsXmlQuickStart::size))
                    .map(Path::toFile);
        } catch (IOException e) {
            logger.warn("Failed to scan {} for Quick Start sample: {}", examplesDir, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isXml(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
