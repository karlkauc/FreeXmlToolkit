package org.fxt.freexmltoolkit.service.fo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A stand-in for an external XSL-FO processor, started by the tests with the running JVM:
 * {@code FakeFoProcessor MODE FO PDF [more…]}.
 * <ul>
 *   <li>{@code ok}: checks the FO exists, prints a warning, writes a small "PDF" holding all arguments</li>
 *   <li>{@code fail}: prints an error, exits with 3</li>
 *   <li>{@code nooutput}: exits with 0 without writing</li>
 *   <li>{@code hang}: sleeps for a minute</li>
 * </ul>
 */
public final class FakeFoProcessor {

    private FakeFoProcessor() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        switch (mode) {
            case "ok" -> {
                Path fo = Path.of(args[1]);
                if (!Files.isRegularFile(fo) || !Files.readString(fo, StandardCharsets.UTF_8).contains("fo:root")) {
                    System.out.println("ERROR: no FO at " + fo);
                    System.exit(2);
                }
                System.out.println("Rendering " + fo.getFileName());
                System.err.println("WARNING: font 'Foo' substituted");
                Files.writeString(Path.of(args[2]), "%PDF-1.4 fake\n" + String.join("\n", List.of(args)),
                        StandardCharsets.UTF_8);
            }
            case "fail" -> {
                System.out.println("[error] Unknown property: fo:bogus");
                System.exit(3);
            }
            case "nooutput" -> System.out.println("done");
            case "hang" -> Thread.sleep(60_000);
            default -> System.exit(9);
        }
    }
}
