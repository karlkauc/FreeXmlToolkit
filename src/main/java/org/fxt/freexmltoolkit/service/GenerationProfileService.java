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

package org.fxt.freexmltoolkit.service;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.GenerationProfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Service for persisting and loading {@link GenerationProfile} configurations.
 * Profiles are stored as individual JSON files in ~/.freeXmlToolkit/generation-profiles/.
 */
public class GenerationProfileService {

    private static final Logger logger = LogManager.getLogger(GenerationProfileService.class);
    private static GenerationProfileService instance;

    private final Path profilesDir;
    private final Gson gson;

    GenerationProfileService(Path profilesDir) {
        this.profilesDir = profilesDir;
        try {
            Files.createDirectories(profilesDir);
        } catch (IOException e) {
            logger.error("Failed to create profiles directory: {}", profilesDir, e);
        }
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
    }

    private GenerationProfileService() {
        this(Paths.get(System.getProperty("user.home"), ".freeXmlToolkit", "generation-profiles"));
    }

    public static synchronized GenerationProfileService getInstance() {
        if (instance == null) {
            instance = new GenerationProfileService();
        }
        return instance;
    }

    /**
     * Saves a profile to disk. Updates the updatedAt timestamp.
     */
    public void save(GenerationProfile profile) {
        if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
            throw new IllegalArgumentException("Profile must have a non-blank name");
        }
        profile.touch();
        Path file = profilesDir.resolve(sanitizeFileName(profile.getName()) + ".json");
        try {
            String json = gson.toJson(profile);
            Files.writeString(file, json);
            logger.info("Saved profile '{}' to {}", profile.getName(), file);
        } catch (IOException e) {
            logger.error("Failed to save profile '{}'", profile.getName(), e);
            throw new RuntimeException("Failed to save profile: " + profile.getName(), e);
        }
    }

    /**
     * Loads a profile by name.
     *
     * @return the profile, or null if not found
     */
    public GenerationProfile load(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Path file = profilesDir.resolve(sanitizeFileName(name) + ".json");
        if (!Files.exists(file)) {
            logger.debug("Profile '{}' not found at {}", name, file);
            return null;
        }
        try {
            String json = Files.readString(file);
            return gson.fromJson(json, GenerationProfile.class);
        } catch (IOException e) {
            logger.error("Failed to load profile '{}'", name, e);
            return null;
        }
    }

    /**
     * Loads all profiles from the profiles directory.
     */
    public List<GenerationProfile> loadAll() {
        List<GenerationProfile> profiles = new ArrayList<>();
        if (!Files.exists(profilesDir)) {
            return profiles;
        }
        try (Stream<Path> files = Files.list(profilesDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            String json = Files.readString(p);
                            GenerationProfile profile = gson.fromJson(json, GenerationProfile.class);
                            if (profile != null) {
                                profiles.add(profile);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to load profile from {}", p, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to list profiles directory", e);
        }
        return profiles;
    }

    /**
     * Deletes a profile by name.
     *
     * @return true if deleted, false if not found
     */
    public boolean delete(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Path file = profilesDir.resolve(sanitizeFileName(name) + ".json");
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                logger.info("Deleted profile '{}'", name);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("Failed to delete profile '{}'", name, e);
            return false;
        }
    }

    /**
     * Duplicates a profile with a new name.
     *
     * @return the duplicated profile, or null if source not found
     */
    public GenerationProfile duplicate(String sourceName, String newName) {
        GenerationProfile source = load(sourceName);
        if (source == null) {
            return null;
        }
        GenerationProfile copy = source.deepCopy();
        copy.setName(newName);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        save(copy);
        return copy;
    }

    /**
     * Exports a profile to an arbitrary file location.
     */
    public void exportToFile(GenerationProfile profile, File target) throws IOException {
        String json = gson.toJson(profile);
        Files.writeString(target.toPath(), json);
        logger.info("Exported profile '{}' to {}", profile.getName(), target);
    }

    /**
     * Imports a profile from a file.
     */
    public GenerationProfile importFromFile(File source) throws IOException {
        String json = Files.readString(source.toPath());
        return gson.fromJson(json, GenerationProfile.class);
    }

    /**
     * Returns the profiles directory path.
     */
    public Path getProfilesDir() {
        return profilesDir;
    }

    static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>,
            JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}
