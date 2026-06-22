package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link XsdDocumentationHtmlService#faviconMimeType(String)} maps favicon
 * file extensions to the correct {@code <link rel="icon">} MIME type. A hardcoded
 * {@code image/x-icon} previously prevented PNG (and other non-ICO) favicons from rendering.
 */
class XsdDocumentationFaviconMimeTypeTest {

    @Test
    void pngMapsToImagePng() {
        assertEquals("image/png", XsdDocumentationHtmlService.faviconMimeType("logo.png"));
    }

    @Test
    void gifMapsToImageGif() {
        assertEquals("image/gif", XsdDocumentationHtmlService.faviconMimeType("logo.gif"));
    }

    @Test
    void jpgAndJpegMapToImageJpeg() {
        assertEquals("image/jpeg", XsdDocumentationHtmlService.faviconMimeType("logo.jpg"));
        assertEquals("image/jpeg", XsdDocumentationHtmlService.faviconMimeType("logo.jpeg"));
    }

    @Test
    void svgMapsToImageSvgXml() {
        assertEquals("image/svg+xml", XsdDocumentationHtmlService.faviconMimeType("logo.svg"));
    }

    @Test
    void icoMapsToImageXIcon() {
        assertEquals("image/x-icon", XsdDocumentationHtmlService.faviconMimeType("favicon.ico"));
    }

    @Test
    void unknownExtensionFallsBackToImageXIcon() {
        assertEquals("image/x-icon", XsdDocumentationHtmlService.faviconMimeType("favicon.bin"));
    }

    @Test
    void extensionMatchingIsCaseInsensitive() {
        assertEquals("image/png", XsdDocumentationHtmlService.faviconMimeType("LOGO.PNG"));
        assertEquals("image/jpeg", XsdDocumentationHtmlService.faviconMimeType("Photo.JPEG"));
    }
}
