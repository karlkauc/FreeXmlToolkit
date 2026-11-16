package org.fxt.freexmltoolkit.service.xsltproc;

import java.nio.file.Path;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * A stand-in for an external XSLT processor, started by the tests with the running JVM:
 * {@code FakeXsltProcessor MODE XSL XML OUT [name=value…]}.
 * <ul>
 *   <li>{@code ok}: transforms with the JDK's XSLT 1.0 processor (relative includes resolve against the
 *       stylesheet file), the parameters set, prints "Message: started" like an {@code xsl:message}</li>
 *   <li>{@code saxonerror}: prints a Saxon static error on line 7 with its code, exits with 2</li>
 *   <li>{@code xsltprocerror}: prints xsltproc's runtime error on line 5, exits with 6</li>
 *   <li>{@code nooutput}: exits with 0 without writing</li>
 *   <li>{@code hang}: sleeps for a minute</li>
 * </ul>
 */
public final class FakeXsltProcessor {

    private FakeXsltProcessor() {
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "ok" -> {
                Transformer transformer = TransformerFactory.newDefaultInstance()
                        .newTransformer(new StreamSource(Path.of(args[1]).toFile()));
                for (int i = 4; i < args.length; i++) {
                    int eq = args[i].indexOf('=');
                    transformer.setParameter(args[i].substring(0, eq), args[i].substring(eq + 1));
                }
                System.err.println("Message: started");
                transformer.transform(new StreamSource(Path.of(args[2]).toFile()), new StreamResult(Path.of(args[3]).toFile()));
            }
            case "saxonerror" -> {
                System.err.println("Static error at xsl:value-of on line 7 column 42 of " + Path.of(args[1]).getFileName() + ":");
                System.err.println("  XPST0008  Variable total has not been declared (or its declaration is not in scope)");
                System.err.println("Errors were reported during stylesheet compilation");
                System.exit(2);
            }
            case "xsltprocerror" -> {
                System.err.println("runtime error: file " + args[1] + " line 5 element value-of");
                System.err.println("XPath error : Undefined variable");
                System.exit(6);
            }
            case "nooutput" -> System.exit(0);
            case "hang" -> Thread.sleep(60_000);
            default -> System.exit(9);
        }
    }
}
