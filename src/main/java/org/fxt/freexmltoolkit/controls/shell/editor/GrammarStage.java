package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.grammar.DeclaredGrammar;
import org.fxt.freexmltoolkit.service.grammar.DtdValidator;
import org.fxt.freexmltoolkit.service.grammar.GrammarKind;
import org.fxt.freexmltoolkit.service.grammar.GrammarPolicy;
import org.fxt.freexmltoolkit.service.grammar.GrammarProblem;
import org.fxt.freexmltoolkit.service.grammar.GrammarResolver;
import org.fxt.freexmltoolkit.service.grammar.RelaxNgValidator;

/**
 * The DTD / RELAX NG stage of {@link ValidationRunner}: a bound {@code .dtd}, {@code .rng} or
 * {@code .rnc} file, or — without a bound schema — the grammar the document declares. A DTD that
 * cannot be loaded (offline, not in a catalog) does not make the document look invalid: it is
 * reported as a warning, and the document is checked for well-formedness instead.
 */
final class GrammarStage {

    private GrammarStage() {
    }

    /** @return the problems, or {@code null} when the XSD / well-formedness stage applies */
    static List<ValidationProblem> validate(String xml, File bound, Path documentPath) {
        String systemId = documentPath == null ? null : documentPath.toAbsolutePath().toUri().toString();
        if (bound != null) {
            GrammarKind kind = GrammarKind.ofFile(bound).orElse(GrammarKind.XSD);
            return switch (kind) {
                // The document's own DOCTYPE wins over a bound DTD (it may hold an internal subset too)
                case DTD -> dtd(xml, systemId, DeclaredGrammar.of(xml).filter(d -> d.kind() == GrammarKind.DTD)
                        .isPresent() ? null : bound);
                case RELAX_NG, RELAX_NG_COMPACT -> problems("RELAX NG", RelaxNgValidator.validate(xml, systemId, bound));
                default -> null;
            };
        }
        Optional<DeclaredGrammar> declared = DeclaredGrammar.of(xml);
        if (declared.isEmpty()) {
            return null;
        }
        DeclaredGrammar grammar = declared.get();
        if (grammar.kind() == GrammarKind.DTD) {
            return GrammarPolicy.dtdAllowed() ? dtd(xml, systemId, null) : null;
        }
        try {
            Path schema = GrammarResolver.resolveToFile(null, grammar.systemId(), systemId);
            return problems("RELAX NG", RelaxNgValidator.validate(xml, systemId, schema.toFile()));
        } catch (IOException e) {
            List<ValidationProblem> result = wellFormed(xml);
            result.add(0, new ValidationProblem("RELAX NG", "warning", 0, "The declared RELAX NG schema "
                    + grammar.systemId() + " is not available (" + e.getMessage()
                    + "); the document was checked for well-formedness only."));
            return result;
        }
    }

    private static List<ValidationProblem> dtd(String xml, String systemId, File dtd) {
        List<GrammarProblem> found = DtdValidator.validate(xml, systemId, dtd);
        boolean unavailable = found.stream().anyMatch(p -> p.message() != null && p.message().startsWith("Cannot load the DTD"));
        if (unavailable && dtd == null) {
            List<ValidationProblem> result = wellFormed(xml);
            String reason = found.stream().filter(p -> p.message().startsWith("Cannot load the DTD"))
                    .map(GrammarProblem::message).findFirst().orElse("");
            result.add(0, new ValidationProblem("DTD", "warning", 0,
                    reason + " — the document was checked for well-formedness only."));
            return result;
        }
        return problems("DTD", found);
    }

    private static List<ValidationProblem> wellFormed(String xml) {
        List<ValidationProblem> result = new ArrayList<>();
        for (var e : org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.XmlService.class)
                .validateText(xml, null)) {
            result.add(new ValidationProblem("Well-formed", "error", e.getLineNumber(), e.getMessage())
                    .withColumn(e.getColumnNumber()));
        }
        return result;
    }

    private static List<ValidationProblem> problems(String source, List<GrammarProblem> found) {
        List<ValidationProblem> result = new ArrayList<>();
        for (GrammarProblem p : found) {
            String severity = "warning".equals(p.severity()) ? "warning" : "error";
            boolean inGrammar = p.systemId() != null && !p.systemId().isBlank() && p.line() > 0
                    && !p.systemId().endsWith(".xml") && (p.systemId().endsWith(".dtd") || p.systemId().endsWith(".rng")
                    || p.systemId().endsWith(".rnc") || p.systemId().endsWith(".mod") || p.systemId().endsWith(".ent"));
            // A problem inside the grammar file has no line in the document
            result.add(new ValidationProblem(source, severity, inGrammar ? 0 : p.line(),
                    inGrammar ? p.message() + " (" + p.systemId().replaceFirst(".*/", "") + ", line " + p.line() + ")"
                            : p.message()).withColumn(inGrammar ? 0 : p.column()));
        }
        return result;
    }
}
