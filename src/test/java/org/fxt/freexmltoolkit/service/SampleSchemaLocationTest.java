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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The schema reference a generated sample carries must be portable: a plain file name for an
 * unsaved document, a relative path for a known output folder, and a relative path once the
 * sample is saved somewhere else than next to its schema.
 */
class SampleSchemaLocationTest {

    @TempDir
    Path dir;

    @Test
    void unsavedSampleReferencesTheSchemaByFileName() throws Exception {
        Path xsd = dir.resolve("xsd").resolve("Order.xsd");
        Files.createDirectories(xsd.getParent());
        Files.writeString(xsd, "<x/>");

        // The path may be unnormalized (e.g. opened via ../xsd/ from a sibling folder)
        String unnormalized = dir.resolve("xml").resolve("..").resolve("xsd").resolve("Order.xsd").toString();
        assertEquals("Order.xsd", SampleSchemaLocation.forSample(unnormalized, null));
    }

    @Test
    void knownOutputFolderGetsARelativePathWithForwardSlashes() throws Exception {
        Path xsd = dir.resolve("xsd").resolve("Order.xsd");
        Path out = dir.resolve("out").resolve("batch");
        Files.createDirectories(xsd.getParent());
        Files.createDirectories(out);

        assertEquals("../../xsd/Order.xsd", SampleSchemaLocation.forSample(xsd.toString(), out));
        assertEquals("Order.xsd", SampleSchemaLocation.forSample(xsd.toString(), xsd.getParent()),
                "a sample next to its schema references it by name");
    }

    @Test
    void relocateRewritesANoNamespaceReferenceOnFirstSave() throws Exception {
        Path xsd = dir.resolve("xsd").resolve("Order.xsd");
        Path target = dir.resolve("xml");
        Files.createDirectories(xsd.getParent());
        Files.createDirectories(target);
        Files.writeString(xsd, "<x/>");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- generated -->
                <Order xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="Order.xsd" xmlns:ds="urn:ds">
                  <Item xsi:nil="true"/>
                </Order>
                """;

        Optional<String> relocated = SampleSchemaLocation.relocate(xml, xsd.toFile(), target);

        assertTrue(relocated.isPresent());
        assertTrue(relocated.get().contains("xsi:noNamespaceSchemaLocation=\"../xsd/Order.xsd\""), relocated.get());
        assertTrue(relocated.get().contains("<Item xsi:nil=\"true\"/>"), "the body is untouched");
    }

    @Test
    void relocateRewritesOnlyTheLocationHalfOfASchemaLocationPair() throws Exception {
        Path xsd = dir.resolve("schemas").resolve("Order.xsd");
        Path target = dir.resolve("data").resolve("in");
        Files.createDirectories(xsd.getParent());
        Files.createDirectories(target);
        Files.writeString(xsd, "<x/>");
        String xml = "<o:Order xmlns:o=\"urn:o\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation='urn:o Order.xsd'/>";

        String relocated = SampleSchemaLocation.relocate(xml, xsd.toFile(), target).orElseThrow();

        assertTrue(relocated.contains("xsi:schemaLocation='urn:o ../../schemas/Order.xsd'"), relocated);
    }

    @Test
    void relocateLeavesAReferenceAloneWhenItAlreadyResolvesOrIsNotTheSchemasName() throws Exception {
        Path xsd = dir.resolve("Order.xsd");
        Files.writeString(xsd, "<x/>");
        String nextToSchema = "<Order xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:noNamespaceSchemaLocation=\"Order.xsd\"/>";
        assertTrue(SampleSchemaLocation.relocate(nextToSchema, xsd.toFile(), dir).isEmpty(),
                "saved next to the schema, the bare name is already right");

        Path elsewhere = dir.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        String foreign = "<Order xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:noNamespaceSchemaLocation=\"http://example.com/Order.xsd\"/>";
        assertTrue(SampleSchemaLocation.relocate(foreign, xsd.toFile(), elsewhere).isEmpty(),
                "a hand-written reference is never touched");
        assertTrue(SampleSchemaLocation.relocate("<Order/>", xsd.toFile(), elsewhere).isEmpty());
        assertTrue(SampleSchemaLocation.relocate(nextToSchema, null, elsewhere).isEmpty());
    }
}
