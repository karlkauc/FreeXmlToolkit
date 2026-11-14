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

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The installed taxonomies of the PK-Meldewesen: one folder per version under
 * {@code ~/.freeXmlToolkit/pk-meldewesen/} (or {@code -Dfxt.pk.dir}) holding the FMA's XSDs,
 * workbooks and documentation. The version shipped with the application is copied there on
 * first use; newer ones are imported from the FMA's download ZIP.
 */
public final class PkTaxonomy {

    private static final Logger logger = LogManager.getLogger(PkTaxonomy.class);

    /** The version folder of the bundled taxonomy. */
    public static final String BUNDLED_VERSION = "2.9.0-hotfix2";

    /** The bundled files (resource folder {@code /plugins/pkmeldewesen/taxonomy/<version>/}). */
    static final List<String> BUNDLED_FILES = List.of(
            "pk_meldung_pfq_2.9.0_Hotfix2.xsd", "pk_meldung_pfa_2.9.0_Hotfix2.xsd", "pk_meldung_pfe_2.9.0_Hotfix2.xsd",
            "PK-Meldewesen_Dokumentation 2.9.0_Hotfix2.pdf",
            "PK-Meldewesen_Business-Templates 2.9.0_Hotfix2.xlsx", "PK-Meldewesen_Dictionary 2.9.0_Hotfix2.xlsx",
            "PK-Meldewesen_Positionsbeschreibung 2.9.0_Hotfix2.xlsx", "PK-Meldewesen_Validierungen_2.9.0_Hotfix2.xlsx",
            "SOURCE.txt");

    private final Path baseDir;

    /** @param baseDir the folder holding one sub-folder per version */
    public PkTaxonomy(Path baseDir) {
        this.baseDir = baseDir;
    }

    /** @return the store in the user's folder ({@code -Dfxt.pk.dir} overrides it) */
    public static PkTaxonomy shared() {
        String override = System.getProperty("fxt.pk.dir");
        return new PkTaxonomy(override != null && !override.isBlank() ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "pk-meldewesen"));
    }

    /** @return the folder holding the versions */
    public Path baseDir() {
        return baseDir;
    }

    /**
     * Copies the bundled taxonomy into the store unless it is there already (cheap when it is).
     *
     * @return the folder of the bundled version
     */
    public Path ensureBundled() {
        Path dir = baseDir.resolve(BUNDLED_VERSION);
        try {
            Files.createDirectories(dir);
            for (String name : BUNDLED_FILES) {
                Path target = dir.resolve(name);
                if (Files.isRegularFile(target)) {
                    continue;
                }
                try (InputStream in = PkTaxonomy.class.getResourceAsStream(
                        "/plugins/pkmeldewesen/taxonomy/" + BUNDLED_VERSION + "/" + name)) {
                    if (in == null) {
                        logger.warn("Bundled PK taxonomy file {} is missing", name);
                        continue;
                    }
                    Path temp = Files.createTempFile(dir, "pk", ".part");
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not install the bundled PK taxonomy: {}", e.getMessage());
        }
        return dir;
    }

    /** @return the installed versions, newest first */
    public List<String> versions() {
        List<String> versions = new ArrayList<>();
        if (Files.isDirectory(baseDir)) {
            try (Stream<Path> dirs = Files.list(baseDir)) {
                dirs.filter(d -> Files.isDirectory(d) && schemaIn(d, PkModule.PFQ) != null)
                        .map(d -> d.getFileName().toString()).forEach(versions::add);
            } catch (IOException e) {
                logger.debug("Could not list PK taxonomies: {}", e.getMessage());
            }
        }
        versions.sort(VERSION_ORDER.reversed());
        return versions;
    }

    /** @return the version in use: the chosen one when installed, else the newest, else the bundled one */
    public String activeVersion() {
        String chosen = PkSettings.get(PkSettings.TAXONOMY_VERSION, "");
        List<String> versions = versions();
        if (!chosen.isEmpty() && versions.contains(chosen)) {
            return chosen;
        }
        return versions.isEmpty() ? BUNDLED_VERSION : versions.getFirst();
    }

    /** @return the folder of a version */
    public Path dir(String version) {
        return baseDir.resolve(version);
    }

    /**
     * @param version a version folder name
     * @param module  a report kind
     * @return the module's XSD in that version, or {@code null}
     */
    public Path schema(String version, PkModule module) {
        return schemaIn(dir(version), module);
    }

    /** @return the documentation PDF of a version, or {@code null} */
    public Path documentation(String version) {
        return firstMatching(dir(version), name -> name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    /**
     * @param version a version folder name
     * @param kind    a word in the workbook's name ({@code Positionsbeschreibung}, {@code Dictionary}, …)
     * @return the workbook of a version, or {@code null}
     */
    public Path workbook(String version, String kind) {
        String word = kind.toLowerCase(Locale.ROOT);
        return firstMatching(dir(version), name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".xlsx") && lower.contains(word);
        });
    }

    private static Path schemaIn(Path dir, PkModule module) {
        String prefix = "pk_meldung_" + module.code() + "_";
        return firstMatching(dir, name -> name.toLowerCase(Locale.ROOT).startsWith(prefix) && name.endsWith(".xsd"));
    }

    private static Path firstMatching(Path dir, java.util.function.Predicate<String> nameTest) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> nameTest.test(f.getFileName().toString())).sorted().findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Orders version folders like {@code 2.9.0-hotfix2} numerically, then by their suffix. */
    static final Comparator<String> VERSION_ORDER = (a, b) -> {
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            String x = i < pa.length ? pa[i] : "";
            String y = i < pb.length ? pb[i] : "";
            int c = x.matches("\\d+") && y.matches("\\d+")
                    ? Integer.compare(Integer.parseInt(x), Integer.parseInt(y))
                    : naturalCompare(x, y);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    };

    private static int naturalCompare(String x, String y) {
        String dx = x.replaceAll("\\D", "");
        String dy = y.replaceAll("\\D", "");
        String sx = x.replaceAll("\\d", "");
        String sy = y.replaceAll("\\d", "");
        int c = sx.compareToIgnoreCase(sy);
        if (c != 0 || dx.isEmpty() || dy.isEmpty()) {
            return c != 0 ? c : Integer.compare(dx.length(), dy.length());
        }
        return Long.compare(Long.parseLong(dx), Long.parseLong(dy));
    }
}
