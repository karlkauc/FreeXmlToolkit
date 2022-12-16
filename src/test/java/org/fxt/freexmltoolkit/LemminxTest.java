package org.fxt.freexmltoolkit;

import org.eclipse.lemminx.XMLLanguageServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class LemminxTest {


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
