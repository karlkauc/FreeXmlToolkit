package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Locale;

import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * What kind of problem a finding is - the filter and grouping of the PROBLEMS panel and the Excel export. Each
 * category belongs to a {@link Group}. Sources set the category (online identifier check, FMA rules, extension
 * rules); {@link #of(ValidationProblem)} derives it for the others from source, rule and severity.
 */
public enum ProblemCategory {

    WELL_FORMED(Group.DOCUMENT),
    SCHEMA(Group.DOCUMENT),
    SCHEMATRON(Group.DOCUMENT),

    LEI_CHECK_DIGITS(Group.LEI),
    LEI_NOT_REGISTERED(Group.LEI),
    LEI_LAPSED(Group.LEI),
    LEI_PENDING(Group.LEI),

    ISIN_UNKNOWN(Group.ISIN),
    ISIN_CFI(Group.ISIN),
    ISIN_ISSUER(Group.ISIN),
    ISIN_NOT_TRADED(Group.ISIN),

    FMA_MASTER_DATA(Group.FMA),
    FMA_FB800(Group.FMA),
    FMA_RECONCILIATION(Group.FMA),
    FMA_ASSETS(Group.FMA),
    FMA_LOOK_THROUGH(Group.FMA),
    FMA_ALLOCATION(Group.FMA),
    FMA_FB900(Group.FMA),
    FMA_TOTALS(Group.FMA),
    FMA_ADDITIONAL(Group.FMA),

    PLUGIN_RULE(Group.EXTENSION),

    ONLINE_SERVICE(Group.OTHER),
    OTHER(Group.OTHER);

    /** The groups of the categories, in display order. */
    public enum Group {
        DOCUMENT, LEI, ISIN, FMA, EXTENSION, OTHER;

        /** @return the group's name in the user's language */
        public String title() {
            return I18n.t("ProblemCategory.group." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final Group group;

    ProblemCategory(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }

    /** @return the category's name in the user's language */
    public String title() {
        return I18n.t("ProblemCategory." + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /** @return "ISIN: CFI code differs" */
    public String fullTitle() {
        return group.title() + ": " + title();
    }

    /** @return the FMA category of a PK rule group ({@code stamm}, {@code fb800} …), {@link #FMA_ADDITIONAL} for INT_ rules */
    public static ProblemCategory ofFmaGroup(String group, boolean additional) {
        if (additional) {
            return FMA_ADDITIONAL;
        }
        return switch (group == null ? "" : group.strip().toLowerCase(Locale.ROOT)) {
            case "stamm" -> FMA_MASTER_DATA;
            case "fb800" -> FMA_FB800;
            case "abgl" -> FMA_RECONCILIATION;
            case "assets" -> FMA_ASSETS;
            case "lt" -> FMA_LOOK_THROUGH;
            case "alloc" -> FMA_ALLOCATION;
            case "fb900" -> FMA_FB900;
            case "gesamt" -> FMA_TOTALS;
            default -> FMA_ADDITIONAL;
        };
    }

    /** @return the category of a problem whose source did not set one (from source, rule and severity) */
    public static ProblemCategory of(ValidationProblem p) {
        String source = p.source() == null ? "" : p.source();
        String rule = p.ruleId() == null ? "" : p.ruleId();
        String message = p.message() == null ? "" : p.message();
        if (message.startsWith("Not checked online")) {
            return ONLINE_SERVICE;
        }
        switch (source) {
            case "Well-formed":
                return WELL_FORMED;
            case "XSD", "DTD", "RELAX NG", "RNG", "Grammar", "XML", "JSON Schema":
                return SCHEMA;
            case "Schematron":
                return SCHEMATRON;
            case "GLEIF":
                if ("LEI-CHECK-DIGITS".equals(rule)) {
                    return LEI_CHECK_DIGITS;
                }
                return switch (p.level()) {
                    case INFO -> LEI_PENDING;
                    case WARNING -> LEI_LAPSED;
                    case ERROR -> LEI_NOT_REGISTERED;
                };
            case "ISIN":
                if ("ISIN-CFI".equals(rule)) {
                    return ISIN_CFI;
                }
                if ("ISIN-ISSUER".equals(rule)) {
                    return ISIN_ISSUER;
                }
                return p.level() == ValidationProblem.Level.INFO ? ISIN_NOT_TRADED : ISIN_UNKNOWN;
            default:
                return source.isBlank() ? OTHER : PLUGIN_RULE;
        }
    }
}
