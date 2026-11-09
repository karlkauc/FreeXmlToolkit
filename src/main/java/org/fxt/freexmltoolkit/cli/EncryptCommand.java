package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.crypto.SecretKey;

import org.fxt.freexmltoolkit.controls.shell.editor.EncryptionActionRunner;
import org.fxt.freexmltoolkit.service.crypto.KeyMaterial;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Kind;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.EncryptionRequest;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.PasswordRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.PublicKeyRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.Recipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.SymmetricRecipient;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.TargetType;
import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionService.X509Reference;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli encrypt}: W3C XML Encryption 1.1 of elements, element content, the whole document
 * or any file, for certificates, public keys, keystore entries, symmetric keys and passwords.
 */
@Command(name = "encrypt",
        mixinStandardHelpOptions = true,
        header = "Encrypt XML (W3C XML Encryption 1.1) for certificates, keys or a password.",
        footer = {
                "  # Encrypt the card elements for a partner's certificate (AES-256-GCM, RSA-OAEP)",
                "  fxt-cli encrypt order.xml -x '//p:card' --ns p=urn:pay --cert partner.pem",
                "",
                "  # The whole document for two recipients and a password (from an environment variable)",
                "  fxt-cli encrypt report.xml --cert alice.cer --cert bob-ec.pem --password-env REPORT_PW -o report.enc.xml",
                "",
                "  # Only the content of an element, with a shared AES key named 'partner-2026'",
                "  fxt-cli encrypt order.xml -x //payment --content --key-file partner.key --key-name partner-2026",
                "",
                "  # Any file (a PDF) as EncryptedData; the algorithms on offer",
                "  fxt-cli encrypt invoice.pdf --binary --cert partner.pem -o invoice.pdf.xml",
                "  fxt-cli encrypt --list-algorithms"
        },
        description = {
                "Encrypts the elements an XPath selects (default: the document element), only their content "
                        + "(--content), or any file as binary data (--binary).",
                "Recipients (any number, at least one): --cert, --public-key, --keystore with --alias, "
                        + "--key-file (shared symmetric key), --password. RSA keys use RSA-OAEP, EC keys ECDH-ES, "
                        + "X25519/X448 keys their key agreement.",
                "Legacy algorithms (RSA-1_5, TripleDES, CBC modes) work but print a warning."
        },
        sortOptions = false)
