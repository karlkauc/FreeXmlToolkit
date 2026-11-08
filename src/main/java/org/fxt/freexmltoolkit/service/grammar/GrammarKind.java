package org.fxt.freexmltoolkit.service.grammar;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** The schema languages an XML document can be validated against. */
public enum GrammarKind {
    XSD("XSD"),
    DTD("DTD"),
    RELAX_NG("RELAX NG"),
    RELAX_NG_COMPACT("RELAX NG");

    private final String label;

    GrammarKind(String label) {
        this.label = label;
    }

    /** @return the name shown in the status bar and messages */
    public String label() {
        return label;
    }

    /** @return whether this is a DTD or RELAX NG grammar (validated without XSD) */
    public boolean isNonXsd() {
        return this != XSD;
    }

    /** @return the kind of a schema file by its extension (.xsd, .dtd, .rng, .rnc) */
    public static Optional<GrammarKind> ofFile(File file) {
        return file == null ? Optional.empty() : ofName(file.getName());
    }

    public static Optional<GrammarKind> ofFile(Path file) {
        return file == null || file.getFileName() == null ? Optional.empty() : ofName(file.getFileName().toString());
    }

    public static Optional<GrammarKind> ofName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dtd") || lower.endsWith(".ent") || lower.endsWith(".mod")) {
            return Optional.of(DTD);
        }
        if (lower.endsWith(".rng")) {
            return Optional.of(RELAX_NG);
        }
        if (lower.endsWith(".rnc")) {
            return Optional.of(RELAX_NG_COMPACT);
        }
        if (lower.endsWith(".xsd")) {
            return Optional.of(XSD);
        }
        return Optional.empty();
    }
}
