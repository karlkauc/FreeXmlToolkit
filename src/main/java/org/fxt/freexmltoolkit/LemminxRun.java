package org.fxt.freexmltoolkit;

import javafx.util.Pair;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class LemminxRun {

    static class AssertingEndpoint implements Endpoint {
        public Map<String, Pair<Object, Object>> expectedRequests = new LinkedHashMap<>();

        @Override
        public CompletableFuture<?> request(String method, Object parameter) {
            // Assert.assertTrue(expectedRequests.containsKey(method));
            Pair<Object, Object> result = expectedRequests.remove(method);
            // Assert.assertEquals(result.getKey().toString(), parameter.toString());
            return CompletableFuture.completedFuture(result.getValue());
        }

        public Map<String, Object> expectedNotifications = new LinkedHashMap<>();

        @Override
        public void notify(String method, Object parameter) {
            // Assert.assertTrue(expectedNotifications.containsKey(method));
            Object object = expectedNotifications.remove(method);
            // Assert.assertEquals(object.toString(), parameter.toString());
        }

        /**
         * wait max 1 sec for all expectations to be removed
         */
        public void joinOnEmpty() {
            long before = System.currentTimeMillis();
            do {
                if (expectedNotifications.isEmpty() && expectedNotifications.isEmpty()) {
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } while (System.currentTimeMillis() < before + 1000);
            // Assert.fail("expectations weren't empty "+toString());
        }

        @Override
        public String toString() {
            // return new ToStringBuilder(this).addAllFields().toString();
            return "";
        }

    }


    private static AssertingEndpoint server;
    private static Launcher<LanguageClient> serverLauncher;
    private static Future<?> serverListening;

    private static AssertingEndpoint client;
    private static Launcher<LanguageServer> clientLauncher;
    private static Future<?> clientListening;

    private Level logLevel;

    public static void main(String[] args) {
        PipedInputStream inClient = new PipedInputStream();
        PipedOutputStream outClient = new PipedOutputStream();
        PipedInputStream inServer = new PipedInputStream();
        PipedOutputStream outServer = new PipedOutputStream();

        try {
            inClient.connect(outServer);
            outClient.connect(inServer);
            server = new AssertingEndpoint();
            serverLauncher = LSPLauncher.createServerLauncher(ServiceEndpoints.toServiceObject(server, LanguageServer.class), inServer, outServer);
            serverListening = serverLauncher.startListening();

            client = new AssertingEndpoint();
            clientLauncher = LSPLauncher.createClientLauncher(ServiceEndpoints.toServiceObject(client, LanguageClient.class), inClient, outClient);
            clientListening = clientLauncher.startListening();

        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
