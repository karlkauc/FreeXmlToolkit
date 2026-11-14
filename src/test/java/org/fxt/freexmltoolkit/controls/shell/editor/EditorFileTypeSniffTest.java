package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.telemetry.FileExtensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Content detection for files of unknown extension, and the extension as telemetry value. */
class EditorFileTypeSniffTest {

    @Test
    void xmlJsonAndHtmlAreRecognisedByTheirStart() {
        assertEquals(EditorFileType.XML, EditorFileType.sniff("﻿<?xml version=\"1.0\"?><a/>"));
        assertEquals(EditorFileType.XML, EditorFileType.sniff("\n  <configuration>"));
        assertEquals(EditorFileType.XML, EditorFileType.sniff("<!-- generated -->\n<root/>"));
        assertEquals(EditorFileType.XML, EditorFileType.sniff("<!DOCTYPE note SYSTEM \"note.dtd\"><note/>"));
        assertEquals(EditorFileType.HTML, EditorFileType.sniff("<!doctype html><html>"));
        assertEquals(EditorFileType.JSON, EditorFileType.sniff("{\n  \"type\": \"FeatureCollection\"}"));
        assertEquals(EditorFileType.JSON, EditorFileType.sniff("{}"));
    }

    @Test
    void plainTextStaysText() {
        assertEquals(EditorFileType.OTHER, EditorFileType.sniff("a < b and so on"));
        assertEquals(EditorFileType.OTHER, EditorFileType.sniff("< not a tag"));
        assertEquals(EditorFileType.OTHER, EditorFileType.sniff("{{ template }}"));
        assertEquals(EditorFileType.OTHER, EditorFileType.sniff("[section]\nkey=value"));
        assertEquals(EditorFileType.OTHER, EditorFileType.sniff("   "));
        assertEquals(EditorFileType.OTHER, EditorFileType.sniff(null));
    }

    @Test
    void filesAreReadWithTheirBom(@TempDir Path tmp) throws Exception {
        Path utf16 = tmp.resolve("app.config");
        Files.write(utf16, "﻿<configuration/>".getBytes(StandardCharsets.UTF_16BE));
        assertEquals(EditorFileType.XML, EditorFileType.sniffFile(utf16));
        assertEquals(EditorFileType.OTHER, EditorFileType.sniffFile(tmp.resolve("missing.cfg")));
    }

    @Test
    void documentsTakeTheContentTypeOnlyForUnknownExtensions() {
        assertEquals(EditorFileType.XML, OpenDocument.forPath(Path.of("web.config"), EditorFileType.XML).getFileType());
        assertTrue(OpenDocument.forPath(Path.of("web.config"), EditorFileType.XML).isTypeFromContent());
        assertEquals(EditorFileType.JSON, OpenDocument.forPath(Path.of("a.json"), EditorFileType.XML).getFileType());
        assertEquals(EditorFileType.OTHER, OpenDocument.forPath(Path.of("notes.txt")).getFileType());
    }

    @Test
    void extensionsAreReportedOnlyWhenWellKnown() {
        assertEquals("config", FileExtensions.telemetryValue("Web.Config"));
        assertEquals("gpx", FileExtensions.telemetryValue("track.gpx"));
        assertEquals("other", FileExtensions.telemetryValue("report.mueller"), "unknown extensions never leak");
        assertEquals("none", FileExtensions.telemetryValue("Dockerfile"));
        assertEquals("none", FileExtensions.telemetryValue(".bashrc"));
        assertEquals("none", FileExtensions.telemetryValue(null));
    }

    @Test
    void contentKindIsReportedForUnknownExtensions() {
        assertEquals("xml", EditorFileSync.contentKind(OpenDocument.forPath(Path.of("a.config"), EditorFileType.XML)));
        assertEquals("text", EditorFileSync.contentKind(OpenDocument.forPath(Path.of("a.log"), EditorFileType.OTHER)));
        assertNull(EditorFileSync.contentKind(OpenDocument.forPath(Path.of("a.xml"))));
    }
}
