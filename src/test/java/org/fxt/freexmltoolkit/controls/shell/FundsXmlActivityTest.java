package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FundsXmlActivityTest {

    @Test
    void fundsXmlActivityExists() {
        // The FundsXML plugin contributes the activity with the stable id "fundsxml".
        Activity a = Activity.fromId("fundsxml").orElseThrow();
        assertTrue(a.plugin().isPresent(), "it comes from the plugin");
        assertTrue(a.workflow() == org.fxt.freexmltoolkit.controls.theme.Workflow.FUNDSXML);
    }
}
