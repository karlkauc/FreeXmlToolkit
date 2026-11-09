package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.service.DataFormatConverter;
import org.fxt.freexmltoolkit.service.DataFormatConverter.ConversionException;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Format;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Mapping;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Options;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli convert}: converts between XML, JSON and YAML with {@link DataFormatConverter},
 * the converter behind Tools → Convert to JSON / XML / YAML.
 */
@Command(name = "convert",
        mixinStandardHelpOptions = true,
        header = "Convert between XML, JSON and YAML.",
        footer = {
                "  # XML to JSON (target format from the input)",
                "  fxt-cli convert order.xml -o order.json",
                "",
                "  # JSON to XML with a root element; YAML to JSON",
                "  fxt-cli convert order.json -t xml --root order -o order.xml",
                "  fxt-cli convert config.yaml -t json",
                "",
                "  # Lossless W3C mapping (fn:json-to-xml vocabulary)",
                "  fxt-cli convert data.json -t xml -m w3c"
        },
        description = "Convert between XML, JSON and YAML.",
        sortOptions = false)
final class ConvertCommand implements Callable<Integer> {

    /** Input formats; {@code null} = by file extension. */
    enum From { XML, JSON, YAML }

    /** Output formats. */
    enum To { JSON, XML, YAML }

    /** Mappings (lower-case for the command line). */
    enum MappingOption { structured, w3c }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(arity = "1..*", paramLabel = "INPUT", description = "Files to convert.")
    List<Path> inputs;

    @Option(names = {"-t", "--to"}, paramLabel = "FORMAT",
            description = "json, xml or yaml; default: json for XML input, xml for JSON and YAML input.")
    To to;

    @Option(names = "--from", paramLabel = "FORMAT",
            description = "xml, json or yaml; default: by extension (.json/.jsonc/.json5, .yaml/.yml, else xml).")
    From from;

    @Option(names = {"-m", "--mapping"}, paramLabel = "MAPPING", defaultValue = "structured",
            description = "structured (readable, any XML; default) or w3c (XPath 3.1 fn:json-to-xml vocabulary, lossless).")
    MappingOption mapping;

    @Option(names = "--typed", description = "XML to JSON: numbers and true/false become JSON numbers and booleans.")
    boolean typed;

    @Option(names = "--root", paramLabel = "NAME", defaultValue = "json",
            description = "JSON to XML: root element when the JSON does not name one (default: ${DEFAULT-VALUE}).")
    String root;

    @Option(names = "--indent", paramLabel = "N", defaultValue = "2",
            description = "Spaces per indentation level, 0 = compact (default: ${DEFAULT-VALUE}).")
    int indent;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE",
            description = "Output file (one input). Without -o and --output-dir the result goes to standard output.")
    Path output;

    @Option(names = {"-d", "--output-dir"}, paramLabel = "DIR",
            description = "Output folder; each result is named after its input with the new extension.")
    Path outputDir;

    @Override
    public Integer call() {
        CommandLine commandLine = spec.commandLine();
        if (output != null && outputDir != null) {
            throw new CommandLine.ParameterException(commandLine, "Use either --output or --output-dir");
        }
        if (inputs.size() > 1 && outputDir == null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Several inputs need --output-dir (one result file per input)");
        }
        for (Path input : inputs) {
            CliSupport.requireFile(commandLine, input, "Input");
        }
        Options options = new Options(mapping == MappingOption.w3c ? Mapping.W3C : Mapping.STRUCTURED, typed, root, indent);
        boolean quiet = parent != null && parent.quiet;
        int exit = ExitCodes.OK;
        for (Path input : inputs) {
            Format source = from != null ? Format.valueOf(from.name()) : Format.ofFileName(input.getFileName().toString());
            Format target = to != null ? Format.valueOf(to.name()) : source == Format.XML ? Format.JSON : Format.XML;
            try {
                String result = DataFormatConverter.convert(CliSupport.read(input), source, target, options);
                Path destination = output != null ? output
                        : outputDir != null ? outputDir.resolve(CliSupport.baseName(input) + "." + target.extension())
                        : null;
                if (destination == null) {
                    commandLine.getOut().print(result);
                    commandLine.getOut().flush();
                } else {
                    CliSupport.write(destination, result);
                    if (!quiet) {
                        commandLine.getErr().println("Wrote " + destination);
                    }
                }
            } catch (ConversionException e) {
                commandLine.getErr().println(input + ": error: " + e.getMessage());
                exit = ExitCodes.FAILED;
            } catch (IOException e) {
                commandLine.getErr().println(input + ": error: " + e.getMessage());
                exit = ExitCodes.FAILED;
            }
        }
        return exit;
    }
}
