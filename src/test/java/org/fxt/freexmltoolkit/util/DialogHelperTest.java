package org.fxt.freexmltoolkit.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DialogHelperTest {
    @Test
    void testEnums() {
        assertNotNull(DialogHelper.HeaderTheme.PRIMARY);
        assertNotNull(DialogHelper.InfoBoxType.INFO);
    }
}
