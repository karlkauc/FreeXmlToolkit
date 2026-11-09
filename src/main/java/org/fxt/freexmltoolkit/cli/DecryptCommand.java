package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.EncryptionActionRunner;
import org.fxt.freexmltoolkit.service.crypto.EncryptionReport;
import org.fxt.freexmltoolkit.service.crypto.KeyMaterial;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.DecryptionKeys;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli decrypt}: decrypts the EncryptedData of a document (also super-encrypted layers)
 * with a private key, a symmetric key or a password, or describes it without a key ({@code --inspect}).
 */
@Command(name = "decrypt",
        mixinStandardHelpOptions = true,
        header = "Decrypt XML (W3C XML Encryption 1.1), or describe an encrypted document.",
        footer = {
                "  # With the private key of a keystore entry (password from an environment variable)",
                "  fxt-cli decrypt order.encrypted.xml --keystore me.p12 --storepass-env KS_PW -o order.xml",
                "",
                "  # With a PEM private key, a shared key file or a password (prompted)",
                "  fxt-cli decrypt report.enc.xml --private-key me.pem",
                "  fxt-cli decrypt order.encrypted.xml --key-file partner.key",
                "  fxt-cli decrypt report.enc.xml --password",
                "",
                "  # What is encrypted, for whom and with which algorithms - no key needed",
                "  fxt-cli decrypt --inspect order.encrypted.xml",
                "  fxt-cli decrypt --inspect -f json order.encrypted.xml"
        },
        description = {
                "Decrypts every EncryptedData that one of the given keys opens (--id: only these), layer by layer. "
                        + "A document that is one EncryptedData of binary data is written as the plain file.",
                "Exit code 0 when everything selected was decrypted, 1 when no key fits or some data stays "
                        + "encrypted, 3 on errors."
        },
        sortOptions = false)
final class DecryptCommand implements Callable<Integer> {

    /** Report formats of --inspect. */
    enum Format { text, json }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(paramLabel = "INPUT", description = "The encrypted document.")
    Path input;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE",
            description = "The decrypted file. Default: INPUT with .decrypted.xml (binary data: .decrypted.bin).")
    Path output;

    @Option(names = "--keystore", paramLabel = "FILE", description = "Keystore (JKS, PKCS#12) with your private key.")
    Path keystore;

    @Option(names = "--alias", paramLabel = "ALIAS", description = "Key alias in --keystore (default: its only key).")
    String alias;

    @Option(names = "--storepass", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "Password of --keystore (prompted without a value).")
    char[] storePassword;

    @Option(names = "--storepass-env", paramLabel = "VAR", description = "Read the keystore password from an environment variable.")
    String storePasswordEnv;

    @Option(names = "--keypass", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "Password of the key entry or of an encrypted --private-key (default: the keystore password).")
    char[] keyPassword;

    @Option(names = "--keypass-env", paramLabel = "VAR", description = "Read the key password from an environment variable.")
    String keyPasswordEnv;

    @Option(names = "--private-key", paramLabel = "FILE", description = "A private key in PEM (repeatable).")
    List<Path> privateKeys = new ArrayList<>();

    @Option(names = "--key-file", paramLabel = "FILE", description = "A shared symmetric key (hex, Base64 or raw; repeatable).")
    List<Path> keyFiles = new ArrayList<>();

    @Option(names = "--key-name", paramLabel = "NAME", description = "The KeyName --key-file stands for (default: any).")
    String keyName;

    @Option(names = "--password", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "The password of a password-encrypted document (prompted without a value).")
    char[] password;

    @Option(names = "--password-env", paramLabel = "VAR", description = "Read the password from an environment variable.")
    String passwordEnv;

    @Option(names = "--id", paramLabel = "ID", description = "Only the EncryptedData with this Id (repeatable).")
    List<String> ids = new ArrayList<>();

    @Option(names = "--inspect", description = "Describe the encrypted document instead of decrypting it (no key needed).")
    boolean inspect;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "Format of --inspect: text or json (default: ${DEFAULT-VALUE}).")
    Format format;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        PrintWriter out = commandLine.getOut(); // NOPMD - picocli's stream
        PrintWriter err = commandLine.getErr(); // NOPMD - picocli's stream
        CliSupport.requireFile(commandLine, input, "Input");
        CliSupport.initServices();
        if (inspect) {
            EncryptionReport report = EncryptionActionRunner.inspectFile(input);
            String text = format == Format.json ? report.toJson() : report.toText();
            if (output != null) {
                CliSupport.write(output, text);
            } else {
                out.print(text);
                out.flush();
            }
            return ExitCodes.OK;
        }
        DecryptionKeys keys = keys(commandLine);
        Path target = output != null ? output : EncryptionActionRunner.sibling(input, ".decrypted.xml");
        EncryptionActionRunner.Result result = EncryptionActionRunner.decryptFile(input, target, keys,
                new LinkedHashSet<>(ids));
        if (result.ok() && output == null && result.binary()) {
            // binary data: not an .xml file
            Path binary = EncryptionActionRunner.sibling(input, ".decrypted.bin");
            java.nio.file.Files.move(target, binary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            target = binary;
        }
        result.warnings().forEach(w -> err.println("fxt-cli: warning: " + w));
        if (!result.ok()) {
            err.println("fxt-cli: " + result.message());
            // a wrong key or missing data is the input's fault (1), anything else an error (3)
            return result.failure().status() == org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.INVALID_INPUT
                    ? ExitCodes.FAILED : ExitCodes.NOT_RUN;
        }
        if (parent == null || !parent.quiet) {
            err.println(result.message());
            err.println("Wrote " + target);
        }
        return result.message().contains(" left") ? ExitCodes.FAILED : ExitCodes.OK;
    }

    private DecryptionKeys keys(CommandLine commandLine) throws Exception {
        List<PrivateKey> privates = new ArrayList<>();
        char[] keyPass = CliSecrets.resolve(commandLine, keyPassword, keyPasswordEnv, "--keypass");
        if (keystore != null) {
            CliSupport.requireFile(commandLine, keystore, "Keystore");
            char[] storePass = CliSecrets.resolve(commandLine, storePassword, storePasswordEnv, "--storepass");
            privates.add(KeyMaterial.privateKey(keystore, storePass, alias, keyPass).privateKey());
        }
        for (Path file : privateKeys) {
            CliSupport.requireFile(commandLine, file, "Private key");
            privates.add(KeyMaterial.privateKey(file, keyPass));
        }
        List<XmlEncryptionService.NamedKey> secrets = new ArrayList<>();
        for (Path file : keyFiles) {
            CliSupport.requireFile(commandLine, file, "Key file");
            secrets.add(new XmlEncryptionService.NamedKey(keyName, KeyMaterial.symmetricKey(file, "AES")));
        }
        char[] pass = CliSecrets.resolve(commandLine, password, passwordEnv, "--password");
        if (privates.isEmpty() && secrets.isEmpty() && (pass == null || pass.length == 0)) {
            throw new CommandLine.ParameterException(commandLine,
                    "Give a key: --keystore, --private-key, --key-file or --password (or --inspect)");
        }
        return new DecryptionKeys(privates, secrets, pass);
    }
}
