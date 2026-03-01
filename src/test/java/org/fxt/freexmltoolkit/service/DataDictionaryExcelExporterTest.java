package org.fxt.freexmltoolkit.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("DataDictionaryExcelExporter Tests")
public class DataDictionaryExcelExporterTest {

    private DataDictionaryExcelExporter exporter;
    private XsdDocumentationData mockDocsData;
    private XsdDocumentationHtmlService mockHtmlService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockDocsData = Mockito.mock(XsdDocumentationData.class);
        mockHtmlService = Mockito.mock(XsdDocumentationHtmlService.class);
        
        // Setup basic mock behavior
        when(mockDocsData.getGlobalElements()).thenReturn(Collections.emptyList());
        when(mockDocsData.getGlobalComplexTypes()).thenReturn(Collections.emptyList());
        when(mockDocsData.getGlobalSimpleTypes()).thenReturn(Collections.emptyList());
        
        exporter = new DataDictionaryExcelExporter(mockDocsData, mockHtmlService);
    }

    @Test
    @DisplayName("Sollte Excel-Datei mit Basis-Struktur erstellen")
    void testBasicExport() throws IOException {
        File outputFile = tempDir.resolve("test_export.xlsx").toFile();
        List<XsdExtendedElement> elements = new ArrayList<>();
        
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementName("TestElement");
        element.setElementType("xs:string");
        element.setLevel(1);
        
        // Mock currentNode to make isMandatory return true (default for elements)
        org.w3c.dom.Node mockNode = Mockito.mock(org.w3c.dom.Node.class);
        element.setCurrentNode(mockNode);
        
        elements.add(element);

        exporter.exportToExcel(outputFile, elements);

        assertTrue(outputFile.exists());
        
        try (FileInputStream fis = new FileInputStream(outputFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            assertNotNull(workbook.getSheet("Schema Info"));
            assertNotNull(workbook.getSheet("DEFAULT"));
            
            Sheet defaultSheet = workbook.getSheet("DEFAULT");
            assertEquals("TestElement", defaultSheet.getRow(1).getCell(2).getStringCellValue());
            // isMandatory() returns true if minOccurs is not set (default 1)
            assertEquals("Yes", defaultSheet.getRow(1).getCell(4).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Sollte mehrere Sprach-Sheets erstellen")
    void testMultiLanguageExport() throws IOException {
        File outputFile = tempDir.resolve("multi_lang_export.xlsx").toFile();
        List<XsdExtendedElement> elements = new ArrayList<>();
        
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementName("LocalizedElement");
        
        DocumentationInfo docDe = new DocumentationInfo("DE", "Deutscher Text");
        DocumentationInfo docEn = new DocumentationInfo("EN", "English Text");
        element.setDocumentations(Arrays.asList(docDe, docEn));
        
        elements.add(element);

        exporter.exportToExcel(outputFile, elements);

        try (FileInputStream fis = new FileInputStream(outputFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            assertNotNull(workbook.getSheet("DE"));
            assertNotNull(workbook.getSheet("EN"));
            
            Sheet deSheet = workbook.getSheet("DE");
            assertTrue(deSheet.getRow(1).getCell(8).getStringCellValue().contains("Deutscher Text"));
            
            Sheet enSheet = workbook.getSheet("EN");
            assertTrue(enSheet.getRow(1).getCell(8).getStringCellValue().contains("English Text"));
        }
    }

    @Test
    @DisplayName("Sollte Sprachen filtern")
    void testFilteredLanguageExport() throws IOException {
        File outputFile = tempDir.resolve("filtered_export.xlsx").toFile();
        List<XsdExtendedElement> elements = new ArrayList<>();
        
        XsdExtendedElement element = new XsdExtendedElement();
        element.setDocumentations(Arrays.asList(
            new DocumentationInfo("DE", "Deutsch"),
            new DocumentationInfo("FR", "Francais")
        ));
        elements.add(element);

        Set<String> filter = new HashSet<>(Collections.singletonList("DE"));
        exporter.exportToExcel(outputFile, elements, filter);

        try (FileInputStream fis = new FileInputStream(outputFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            assertNotNull(workbook.getSheet("DE"));
            assertNull(workbook.getSheet("FR"));
        }
    }

    @Test
    @DisplayName("Sollte HTML-Tags aus Dokumentation entfernen")
    void testStripHtml() throws IOException {
        File outputFile = tempDir.resolve("html_strip_test.xlsx").toFile();
        List<XsdExtendedElement> elements = new ArrayList<>();
        
        XsdExtendedElement element = new XsdExtendedElement();
        element.setDocumentations(Collections.singletonList(
            new DocumentationInfo("default", "<b>Bold</b> &amp; <i>Italic</i>")
        ));
        elements.add(element);

        exporter.exportToExcel(outputFile, elements);

        try (FileInputStream fis = new FileInputStream(outputFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("DEFAULT");
            String content = sheet.getRow(1).getCell(8).getStringCellValue();
            assertEquals("Bold & Italic", content);
        }
    }
}
