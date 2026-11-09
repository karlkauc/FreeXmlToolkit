package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.EncryptionActionRunner;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.fxt.freexmltoolkit.service.crypto.KeyMaterial;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code fxt-cli sign}: signs an XML document (XML-DSig) with a key from a keystore. */
@Command(name = "sign",
        mixinStandardHelpOptions = true,
        header = "Sign an XML document (XML digital signature) with a keystore key.",
        footer = {
                "  # Enveloped signature, keystore password from an environment variable",
                "  fxt-cli sign order.xml --keystore me.p12 --storepass-env KS_PW",
                "",
                "  # Enveloping signature to a chosen file; prompt for the password",
                "  fxt-cli sign order.xml --keystore me.jks --alias me --storepass --type enveloping -o order.sig.xml"
        },
        description = {
                "Signs with RSA-SHA256 and exclusive canonicalization; the certificate goes into KeyInfo.",
                "Types: enveloped (the signature inside the document, default), enveloping (the document inside "
                        + "the signature), detached (a signature file referring to the document next to it)."
        },
        sortOptions = false)
final class SignCommand implements Callable<Integer> {

    /** Signature types. */
    enum Type { enveloped, enveloping, detached }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(paramLabel = "INPUT", description = "The XML document to sign.")
    Path input;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE",
            description = "The signed file. Default: INPUT with .signed.xml (detached: .sig.xml).")
    Path output;

    @Option(names = "--keystore", required = true, paramLabel = "FILE", description = "Keystore (JKS or PKCS#12) with the signing key.")
    Path keystore;

    @Option(names = "--alias", paramLabel = "ALIAS", description = "Key alias (default: the keystore's only key).")
    String alias;

    @Option(names = "--storepass", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "Keystore password (prompted without a value).")
    char[] storePassword;

    @Option(names = "--storepass-env", paramLabel = "VAR", description = "Read the keystore password from an environment variable.")
    String storePasswordEnv;

    @Option(names = "--keypass", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "Password of the key entry (default: the keystore password).")
    char[] keyPassword;

    @Option(names = "--keypass-env", paramLabel = "VAR", description = "Read the key password from an environment variable.")
    String keyPasswordEnv;

    @Option(names = "--type", paramLabel = "TYPE", defaultValue = "enveloped",
            description = "enveloped, enveloping or detached (default: ${DEFAULT-VALUE}).")
    Type type;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        PrintWriter err = commandLine.getErr(); // NOPMD - picocli's stream
        CliSupport.requireFile(commandLine, input, "Input");
        CliSupport.requireFile(commandLine, keystore, "Keystore");
        char[] storePass = CliSecrets.resolve(commandLine, storePassword, storePasswordEnv, "--storepass");
        if (storePass == null) {
            throw new CommandLine.ParameterException(commandLine, "Give --storepass or --storepass-env");
        }
        char[] keyPass = CliSecrets.resolve(commandLine, keyPassword, keyPasswordEnv, "--keypass");
        CliSupport.initServices();
        String chosen = alias;
        if (chosen == null || chosen.isBlank()) {
            var aliases = KeyMaterial.privateKeyAliases(KeyMaterial.loadKeystore(keystore, storePass));
            if (aliases.size() != 1) {
                err.println("fxt-cli: " + (aliases.isEmpty() ? "the keystore holds no private key"
                        : "the keystore holds several keys, choose one with --alias: " + String.join(", ", aliases)));
                return ExitCodes.FAILED;
            }
            chosen = aliases.get(0);
        }
        Path target = output != null ? output
                : EncryptionActionRunner.sibling(input, type == Type.detached ? ".sig.xml" : ".signed.xml");
        try {
            new SignatureService().signDocument(input.toFile(), keystore.toFile(), new String(storePass), chosen,
                    new String(keyPass != null ? keyPass : storePass), target.toString(),
                    SignatureService.SignatureType.valueOf(type.name().toUpperCase(Locale.ROOT)));
        } catch (RuntimeException e) {
            err.println("fxt-cli: signing failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            return ExitCodes.FAILED;
        }
        if (parent == null || !parent.quiet) {
            err.println("Wrote " + target);
        }
        return ExitCodes.OK;
    }
}
