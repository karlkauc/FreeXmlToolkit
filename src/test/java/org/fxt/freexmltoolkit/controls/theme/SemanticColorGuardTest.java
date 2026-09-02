package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ratchet guard for the colour-system consolidation (usability report #10): source
 * files that have been migrated to {@link SemanticColors} must stay free of inline
 * {@code "#rrggbb"} colour literals, so the scattered-hex problem cannot creep back
 * into already-cleaned code. As more files are migrated, add them to
 * {@link #MIGRATED_FILES}.
 */
class SemanticColorGuardTest {

    /** Files already migrated to {@link SemanticColors}; must contain no inline hex colours. */
    private static final List<String> MIGRATED_FILES = List.of(
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/menu/XsdContextMenuFactory.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/view/XmlGridContextMenu.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shared/utilities/XmlContextMenuManager.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/jsoneditor/editor/JsonContextMenuManager.java",
            "src/main/java/org/fxt/freexmltoolkit/util/ContextMenuFactory.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/dialogs/UpdateProgressDialog.java",
            "src/main/java/org/fxt/freexmltoolkit/debugger/ui/BreakpointGutterFactory.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/managers/ContextMenuManagerV2.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shared/CustomizableSectionContainer.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/DocumentationView.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/schema/XmlInstanceTreeView.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/widgets/EnumerationComboBox.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/widgets/NumericSpinner.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/unified/UnifiedSearchBar.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/xmleditor/widgets/LengthValidatedField.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/v2/editor/dialogs/TypeUsageDialog.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/SchematronTester.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/diff/DiffView.java",
            "src/main/java/org/fxt/freexmltoolkit/domain/ValidationStatus.java",
            "src/main/java/org/fxt/freexmltoolkit/domain/SnippetValidationRule.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/analysis/AnalysisSupport.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/analysis/SchemaAnalysisView.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/analysis/StatisticsSection.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/analysis/QualitySection.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/analysis/IdentityConstraintsSection.java",
            "src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/analysis/XPathSection.java"
    );

    /** A 6-digit hex colour literal (e.g. {@code #dc3545}). */
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}\\b");

    @Test
    @DisplayName("Migrated files must use SemanticColors, not inline hex")
    void migratedFilesHaveNoInlineHexColours() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String relative : MIGRATED_FILES) {
            Path file = Path.of(relative);
            assertTrue(Files.exists(file), "Guarded file not found: " + file.toAbsolutePath());
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (HEX.matcher(lines.get(i)).find()) {
                    failures.add(relative + ":" + (i + 1) + "  " + lines.get(i).trim());
                }
            }
        }
        assertTrue(failures.isEmpty(),
                "These lines still hardcode a hex colour — use org.fxt.freexmltoolkit.controls.theme.SemanticColors:\n"
                        + String.join("\n", failures));
    }
}
