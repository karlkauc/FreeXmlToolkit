package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter;

/** Gives tests in other packages the sample schema of {@link EntryHelperModelTest}. */
public final class EntryHelperModelTestSupport {

    private EntryHelperModelTestSupport() {
    }

    /** @return a schema adapter over the Company sample schema, written into {@code dir} */
    public static XsdSchemaAdapter schema(Path dir) throws Exception {
        return EntryHelperModelTest.schema(dir);
    }
}
