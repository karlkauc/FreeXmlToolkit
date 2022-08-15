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
        System.out.println("setUp()");
        Injector injector = Guice.createInjector(new ModuleBindings());
        xmlService = injector.getInstance(XmlServiceImpl.class);

    }

    @Test
    void xpathTest1() {
        var f = new File("src/test/resources/test01.xml");

        xmlService.setCurrentXmlFile(f);
        System.out.println("xmlService = " + xmlService.getSchemaFromXMLFile());
        System.out.println("xmlService.getCurrentXmlFile() = " + xmlService.getCurrentXmlFile());
    }
}
