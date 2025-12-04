package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MultiFileXsdSerializer.
 * Focuses on nested xs:include serialization.
 *
 * @since 2.0
 */
class MultiFileXsdSerializerTest {

    private MultiFileXsdSerializer serializer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        serializer = new MultiFileXsdSerializer();
        serializer.setCreateBackups(false);
    }

    @Test
    @DisplayName("saveAll() should preserve xs:include in nested included files")
    void testNestedIncludesSerialization() throws IOException {
        // Create directory structure for nested includes
        // main.xsd -> includes middle.xsd -> includes base.xsd
        Path mainFile = tempDir.resolve("main.xsd");
        Path middleFile = tempDir.resolve("middle.xsd");
        Path baseFile = tempDir.resolve("base.xsd");

        // Create the schema model with proper source info
        XsdSchema schema = new XsdSchema();
        schema.setMainSchemaPath(mainFile);
        schema.getNamespaces().put("xs", "http://www.w3.org/2001/XMLSchema");
        schema.setTargetNamespace("http://example.com/test");

        // XsdInclude from main.xsd to middle.xsd
        XsdInclude includeMiddle = new XsdInclude();
        includeMiddle.setSchemaLocation("middle.xsd");
        includeMiddle.setSourceInfo(IncludeSourceInfo.forMainSchema(mainFile));
        schema.addChild(includeMiddle);

        // XsdInclude from middle.xsd to base.xsd (this is the nested include)
        XsdInclude includeBase = new XsdInclude();
        includeBase.setSchemaLocation("base.xsd");
        includeBase.setSourceInfo(IncludeSourceInfo.forIncludedSchema(middleFile, "middle.xsd", includeMiddle));
        schema.addChild(includeBase);

        // Element from main.xsd
        XsdElement mainElement = new XsdElement("MainElement");
        mainElement.setType("xs:string");
        mainElement.setSourceInfo(IncludeSourceInfo.forMainSchema(mainFile));
        schema.addChild(mainElement);

        // Element from middle.xsd
        XsdElement middleElement = new XsdElement("MiddleElement");
        middleElement.setType("xs:string");
        middleElement.setSourceInfo(IncludeSourceInfo.forIncludedSchema(middleFile, "middle.xsd", includeMiddle));
        schema.addChild(middleElement);

        // Element from base.xsd
        XsdElement baseElement = new XsdElement("BaseElement");
        baseElement.setType("xs:string");
        baseElement.setSourceInfo(IncludeSourceInfo.forIncludedSchema(baseFile, "base.xsd", includeBase));
        schema.addChild(baseElement);

        // Save all files
        Map<Path, MultiFileXsdSerializer.SaveResult> results = serializer.saveAll(schema, mainFile, false);

        // Verify all files were saved successfully
        assertEquals(3, results.size(), "Should save 3 files");
        assertTrue(results.get(mainFile).success(), "Main file should save successfully");
        assertTrue(results.get(middleFile).success(), "Middle file should save successfully");
        assertTrue(results.get(baseFile).success(), "Base file should save successfully");

        // Read and verify main.xsd content
        String mainContent = Files.readString(mainFile);
        assertTrue(mainContent.contains("xs:include"), "main.xsd should contain xs:include");
        assertTrue(mainContent.contains("schemaLocation=\"middle.xsd\""),
                "main.xsd should include middle.xsd");
        assertTrue(mainContent.contains("MainElement"),
                "main.xsd should contain MainElement");

        // Read and verify middle.xsd content - THIS IS THE KEY TEST
        String middleContent = Files.readString(middleFile);
        assertTrue(middleContent.contains("xs:include"),
                "middle.xsd should contain xs:include (nested include preserved)");
        assertTrue(middleContent.contains("schemaLocation=\"base.xsd\""),
                "middle.xsd should include base.xsd");
        assertTrue(middleContent.contains("MiddleElement"),
                "middle.xsd should contain MiddleElement");

        // Read and verify base.xsd content
        String baseContent = Files.readString(baseFile);
        assertTrue(baseContent.contains("BaseElement"),
                "base.xsd should contain BaseElement");
        assertFalse(baseContent.contains("xs:include"),
                "base.xsd should not contain xs:include (it's the leaf)");
    }

    @Test
    @DisplayName("saveAll() should correctly serialize xs:import in included files")
    void testNestedImportSerialization() throws IOException {
        Path mainFile = tempDir.resolve("main.xsd");
        Path includedFile = tempDir.resolve("included.xsd");

        XsdSchema schema = new XsdSchema();
        schema.setMainSchemaPath(mainFile);
        schema.getNamespaces().put("xs", "http://www.w3.org/2001/XMLSchema");
        schema.setTargetNamespace("http://example.com/test");

        // XsdInclude from main.xsd to included.xsd
        XsdInclude xsdInclude = new XsdInclude();
        xsdInclude.setSchemaLocation("included.xsd");
        xsdInclude.setSourceInfo(IncludeSourceInfo.forMainSchema(mainFile));
        schema.addChild(xsdInclude);

        // XsdImport in included.xsd (from another namespace)
        XsdImport xsdImport = new XsdImport();
        xsdImport.setNamespace("http://example.com/other");
        xsdImport.setSchemaLocation("other.xsd");
        xsdImport.setSourceInfo(IncludeSourceInfo.forIncludedSchema(includedFile, "included.xsd", xsdInclude));
        schema.addChild(xsdImport);

        // Element from included.xsd
        XsdElement element = new XsdElement("ImportedElement");
        element.setType("xs:string");
        element.setSourceInfo(IncludeSourceInfo.forIncludedSchema(includedFile, "included.xsd", xsdInclude));
        schema.addChild(element);

        // Save all files
        Map<Path, MultiFileXsdSerializer.SaveResult> results = serializer.saveAll(schema, mainFile, false);

        // Verify included.xsd contains the import
        String includedContent = Files.readString(includedFile);
        assertTrue(includedContent.contains("xs:import"),
                "included.xsd should contain xs:import");
        assertTrue(includedContent.contains("namespace=\"http://example.com/other\""),
                "included.xsd should have correct import namespace");
        assertTrue(includedContent.contains("schemaLocation=\"other.xsd\""),
                "included.xsd should have correct import schemaLocation");
    }

    @Test
    @DisplayName("saveAll() should handle deeply nested includes (A -> B -> C -> D)")
    void testDeeplyNestedIncludes() throws IOException {
        Path fileA = tempDir.resolve("a.xsd");
        Path fileB = tempDir.resolve("b.xsd");
        Path fileC = tempDir.resolve("c.xsd");
        Path fileD = tempDir.resolve("d.xsd");

        XsdSchema schema = new XsdSchema();
        schema.setMainSchemaPath(fileA);
        schema.getNamespaces().put("xs", "http://www.w3.org/2001/XMLSchema");

        // A includes B
        XsdInclude includeB = new XsdInclude();
        includeB.setSchemaLocation("b.xsd");
        includeB.setSourceInfo(IncludeSourceInfo.forMainSchema(fileA));
        schema.addChild(includeB);

        // B includes C
        XsdInclude includeC = new XsdInclude();
        includeC.setSchemaLocation("c.xsd");
        includeC.setSourceInfo(IncludeSourceInfo.forIncludedSchema(fileB, "b.xsd", includeB));
        schema.addChild(includeC);

        // C includes D
        XsdInclude includeD = new XsdInclude();
        includeD.setSchemaLocation("d.xsd");
        includeD.setSourceInfo(IncludeSourceInfo.forIncludedSchema(fileC, "c.xsd", includeC));
        schema.addChild(includeD);

        // Elements in each file
        XsdElement elementA = new XsdElement("ElementA");
        elementA.setType("xs:string");
        elementA.setSourceInfo(IncludeSourceInfo.forMainSchema(fileA));
        schema.addChild(elementA);

        XsdElement elementB = new XsdElement("ElementB");
        elementB.setType("xs:string");
        elementB.setSourceInfo(IncludeSourceInfo.forIncludedSchema(fileB, "b.xsd", includeB));
        schema.addChild(elementB);

        XsdElement elementC = new XsdElement("ElementC");
        elementC.setType("xs:string");
        elementC.setSourceInfo(IncludeSourceInfo.forIncludedSchema(fileC, "c.xsd", includeC));
        schema.addChild(elementC);

        XsdElement elementD = new XsdElement("ElementD");
        elementD.setType("xs:string");
        elementD.setSourceInfo(IncludeSourceInfo.forIncludedSchema(fileD, "d.xsd", includeD));
        schema.addChild(elementD);

        // Save all
        Map<Path, MultiFileXsdSerializer.SaveResult> results = serializer.saveAll(schema, fileA, false);

        // Verify all 4 files created
        assertEquals(4, results.size());

        // Verify each file contains proper content
        assertTrue(Files.readString(fileA).contains("schemaLocation=\"b.xsd\""));
        assertTrue(Files.readString(fileB).contains("schemaLocation=\"c.xsd\""));
        assertTrue(Files.readString(fileC).contains("schemaLocation=\"d.xsd\""));

        // D is the leaf - no includes
        String contentD = Files.readString(fileD);
        assertFalse(contentD.contains("xs:include"));
        assertTrue(contentD.contains("ElementD"));
    }
}
