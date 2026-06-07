package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FundsXmlActivityTest {

    @Test
    void fundsXmlActivityExists() {
        // The enum constant must exist with the stable id "fundsxml".
        Activity a = Activity.valueOf("FUNDSXML");
        assertTrue(a.id().equals("fundsxml"));
    }
}
