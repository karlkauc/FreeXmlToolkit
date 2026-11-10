package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.di.FxtTestModule;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The status-bar mark that shows a document was recognised as FundsXML and its checks apply. */
class FundsXmlBadgeTest {

    private static final String FUNDS_XML = "<?xml version=\"1.0\"?>\n<FundsXML4><ControlData/></FundsXML4>";

    private PropertiesService props;

    @BeforeEach
    void setUp() {
        FxtTestModule module = new FxtTestModule();
        module.configure();
        props = module.mockPropertiesService();
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
    }

    /** Switches the FundsXML extension on or off in the (mocked) settings. */
    private void extension(boolean on) {
        Properties settings = new Properties();
        settings.setProperty(FundsXmlPropertyKeys.ENABLED, String.valueOf(on));
        when(props.loadProperties()).thenReturn(settings);
    }

    @Test
    void shownForFundsXmlWhileTheExtensionIsOn() {
        extension(true);
        var badge = FundsXmlPlugin.badgeFor(null, FUNDS_XML);
        assertNotNull(badge);
        assertEquals("FundsXML", badge.text());
        assertEquals(Workflow.FUNDSXML, badge.workflow());
        assertTrue(badge.tooltip().contains("data-quality checks"), badge.tooltip());
        assertNotNull(badge.onClick());
    }

    @Test
    void notShownForOtherDocumentsOrWhileTheExtensionIsOff() {
        extension(true);
        assertNull(FundsXmlPlugin.badgeFor(null, "<order><FundsXML4/></order>"));
        extension(false);
        assertNull(FundsXmlPlugin.badgeFor(null, FUNDS_XML));
    }
}
