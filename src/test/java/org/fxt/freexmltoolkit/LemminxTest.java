package org.fxt.freexmltoolkit;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.lemminx.XMLLanguageServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.fxt.freexmltoolkit.service.ModuleBindings;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class LemminxTest {

    @Inject
    XmlService xmlService;


    @BeforeEach
    public void setUp() {
        Injector injector = Guice.createInjector(new ModuleBindings());
        xmlService = injector.getInstance(XmlServiceImpl.class);
    }

    @Test
    void xpathTest1() throws ExecutionException, InterruptedException {
        var f = new File("src/test/resources/test01.xml");
        var server = new XMLLanguageServer();

        final Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        var startListening = launcher.startListening();
        var t = startListening.get();


        var client = server.getLanguageClient();


    }

}