final class EncryptCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(arity = "0..1", paramLabel = "INPUT", description = "The XML document (or any file with --binary).")
    Path input;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE",
            description = "The encrypted file. Default: INPUT with .encrypted.xml.")
    Path output;

    @Option(names = {"-x", "--xpath"}, paramLabel = "XPATH",
            description = "Elements to encrypt (XPath 1.0, repeatable). Default: the document element.")
    List<String> xpaths = new ArrayList<>();

    @Option(names = "--ns", paramLabel = "PREFIX=URI",
            description = "A namespace prefix for --xpath (repeatable; the document element's prefixes are known).")
    Map<String, String> namespaces = new LinkedHashMap<>();

    @Option(names = "--content", description = "Encrypt only the content of the elements, not the elements themselves.")
    boolean content;

    @Option(names = "--binary", description = "Encrypt INPUT as binary data (any file).")
    boolean binary;

    @Option(names = "--mime-type", paramLabel = "TYPE", description = "MimeType of --binary data (default: from the file).")
    String mimeType;

    @Option(names = "--data-alg", paramLabel = "ALG", defaultValue = "aes256-gcm",
            description = "Block cipher of the data (default: ${DEFAULT-VALUE}).")
    String dataAlgorithm;

    @Option(names = "--cert", paramLabel = "FILE", description = "A recipient certificate (PEM or DER; repeatable).")
    List<Path> certificates = new ArrayList<>();

    @Option(names = "--public-key", paramLabel = "FILE", description = "A recipient public key in PEM (repeatable).")
    List<Path> publicKeys = new ArrayList<>();

    @Option(names = "--keystore", paramLabel = "FILE", description = "Keystore (JKS, PKCS#12) with recipient certificates.")
    Path keystore;

    @Option(names = "--alias", paramLabel = "ALIAS", description = "Recipient certificate alias in --keystore (repeatable).")
    List<String> aliases = new ArrayList<>();

    @Option(names = "--storepass", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "Password of --keystore (prompted without a value).")
    char[] storePassword;

    @Option(names = "--storepass-env", paramLabel = "VAR", description = "Read the keystore password from an environment variable.")
    String storePasswordEnv;

    @Option(names = "--key-file", paramLabel = "FILE",
            description = "A shared symmetric key (16, 24 or 32 bytes; hex, Base64 or raw).")
    Path keyFile;

    @Option(names = "--key-name", paramLabel = "NAME", description = "KeyName written for --key-file, so the receiver knows the key.")
    String keyName;

    @Option(names = "--key-wrap", paramLabel = "ALG",
            description = "Key wrap for --key-file and --password (default: AES matching the data key); "
                    + "'none' uses --key-file as the data key itself.")
    String keyWrap;

    @Option(names = "--password", paramLabel = "PASSWORD", arity = "0..1", interactive = true,
            description = "A password recipient (PBKDF2; prompted without a value).")
    char[] password;

    @Option(names = "--password-env", paramLabel = "VAR", description = "Read the password from an environment variable.")
    String passwordEnv;

    @Option(names = "--iterations", paramLabel = "N", defaultValue = "600000",
            description = "PBKDF2 iterations for --password (default: ${DEFAULT-VALUE}).")
    int iterations;

    @Option(names = "--prf", paramLabel = "HMAC", defaultValue = "sha256",
            description = "PBKDF2 PRF: sha256, sha384 or sha512 (default: ${DEFAULT-VALUE}).")
    String prf;

    @Option(names = "--key-transport", paramLabel = "ALG",
            description = "For RSA recipients: rsa-oaep (default), rsa-oaep-mgf1p or rsa-1_5.")
    String keyTransport;

    @Option(names = "--oaep-digest", paramLabel = "DIGEST", description = "RSA-OAEP digest: sha1, sha224, sha256 (default), sha384, sha512.")
    String oaepDigest;

    @Option(names = "--mgf", paramLabel = "MGF", description = "RSA-OAEP 1.1 mask generation: mgf1sha1 … mgf1sha512 (default mgf1sha256).")
    String mgf;

    @Option(names = "--kdf", paramLabel = "KDF", description = "For EC/X25519/X448 recipients: concatkdf (default) or hkdf.")
    String kdf;

    @Option(names = "--kdf-digest", paramLabel = "DIGEST", description = "Digest of the KDF (default sha256).")
    String kdfDigest;

    @Option(names = "--x509", paramLabel = "KIND", defaultValue = "issuer-serial",
            description = "How an EncryptedKey names a recipient certificate: issuer-serial (default), certificate, "
                    + "ski, subject or none.")
    String x509;

    @Option(names = "--recipient-name", paramLabel = "NAME",
            description = "Recipient attribute of every EncryptedKey (e.g. the partner's name).")
    String recipientName;

    @Option(names = "--property", paramLabel = "NAME=VALUE", description = "An EncryptionProperty (repeatable).")
    Map<String, String> properties = new LinkedHashMap<>();

    @Option(names = "--keys-outside", description = "Write EncryptedKeys next to the EncryptedData (ReferenceList) instead of inside it.")
    boolean keysOutside;

    @Option(names = "--list-algorithms", description = "List the algorithms and exit.")
    boolean listAlgorithms;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        PrintWriter out = commandLine.getOut(); // NOPMD - picocli's stream
        PrintWriter err = commandLine.getErr(); // NOPMD - picocli's stream
        if (listAlgorithms) {
            out.print(algorithmTable());
            out.flush();
            return ExitCodes.OK;
        }
        if (input == null) {
            throw new CommandLine.ParameterException(commandLine, "Give the file to encrypt");
        }
        CliSupport.requireFile(commandLine, input, "Input");
        if (binary && (!xpaths.isEmpty() || content)) {
            throw new CommandLine.ParameterException(commandLine, "--binary encrypts the whole file: no --xpath or --content");
        }
        Algorithm data = algorithm(commandLine, Kind.DATA, dataAlgorithm, "--data-alg");
        List<Recipient> recipients = recipients(commandLine);
        if (recipients.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Give at least one recipient: --cert, --public-key, --keystore/--alias, --key-file or --password");
        }
        CliSupport.initServices();
        EncryptionRequest request = new EncryptionRequest(xpaths, namespaces,
                content ? TargetType.CONTENT : TargetType.ELEMENT, data, recipients, properties, keysOutside);
        Path target = output != null ? output : EncryptionActionRunner.sibling(input, ".encrypted.xml");
        EncryptionActionRunner.Result result = binary
                ? EncryptionActionRunner.encryptBinaryFile(input, target, mimeType, request)
                : EncryptionActionRunner.encryptFile(input, target, request);
        result.warnings().forEach(w -> err.println("fxt-cli: warning: " + w));
        if (!result.ok()) {
            err.println("fxt-cli: " + result.message());
            return ExitCodes.FAILED;
        }
        if (parent == null || !parent.quiet) {
            err.println(result.message());
            err.println("Wrote " + target);
        }
        return ExitCodes.OK;
    }

    private List<Recipient> recipients(CommandLine commandLine) throws Exception {
        List<Recipient> recipients = new ArrayList<>();
        for (Path file : certificates) {
            CliSupport.requireFile(commandLine, file, "Certificate");
            for (X509Certificate certificate : KeyMaterial.certificates(file)) {
                recipients.add(publicKeyRecipient(commandLine, certificate.getPublicKey(), certificate));
            }
        }
        for (Path file : publicKeys) {
            CliSupport.requireFile(commandLine, file, "Public key");
            recipients.add(publicKeyRecipient(commandLine, KeyMaterial.publicKey(file), null));
        }
        if (keystore != null) {
            CliSupport.requireFile(commandLine, keystore, "Keystore");
            if (aliases.isEmpty()) {
                throw new CommandLine.ParameterException(commandLine, "--keystore needs --alias (the recipient's certificate)");
            }
            char[] storePass = CliSecrets.resolve(commandLine, storePassword, storePasswordEnv, "--storepass");
            for (String alias : aliases) {
                X509Certificate certificate = KeyMaterial.certificate(keystore, storePass, alias);
                recipients.add(publicKeyRecipient(commandLine, certificate.getPublicKey(), certificate));
            }
        } else if (!aliases.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine, "--alias belongs to --keystore");
        }
        boolean direct = "none".equalsIgnoreCase(keyWrap);
        Algorithm wrap = keyWrap == null || direct ? null : algorithm(commandLine, Kind.KEY_WRAP, keyWrap, "--key-wrap");
        if (keyFile != null) {
            CliSupport.requireFile(commandLine, keyFile, "Key file");
            Algorithm data = algorithm(commandLine, Kind.DATA, dataAlgorithm, "--data-alg");
            Algorithm keyWrapAlgorithm = direct ? null
                    : wrap != null ? wrap : XmlEncryptionAlgorithms.DEFAULT_KEY_WRAP;
            String jce = direct ? data.jceKeyAlgorithm() : keyWrapAlgorithm.jceKeyAlgorithm();
            SecretKey key = KeyMaterial.symmetricKey(keyFile, jce);
            if (!direct) {
                keyWrapAlgorithm = fitWrap(keyWrapAlgorithm, key, wrap != null);
            }
            recipients.add(new SymmetricRecipient(key, keyName, keyWrapAlgorithm, recipientName));
        }
        char[] pass = CliSecrets.resolve(commandLine, password, passwordEnv, "--password");
        if (pass != null) {
            if (pass.length == 0) {
                throw new CommandLine.ParameterException(commandLine, "The password is empty");
            }
            if (direct) {
                throw new CommandLine.ParameterException(commandLine, "--key-wrap none is for --key-file only");
            }
            recipients.add(new PasswordRecipient(pass, wrap, iterations, prfUri(commandLine), recipientName));
        }
        return recipients;
    }

    /** An AES key wrap matching the key length when none was asked for (a 16-byte key: kw-aes128). */
    private static Algorithm fitWrap(Algorithm wrap, SecretKey key, boolean explicit) {
        int bits = key.getEncoded().length * 8;
        if (explicit || wrap.keyBits() == bits || !"AES".equals(wrap.jceKeyAlgorithm())) {
            return wrap;
        }
        return XmlEncryptionAlgorithms.keyWrapFor(bits);
    }

    private PublicKeyRecipient publicKeyRecipient(CommandLine commandLine, PublicKey key, X509Certificate certificate) {
        Algorithm method = XmlEncryptionService.defaultMethodFor(key);
        if (keyTransport != null && method.kind() == Kind.KEY_TRANSPORT) {
            method = algorithm(commandLine, Kind.KEY_TRANSPORT, keyTransport, "--key-transport");
        }
        X509Reference reference = certificate == null ? X509Reference.NONE : switch (x509.toLowerCase(Locale.ROOT)) {
            case "certificate" -> X509Reference.CERTIFICATE;
            case "ski" -> X509Reference.SKI;
            case "subject" -> X509Reference.SUBJECT_NAME;
            case "none" -> X509Reference.NONE;
            case "issuer-serial" -> X509Reference.ISSUER_SERIAL;
            default -> throw new CommandLine.ParameterException(commandLine, "Unknown --x509 " + x509);
        };
        return new PublicKeyRecipient(key, certificate, method,
                oaepDigest == null ? null : algorithm(commandLine, Kind.DIGEST, oaepDigest, "--oaep-digest"),
                mgf == null ? null : algorithm(commandLine, Kind.MGF, mgf, "--mgf"),
                keyWrap == null || "none".equalsIgnoreCase(keyWrap) ? null
                        : algorithm(commandLine, Kind.KEY_WRAP, keyWrap, "--key-wrap"),
                kdf == null ? null : algorithm(commandLine, Kind.KEY_DERIVATION, kdf, "--kdf"),
                kdfDigest == null ? null : algorithm(commandLine, Kind.DIGEST, kdfDigest, "--kdf-digest"),
                reference, recipientName);
    }

    private String prfUri(CommandLine commandLine) {
        return switch (prf.toLowerCase(Locale.ROOT)) {
            case "sha256", "hmac-sha256" -> XmlEncryptionService.HMAC_SHA256;
            case "sha384", "hmac-sha384" -> XmlEncryptionService.HMAC_SHA384;
            case "sha512", "hmac-sha512" -> XmlEncryptionService.HMAC_SHA512;
            default -> throw new CommandLine.ParameterException(commandLine, "Unknown --prf " + prf);
        };
    }

    static Algorithm algorithm(CommandLine commandLine, Kind kind, String value, String option) {
        return XmlEncryptionAlgorithms.parse(kind, value).orElseThrow(() -> new CommandLine.ParameterException(commandLine,
                "Unknown " + option + " '" + value + "'; one of: " + String.join(", ",
                        XmlEncryptionAlgorithms.of(kind).stream().map(Algorithm::id).toList())));
    }

    /** The algorithms by kind, with name, URI and legacy marker. */
    static String algorithmTable() {
        StringBuilder out = new StringBuilder();
        for (Kind kind : Kind.values()) {
            out.append(switch (kind) {
                case DATA -> "Data (--data-alg)";
                case KEY_TRANSPORT -> "Key transport, RSA (--key-transport)";
                case KEY_WRAP -> "Key wrap (--key-wrap)";
                case KEY_AGREEMENT -> "Key agreement (chosen by the key type)";
                case KEY_DERIVATION -> "Key derivation (--kdf; pbkdf2 with --password)";
                case DIGEST -> "Digests (--oaep-digest, --kdf-digest)";
                case MGF -> "Mask generation (--mgf)";
            }).append('\n');
            for (Algorithm a : XmlEncryptionAlgorithms.of(kind)) {
                out.append(String.format("  %-16s %-48s %s%n", a.id(), a.uri(), a.legacy() ? "legacy" : ""));
            }
        }
        return out.toString();
    }
}
