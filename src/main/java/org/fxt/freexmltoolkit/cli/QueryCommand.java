package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli query}: evaluates an XPath 3.1, XQuery 3.1 or JSONPath expression against files,
 * like the Query Console of the application ({@link TransformRunner}). Exits like grep: 0 when a
 * result was found, 1 when all results are empty.
 */
@Command(name = "query",
        mixinStandardHelpOptions = true,
        header = "Evaluate an XPath, XQuery or JSONPath expression against files.",
        footer = {
                "  # How many positions does the fund have?",
                "  fxt-cli query -e 'count(//Position)' fund.xml",
                "",
                "  # The ISINs of all positions above 10 %, in every XML file of a folder",
                "  fxt-cli query -e '//Position[xs:decimal(TotalPercentage) gt 10]/Identifiers/ISIN/string()' data/",
                "",
                "  # An XQuery file with an external variable; a query without input",
                "  fxt-cli query --query-file report.xq --var year=2026 orders.xml",
                "  fxt-cli query -l xquery -e 'string-join((1 to 5) ! string(), \",\")'",
                "",
                "  # JSONPath (chosen for .json files); input from a pipe",
                "  fxt-cli query -e '$.items[*].name' data.json",
                "  curl -s https://example.org/feed.xml | fxt-cli query -e '//title/string()' -"
        },
        description = {
                "Evaluates an expression with Saxon (XPath and XQuery 3.1; map:, array: and math: are declared) "
                        + "or JSONPath, against each file, a folder's files or standard input (-).",
                "Exit code 0 when at least one result is not empty, 1 when all are empty, 3 when the expression "
                        + "or an input fails (the other inputs still run)."
        },
        sortOptions = false)
final class QueryCommand implements Callable<Integer> {

    /** Query languages. */
    enum Language { xpath, xquery, jsonpath }

    /** Output formats. */
    enum Format { text, json }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-e", "--expression"}, paramLabel = "EXPR", description = "The expression.")
    String expression;

    @Option(names = "--query-file", paramLabel = "FILE",
            description = "Read the expression from FILE instead (.xq, .xquery, .xqy, .xql, .xqm: XQuery).")
    Path queryFile;

    @Option(names = {"-l", "--language"}, paramLabel = "LANG",
            description = "xpath, xquery or jsonpath. Default: from --query-file's extension, jsonpath for "
                    + ".json inputs, else xpath.")
    Language language;

    @Option(names = "--var", paramLabel = "NAME=VALUE",
            description = "An external XQuery variable (string value); repeatable.")
    Map<String, String> variables = new LinkedHashMap<>();

    @Parameters(arity = "0..*", paramLabel = "INPUT",
            description = "Files, folders (searched recursively, see --include) or - for standard input.")
    List<Path> inputs = new ArrayList<>();

    @Option(names = "--include", split = ",", paramLabel = "GLOB", defaultValue = "*.xml,*.json",
            description = "File names to query inside folders (default: ${DEFAULT-VALUE}).")
    List<String> include;

