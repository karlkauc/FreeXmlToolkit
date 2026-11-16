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

package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * What the extension adds to the application's stores once its panel is opened: analysis queries for the
 * Query Console (tag {@value #TAG}), new-file templates of the four reports (category {@value #CATEGORY})
 * and favorites for the installed schemas (folder {@value #CATEGORY}). Every entry has a fixed id, so
 * registering again updates it.
 */
final class KagExtras {

    private static final Logger logger = LogManager.getLogger(KagExtras.class);

    static final String TAG = "kag-meldewesen";
    static final String CATEGORY = "KAG-Meldewesen";
    static final String SNIPPETS = "/plugins/kagmeldewesen/snippets/snippets.json";
    static final String ID_PREFIX = "kag-meldewesen-";

    /** Template id suffix → example file and name of the four reports. */
    static final Map<String, String[]> TEMPLATES = Map.of(
            "aif", new String[] {"aif-example.xml", "AIFMD AIF file", "ESMA AIFMD reporting v1.2: one AIF record with its principal information"},
            "aifm", new String[] {"aifm-example.xml", "AIFMD AIFM file", "ESMA AIFMD reporting v1.2: one AIFM record"},
            "mmf", new String[] {"mmf-example.xml", "Money market fund report", "ESMA auth.093 in the BizData envelope, one fund report"},
            "derimv", new String[] {"derimv-example.xml", "Derivative risk report (4. DeRiMV)", "FMA Derivatemeldung: one fund per method"});

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private KagExtras() {
    }

    /** A snippet as stored in {@code snippets.json}. */
    static final class Snippet {
        String key;
        String name;
        String description;
        String category;
        String query;
    }

    /** Test hook: the stores are not touched (UI tests would write into the user's templates). */
    static void skipRegistrationForTests() {
        REGISTERED.set(true);
    }

    /** Registers snippets, templates and favorites once per session (later calls do nothing). */
    static void registerOnce(List<Path> schemas) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("kag-extras").start(() -> {
            try {
                registerSnippets(XPathSnippetRepository.getInstance());
                registerTemplates(TemplateRepository.getInstance());
                registerFavorites(FavoritesService.getInstance(), schemas);
            } catch (RuntimeException | LinkageError e) {
                logger.warn("KAG-Meldewesen extras not registered: {}", e.getMessage());
            }
        });
    }

    /** @return the analysis queries of the bundled catalogue */
    static List<Snippet> snippets() {
        try (InputStream in = KagExtras.class.getResourceAsStream(SNIPPETS);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return List.of(new Gson().fromJson(reader, Snippet[].class));
        } catch (IOException | RuntimeException e) {
            logger.warn("KAG snippets unreadable: {}", e.getMessage());
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

    /** Adds (or updates) the new-file templates of the four reports (the bundled examples). */
    static int registerTemplates(TemplateRepository repository) {
        int count = 0;
        for (Map.Entry<String, String[]> t : TEMPLATES.entrySet()) {
            try (InputStream in = KagExtras.class.getResourceAsStream("/plugins/kagmeldewesen/examples/" + t.getValue()[0])) {
                if (in == null) {
                    continue;
                }
                XmlTemplate template = new XmlTemplate();
                template.setId(ID_PREFIX + "template-" + t.getKey());
                template.setName(t.getValue()[1]);
                template.setContent(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                template.setCategory(CATEGORY);
                template.setDescription(t.getValue()[2] + " (fictitious data)");
                template.setBuiltIn(false);
                repository.addTemplate(template, true);
                count++;
            } catch (IOException | RuntimeException e) {
                logger.warn("KAG template {} not registered: {}", t.getKey(), e.getMessage());
            }
        }
        return count;
    }

    /** Adds the installed schemas to the favorites (once each). */
    static int registerFavorites(FavoritesService favorites, List<Path> schemas) {
        Set<String> known = new HashSet<>();
        favorites.getFavoritesByFolder(CATEGORY).forEach(f -> known.add(f.getFilePath()));
        int count = 0;
        for (Path schema : schemas) {
            String path = schema.toAbsolutePath().toString();
            if (Files.isRegularFile(schema) && known.add(path)) {
                favorites.addFavorite(path, schema.getFileName().toString(), CATEGORY);
                count++;
            }
        }
        return count;
    }
}
