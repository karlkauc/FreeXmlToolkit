package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.SignatureTrustValidator;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.fxt.freexmltoolkit.service.crypto.KeyMaterial;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code fxt-cli verify}: checks XML signatures, optionally the signer's trust chain. */
@Command(name = "verify",
        mixinStandardHelpOptions = true,
        header = "Verify XML digital signatures, optionally against a trust store.",
        footer = {
                "  # Is the signature intact?",
                "  fxt-cli verify order.signed.xml",
                "",
                "  # Also: does the signer's certificate chain lead to a trusted CA (with revocation check)?",
                "  fxt-cli verify order.signed.xml --trust-store cas.p12 --storepass-env TS_PW --revocation",
                "",
                "  # Many files, as JSON",
                "  fxt-cli verify -f json signed/*.xml"
        },
        description = {
                "Checks every signature reference and the signature value; SHA-1 signatures count as weak.",
                "With --trust-store (or --trust-cacerts) the signer's certificate chain is validated with PKIX.",
                "Exit code 0 when every file is valid (and trusted), 1 when one is not, 3 on errors."
        },
        sortOptions = false)
final class VerifyCommand implements Callable<Integer> {

    /** Report formats. */
    enum Format { text, json }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(arity = "1..*", paramLabel = "INPUT", description = "Signed XML documents.")
    List<Path> inputs = new ArrayList<>();

    @Option(names = "--trust-store", paramLabel = "FILE", description = "Trusted CA certificates (JKS or PKCS#12).")
    Path trustStore;

    @Option(names = "--trust-cacerts", description = "Use the Java runtime's CA certificates as trust store.")
    boolean trustCacerts;

    @Option(names = "--storepass", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "Password of --trust-store (prompted without a value).")
    char[] storePassword;

    @Option(names = "--storepass-env", paramLabel = "VAR", description = "Read the trust store password from an environment variable.")
    String storePasswordEnv;

    @Option(names = "--revocation", description = "Check revocation (CRL/OCSP; soft fail) with the trust check.")
    boolean revocation;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "text or json (default: ${DEFAULT-VALUE}).")
    Format format;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        PrintWriter out = commandLine.getOut(); // NOPMD - picocli's stream
        for (Path input : inputs) {
            CliSupport.requireFile(commandLine, input, "Input");
        }
        KeyStore trust = null;
        if (trustStore != null) {
            CliSupport.requireFile(commandLine, trustStore, "Trust store");
            char[] pass = CliSecrets.resolve(commandLine, storePassword, storePasswordEnv, "--storepass");
            trust = KeyMaterial.loadKeystore(trustStore, pass);
        } else if (trustCacerts) {
            trust = KeyStore.getInstance(KeyStore.getDefaultType());
            Path cacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
            try (var in = java.nio.file.Files.newInputStream(cacerts)) {
                trust.load(in, null);
            }
        }
        CliSupport.initServices();
        SignatureService service = new SignatureService();
        int exit = ExitCodes.OK;
        JsonArray json = new JsonArray();
        StringBuilder text = new StringBuilder();
        for (Path input : inputs) {
            SignatureService.ValidationOutcome outcome = service.validateSignatureDetailed(input.toFile());
            SignatureTrustValidator.TrustResult trustResult = null;
            if (trust != null && outcome.isValid()) {
                trustResult = SignatureTrustValidator.validate(input.toFile(), trust, revocation);
            }
            boolean ok = outcome.isValid() && (trustResult == null || trustResult.trusted());
            if (!ok) {
                exit = Math.max(exit, outcome.status() == SignatureService.ValidationStatus.ERROR
                        ? ExitCodes.NOT_RUN : ExitCodes.FAILED);
            }
            JsonObject o = new JsonObject();
            o.addProperty("file", input.toString());
            o.addProperty("status", outcome.status().name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("message", outcome.message());
            if (trustResult != null) {
                o.addProperty("trusted", trustResult.trusted());
                o.addProperty("trust", trustResult.message());
            }
            json.add(o);
            text.append(input).append(": ").append(outcome.status()).append(" - ").append(outcome.message()).append('\n');
            if (trustResult != null) {
                trustResult.report().lines().forEach(l -> text.append("    ").append(l).append('\n'));
            }
        }
        out.print(format == Format.json
                ? new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n" : text);
        out.flush();
        return exit;
    }
}