    @Option(names = "--with-filename", negatable = true,
            description = "Prefix each result line with its file (default: when there is more than one input).")
    Boolean withFilename;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "text or json (one object per input: file, result, error). Default: ${DEFAULT-VALUE}.")
    Format format;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE", description = "Write the results to FILE.")
    Path output;

    /** The standard input, for {@code -}; tests replace it. */
    static java.util.function.Supplier<java.io.InputStream> stdin = () -> System.in;

    private static final Path STDIN = Path.of("-");

    private static final java.util.regex.Pattern XML_DECLARATION = java.util.regex.Pattern.compile("^\\s*<\\?xml\\s[^>]*\\?>\\s*");

    /** One input: a file or standard input. */
    private record Input(String name, Path file) {
    }

    @Override
    public Integer call() throws IOException {
        CommandLine commandLine = spec.commandLine();
        if ((expression == null) == (queryFile == null)) {
            throw new CommandLine.ParameterException(commandLine, "Give the expression with -e or --query-file (one of them)");
        }
        String query = expression;
        if (queryFile != null) {
            CliSupport.requireFile(commandLine, queryFile, "Query file");
            query = CliSupport.read(queryFile);
        }
        List<Input> sources = inputs(commandLine);
        Language lang = language != null ? language : languageFor(sources);
        if (sources.isEmpty() && lang != Language.xquery) {
            throw new CommandLine.ParameterException(commandLine,
                    "Give a file to query (only an XQuery runs without input)");
        }
        if (!variables.isEmpty() && lang != Language.xquery) {
            throw new CommandLine.ParameterException(commandLine, "--var is for XQuery only");
        }
        CliSupport.initServices();

        boolean prefix = withFilename != null ? withFilename : sources.size() > 1;
        StringBuilder text = new StringBuilder();
        JsonArray json = new JsonArray();
        boolean found = false;
        boolean failed = false;
        List<Input> runs = sources.isEmpty() ? java.util.Collections.singletonList(null) : sources;
        for (Input source : runs) {
            String name = source == null ? "" : source.name();
            String content;
            try {
                content = source == null ? "" : source.file() == STDIN
                        ? new String(stdin.get().readAllBytes(), StandardCharsets.UTF_8) : CliSupport.read(source.file());
            } catch (IOException e) {
                commandLine.getErr().println(name + ": error: cannot read: " + e.getMessage());
                failed = true;
                json.add(entry(name, null, "cannot read: " + e.getMessage()));
                continue;
            }
            String error = null;
            String result = null;
            if (lang == Language.xquery && !content.isBlank()) {
                // the engine would run an XQuery without context on a broken document
                error = XmlStructuralDiff.wellFormednessError(content);
            }
            if (error == null) {
                TransformRunner.QueryRun run = switch (lang) {
                    case xpath -> TransformRunner.xpath(content, query);
                    case jsonpath -> TransformRunner.jsonPath(content, query);
                    case xquery -> TransformRunner.xquery(content, query, new LinkedHashMap<>(variables),
                            XsltTransformationEngine.OutputFormat.XML);
                };
                if (run.failure() != null) {
                    error = run.text().startsWith("ERROR: ") ? run.text().substring(7) : run.text();
                } else {
                    // like grep, results only: the serializer's XML declaration is not one
                    result = stripTrailingNewline(XML_DECLARATION.matcher(run.text()).replaceFirst(""));
                }
            }
            if (error != null) {
                failed = true;
                commandLine.getErr().println((name.isEmpty() ? "" : name + ": ") + "error: " + error);
            } else if (!result.isBlank()) {
                found = true;
                if (format == Format.text) {
                    append(text, result, prefix ? name + ": " : "");
                }
            }
            json.add(entry(name, result, error));
        }

        String out = format == Format.json
                ? new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n"
                : text.toString();
        if (output != null) {
            CliSupport.write(output, out);
        } else {
            // picocli owns the stream: print and flush, never close it
            commandLine.getOut().print(out);
            commandLine.getOut().flush();
        }
        if (failed) {
            return ExitCodes.NOT_RUN;
        }
        return found ? ExitCodes.OK : ExitCodes.FAILED;
    }

    private List<Input> inputs(CommandLine commandLine) {
        List<Input> sources = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (Path input : inputs) {
            if (input.equals(STDIN)) {
                sources.add(new Input("-", STDIN));
            } else {
                files.add(input);
            }
        }
        for (Path file : CliSupport.expand(commandLine, files, include)) {
            sources.add(new Input(file.toString(), file));
        }
        return sources;
    }

    /** The language when none is given: from the query file's extension, else from the inputs. */
    private Language languageFor(List<Input> sources) {
        if (queryFile != null) {
            String ext = CliSupport.extension(queryFile).toLowerCase(Locale.ROOT);
            if (List.of("xq", "xquery", "xqy", "xql", "xqm").contains(ext)) {
                return Language.xquery;
            }
        }
        boolean allJson = !sources.isEmpty() && sources.stream().allMatch(
                s -> s.file() != STDIN && CliSupport.extension(s.file()).equalsIgnoreCase("json"));
        return allJson ? Language.jsonpath : Language.xpath;
    }

    private static JsonObject entry(String file, String result, String error) {
        JsonObject o = new JsonObject();
        if (!file.isEmpty()) {
            o.addProperty("file", file);
        }
        if (result != null) {
            o.addProperty("result", result);
        }
        if (error != null) {
            o.addProperty("error", error);
        }
        return o;
    }

    private static void append(StringBuilder text, String result, String prefix) {
        for (String line : result.split("\\R", -1)) {
            text.append(prefix).append(line).append('\n');
        }
    }

    private static String stripTrailingNewline(String text) {
        String t = text == null ? "" : text;
        while (t.endsWith("\n") || t.endsWith("\r")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
