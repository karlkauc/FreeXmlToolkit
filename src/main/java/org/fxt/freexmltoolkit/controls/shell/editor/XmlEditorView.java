package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.scene.layout.Region;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;

/**
 * {@link EditorView} adapter for the XML-family text editor ({@code XmlCodeEditorV2}),
 * including schema-aware IntelliSense via a mutable schema provider.
 */
final class XmlEditorView implements EditorView {

    private final MutableXmlSchemaProvider schemaProvider = new MutableXmlSchemaProvider();
    private final XmlCodeEditorV2 editor = new XmlCodeEditorV2(schemaProvider);

    @Override
    public Region getNode() {
        return editor;
    }

    @Override
    public void setText(String text) {
        editor.setText(text);
    }

    @Override
    public String getText() {
        return editor.getText();
    }

    @Override
    public void replaceTextRegion(int start, int oldEnd, String replacement) {
        editor.replaceTextRegion(start, oldEnd, replacement);
    }

    @Override
    public CodeArea getCodeArea() {
        return editor.getCodeArea();
    }

    @Override
    public boolean supportsSchema() {
        return true;
    }

    @Override
    public boolean loadSchema(File xsd) {
        return schemaProvider.loadSchema(xsd);
    }

    @Override
    public void invalidateIntelliSenseCache() {
        editor.invalidateIntelliSenseCache();
    }

    @Override
    public boolean setExtraGutterFactory(java.util.function.IntFunction<javafx.scene.Node> factory) {
        editor.setExtraGutterFactory(factory);
        return true;
    }

    @Override
    public void refreshGutter() {
        editor.refreshGutter();
    }

    @Override
    public void setGoToDefinitionHandler(
            java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.editor.core.NavigationRequest> handler) {
        editor.getEditorContext().setGoToDefinitionHandler(handler);
    }

    /** @return the wrapped editor's context (for go-to-definition wiring / tests). */
    org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext getEditorContext() {
        return editor.getEditorContext();
    }
}
