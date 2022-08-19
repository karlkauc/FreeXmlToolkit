package org.fxt.freexmltoolkit;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

public class XpathTest {

    @Inject
    XmlService xmlService;

    @BeforeEach
    public void setUp() {
        Injector injector = Guice.createInjector(new ModuleBindings());
        xmlService = injector.getInstance(XmlServiceImpl.class);
    }



    @Test
    void xpathTest1() {
        var f = new File("src/test/resources/test01.xml");

        assert (xmlService != null);
        xmlService.setCurrentXmlFile(f);
        assert (xmlService.getSchemaNameFromCurrentXMLFile().equals("https://fdp-service.oekb.at/FundsXML_4.1.7_AI.xsd"));
        assert (xmlService.getCurrentXmlFile().getPath().equals("src/test/resources/test01.xml"));


    }
}
