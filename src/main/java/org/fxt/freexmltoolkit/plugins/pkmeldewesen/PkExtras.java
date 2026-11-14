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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;

import com.google.gson.Gson;

/**
 * What the extension adds to the application's stores once it is used: analysis queries for the
 * Query Console (tag {@value #TAG}), new-file templates of the three reports (category
 * {@value #CATEGORY}) and favorites for the schemas and the documentation of the taxonomy in use
 * (folder {@value #CATEGORY}). Every entry has a fixed id, so registering again updates it.
 */
public final class PkExtras {

    private static final Logger logger = LogManager.getLogger(PkExtras.class);

    /** Snippet tag, template category and favorites folder. */
    public static final String TAG = "pk-meldewesen";
    public static final String CATEGORY = "PK-Meldewesen";
    static final String SNIPPETS = "/plugins/pkmeldewesen/snippets/snippets.json";
    static final String ID_PREFIX = "pk-meldewesen-";

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private PkExtras() {
    }

    /** A snippet as stored in {@code snippets.json}. */
    static final class Snippet {
        String key;
        String name;
        String description;
        String category;
        String query;
    }

    /** Registers snippets, templates and favorites once per session (later calls do nothing). */
    static void registerOnce(PkTaxonomy taxonomy) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("pk-extras").start(() -> {
            try {
                registerSnippets(XPathSnippetRepository.getInstance());
                registerTemplates(TemplateRepository.getInstance());
                registerFavorites(FavoritesService.getInstance(), taxonomy);
            } catch (RuntimeException | LinkageError e) {
                logger.warn("PK-Meldewesen extras not registered: {}", e.getMessage());
            }
        });
    }

    /** @return the analysis queries of the bundled catalogue */
    static List<Snippet> snippets() {
        try (InputStream in = PkExtras.class.getResourceAsStream(SNIPPETS);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return List.of(new Gson().fromJson(reader, Snippet[].class));
        } catch (IOException | RuntimeException e) {
            logger.warn("PK snippets unreadable: {}", e.getMessage());
            return List.of();
        }
    }

    /** Adds (or updates) the analysis queries. */
    static int registerSnippets(XPathSnippetRepository repository) {
        int count = 0;
        for (Snippet s : snippets()) {
            XPathSnippet snippet = new XPathSnippet(s.name, XPathSnippet.SnippetType.XQUERY,
                    XPathSnippet.SnippetCategory.valueOf(s.category), s.query);
            snippet.setId(ID_PREFIX + s.key);
            snippet.setDescription(s.description);
            snippet.setAuthor("FreeXmlToolkit");
            snippet.setTags(new LinkedHashSet<>(Set.of(TAG)));
            repository.saveSnippet(snippet);
            count++;
        }
        return count;
    }

    /** Adds (or updates) the new-file templates of the three reports. */
    static int registerTemplates(TemplateRepository repository) {
        int count = 0;
        for (PkModule module : PkModule.values()) {
            String resource = "/plugins/pkmeldewesen/templates/pk-" + module.code() + ".xml";
            try (InputStream in = PkExtras.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                XmlTemplate template = new XmlTemplate();
                template.setId(ID_PREFIX + "template-" + module.code());
                template.setName(module.shortName() + " (" + module.code() + ") – " + module.title());
                template.setContent(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                template.setCategory(CATEGORY);
                template.setDescription("FMA " + module.title() + ": root element and the master data of the total VRG (PK.000.01)");
                template.setBuiltIn(false);
                repository.addTemplate(template, true);
                count++;
            } catch (IOException | RuntimeException e) {
                logger.warn("PK template {} not registered: {}", module.code(), e.getMessage());
            }
        }
        return count;
    }

    /** Adds the schemas and the documentation of the taxonomy in use to the favorites (once each). */
    static int registerFavorites(FavoritesService favorites, PkTaxonomy taxonomy) {
        List<Path> files = new ArrayList<>();
        for (PkModule module : PkModule.values()) {
            Path schema = taxonomy.activeSchema(module);
            if (schema != null) {
                files.add(schema);
            }
        }
        Path pdf = taxonomy.documentation(taxonomy.activeVersion());
        if (pdf != null) {
            files.add(pdf);
        }
        Set<String> known = new java.util.HashSet<>();
        favorites.getFavoritesByFolder(CATEGORY).forEach(f -> known.add(f.getFilePath()));
        int count = 0;
        for (Path file : files) {
            String path = file.toAbsolutePath().toString();
            if (known.add(path)) {
                String name = file.getFileName().toString();
                for (PkModule module : PkModule.values()) {
                    if (name.equals("pk_meldung_" + module.code() + ".xsd")) {
                        name = "FMA schema " + module.shortName() + " (" + module.code() + ")";
                    }
                }
                favorites.addFavorite(path, name, CATEGORY);
                count++;
            }
        }
        return count;
    }
}
