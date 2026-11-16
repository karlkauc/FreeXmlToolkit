package org.fxt.freexmltoolkit.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;
import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifClient;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiRecord;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiScanner;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code fxt-cli lei}: looks LEIs up at GLEIF - given on the command line or found in files. */
@Command(name = "lei",
        mixinStandardHelpOptions = true,
        header = "Look LEIs up at GLEIF: legal name, country, registration and entity status.",
        description = {
                "Asks GLEIF's public API (api.gleif.org) about the given LEIs and about every LEI found in the files "
                        + "given with --file. Answers are cached for a day (Settings → GLEIF). Exit code 1 when an "
                        + "LEI is invalid, unknown, lapsed, retired or annulled, or its entity inactive."
        },
        footerHeading = "%nExamples:%n",
        footer = {
                "  # One LEI",
                "  fxt-cli lei 506700GE1G29325QX363",
                "",
                "  # Every LEI of a report, as JSON",
                "  fxt-cli lei --file aif-report.xml -f json"
        },
        sortOptions = false)
final class LeiCommand implements Callable<Integer> {

    enum Format { TEXT, JSON }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Parameters(arity = "0..*", paramLabel = "LEI", description = "LEIs to look up.")
    List<String> leis = new ArrayList<>();

    @Option(names = "--file", paramLabel = "FILE", description = "Look up every LEI found in this file (repeatable).")
    List<Path> files = new ArrayList<>();

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "text or json (default: ${DEFAULT-VALUE}).")
    String format;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        Format fmt;
        try {
            fmt = Format.valueOf(format.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(commandLine, "--format: text or json, not " + format);
        }
        if (leis.isEmpty() && files.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine, "Give LEIs or --file");
        }
        CliSupport.initServices();
        if (!Gleif.allowed()) {
            commandLine.getErr().println("fxt-cli lei: GLEIF lookups are turned off by the administrator ("
                    + AdminPolicy.KEY_GLEIF_ALLOWED + "=false)");
            return ExitCodes.NOT_RUN;
        }
        Set<String> wanted = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String lei : leis) {
            String l = lei.strip().toUpperCase(java.util.Locale.ROOT);
            String problem = FinancialIdentifiers.checkLei(l);
            if (problem == null) {
                wanted.add(l);
            } else {
                invalid.add(problem);
            }
        }
        for (Path file : files) {
            CliSupport.requireFile(commandLine, file, "File");
            wanted.addAll(LeiScanner.distinct(LeiScanner.scan(CliSupport.read(file))));
        }
        Map<String, LeiRecord> records;
        try {
            records = wanted.isEmpty() ? Map.of() : Gleif.cache().lookup(wanted);
        } catch (GleifClient.GleifException e) {
            commandLine.getErr().println("fxt-cli lei: " + e.getMessage());
            return ExitCodes.NOT_RUN;
        }
        var out = commandLine.getOut(); // NOPMD - picocli's stream, owned by CommandLine
        boolean problems = !invalid.isEmpty();
        if (fmt == Format.JSON) {
            JsonArray array = new JsonArray();
            for (LeiRecord r : records.values()) {
                LeiRecord.Problem p = r.problem();
                problems |= p != null && p.severity() != LeiRecord.Severity.INFO;
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
            for (LeiRecord r : records.values()) {
                LeiRecord.Problem p = r.problem();
                problems |= p != null && p.severity() != LeiRecord.Severity.INFO;
                out.println(r.summary());
                if (p != null) {
                    out.println("  " + p.severity().name().toLowerCase(java.util.Locale.ROOT) + ": " + p.message());
                }
            }
            if (records.isEmpty() && invalid.isEmpty()) {
                out.println("No LEI found.");
            }
        }
        out.flush();
        return problems ? ExitCodes.FAILED : ExitCodes.OK;
    }

    private static JsonObject json(LeiRecord r, LeiRecord.Problem p) {
        JsonObject o = new JsonObject();
        o.addProperty("lei", r.lei());
        o.addProperty("found", r.found());
        o.addProperty("legalName", r.legalName());
        o.addProperty("country", r.country());
        o.addProperty("city", r.city());
        o.addProperty("entityStatus", r.entityStatus());
        o.addProperty("registrationStatus", r.registrationStatus());
        o.addProperty("nextRenewal", r.nextRenewal() == null ? null : r.nextRenewal().toString());
        o.addProperty("managingLou", r.managingLou());
        o.addProperty("problem", p == null ? null : p.message());
        o.addProperty("severity", p == null ? null : p.severity().name().toLowerCase(java.util.Locale.ROOT));
        return o;
    }
}
