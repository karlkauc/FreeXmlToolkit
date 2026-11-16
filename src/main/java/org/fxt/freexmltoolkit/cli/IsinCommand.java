package org.fxt.freexmltoolkit.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;
import org.fxt.freexmltoolkit.service.finance.isin.Isin;
import org.fxt.freexmltoolkit.service.finance.isin.IsinLookup;
import org.fxt.freexmltoolkit.service.finance.isin.IsinRecord;
import org.fxt.freexmltoolkit.service.finance.isin.IsinScanner;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code fxt-cli isin}: looks ISINs up at ESMA FIRDS (OpenFIGI as fallback) - given on the command line or in files. */
@Command(name = "isin",
        mixinStandardHelpOptions = true,
        header = "Look ISINs up at ESMA FIRDS (OpenFIGI as fallback): name, CFI code, issuer LEI, EU trading.",
        description = {
                "Asks ESMA FIRDS (EU reference data) about the given ISINs and every ISIN found in the files given with "
                        + "--file; ISINs FIRDS does not know are asked at OpenFIGI (an API key from FXT_OPENFIGI_APIKEY "
                        + "raises its limits). Answers are cached for a day. Exit code 1 when an ISIN is invalid or known "
                        + "to neither service."
        },
        footerHeading = "%nExamples:%n",
        footer = {
                "  # One ISIN",
                "  fxt-cli isin IE00B4L5Y983",
                "",
                "  # Every ISIN of a report, FIRDS only, as JSON",
                "  fxt-cli isin --file holdings.xml --no-openfigi -f json"
        },
        sortOptions = false)
final class IsinCommand implements Callable<Integer> {

    enum Format { TEXT, JSON }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Parameters(arity = "0..*", paramLabel = "ISIN", description = "ISINs to look up.")
    List<String> isins = new ArrayList<>();

    @Option(names = "--file", paramLabel = "FILE", description = "Look up every ISIN found in this file (repeatable).")
    List<Path> files = new ArrayList<>();

    @Option(names = "--no-openfigi", description = "Ask only ESMA FIRDS, never OpenFIGI.")
    boolean noOpenFigi;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "text or json (default: ${DEFAULT-VALUE}).")
    String format;

    @Override
    public Integer call() throws Exception {
        try {
            return run();
        } finally {
            Isin.setOpenFigiOverride(null);
        }
    }

    private Integer run() throws Exception {
        CommandLine commandLine = spec.commandLine();
        Format fmt;
        try {
            fmt = Format.valueOf(format.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(commandLine, "--format: text or json, not " + format);
        }
        if (isins.isEmpty() && files.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine, "Give ISINs or --file");
        }
        CliSupport.initServices();
        if (!Isin.allowed()) {
            commandLine.getErr().println("fxt-cli isin: ISIN lookups are turned off by the administrator ("
                    + AdminPolicy.KEY_ISIN_ALLOWED + "=false)");
            return ExitCodes.NOT_RUN;
        }
        if (noOpenFigi) {
            Isin.setOpenFigiOverride(false);
        }
        Set<String> wanted = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String isin : isins) {
            String i = isin.strip().toUpperCase(Locale.ROOT);
            String problem = FinancialIdentifiers.checkIsin(i);
            if (problem == null) {
                wanted.add(i);
            } else {
                invalid.add(problem);
            }
        }
        for (Path file : files) {
            CliSupport.requireFile(commandLine, file, "File");
            wanted.addAll(IsinScanner.distinct(IsinScanner.scan(CliSupport.read(file))));
        }
        Map<String, IsinRecord> records;
        try {
            records = wanted.isEmpty() ? Map.of() : Isin.cache().lookup(wanted);
        } catch (IsinLookup.LookupException e) {
            commandLine.getErr().println("fxt-cli isin: " + e.getMessage());
            return ExitCodes.NOT_RUN;
        }
        var out = commandLine.getOut(); // NOPMD - picocli's stream, owned by CommandLine
        boolean problems = !invalid.isEmpty();
        if (fmt == Format.JSON) {
            JsonArray array = new JsonArray();
            for (IsinRecord r : records.values()) {
                IsinRecord.Problem p = r.problem();
                problems |= p != null && p.severity() == IsinRecord.Severity.WARNING;
                array.add(json(r, p));
            }
            JsonObject root = new JsonObject();
            root.add("records", array);
            JsonArray bad = new JsonArray();
            invalid.forEach(bad::add);
            root.add("invalid", bad);
            out.println(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
        } else {
            invalid.forEach(out::println);
            for (IsinRecord r : records.values()) {
                IsinRecord.Problem p = r.problem();
                problems |= p != null && p.severity() == IsinRecord.Severity.WARNING;
                out.println(r.summary());
                if (p != null) {
                    out.println("  " + p.severity().name().toLowerCase(Locale.ROOT) + ": " + p.message());
                }
            }
            if (records.isEmpty() && invalid.isEmpty()) {
                out.println("No ISIN found.");
            }
        }
        out.flush();
        return problems ? ExitCodes.FAILED : ExitCodes.OK;
    }

    private static JsonObject json(IsinRecord r, IsinRecord.Problem p) {
        JsonObject o = new JsonObject();
        o.addProperty("isin", r.isin());
        o.addProperty("found", r.found());
        o.addProperty("source", r.source().name());
        o.addProperty("name", r.name());
        o.addProperty("cfi", r.cfi());
        o.addProperty("issuerLei", r.issuerLei());
        o.addProperty("currency", r.currency());
        o.addProperty("activeVenues", r.activeVenues());
        o.addProperty("totalVenues", r.totalVenues());
        o.addProperty("terminatedSince", r.terminatedSince() == null ? null : r.terminatedSince().toString());
        o.addProperty("figi", r.figi());
        o.addProperty("ticker", r.ticker());
        o.addProperty("securityType", r.securityType());
        o.addProperty("problem", p == null ? null : p.message());
        o.addProperty("severity", p == null ? null : p.severity().name().toLowerCase(Locale.ROOT));
        return o;
    }
}
