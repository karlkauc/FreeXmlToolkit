package org.fxt.freexmltoolkit;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;

public class CreateTableTest {

    @Test
    void createHtmlTable() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/template/");
        resolver.setSuffix(".html");

        var context = new Context();
        context.setVariable("name", "Lind");
        context.setVariable("date", LocalDateTime.now().toString());

        var templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        var result = templateEngine.process("xsdTemplate", context);
        System.out.println(result);
    }

}
