package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every HTTP connection of the application is opened by {@link ConnectionServiceImpl}, which applies
 * the proxy, proxy authentication (NTLM), timeouts and certificate settings. Code that needs one asks
 * {@link ConnectionService} (openConnection, fetchBinary, executeHttpRequest, postJson).
 */
class HttpCallsGuardTest {

    private static final Pattern DIRECT_CALL = Pattern.compile(
            "toURL\\(\\)\\.open(Connection|Stream)\\(|\\burl\\w*\\.open(Connection|Stream)\\(|new URL\\("
                    + "|HttpClient\\.new|java\\.net\\.http\\.|HttpClients\\.|HttpClientBuilder");

    @Test
    void onlyTheHttpServiceOpensConnections() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("ConnectionServiceImpl.java")) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                        continue; // documentation
                    }
                    if (DIRECT_CALL.matcher(line).find()) {
                        offenders.add(file + ":" + (i + 1) + "  " + line);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "HTTP connections opened outside ConnectionServiceImpl:\n"
                + String.join("\n", offenders));
    }
}
