package org.fxt.freexmltoolkit.plugins.findatex;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;

/**
 * The field definitions of the FinDatEx templates EMT, EET, EPT and TPT (V7 and V8), extracted from
 * their official specifications by {@code scripts/findatex-fields.py} into
 * {@code plugins/findatex/templates.json}. Several versions of one template share their version field;
 * {@link #forVersion} picks the one a file names.
 */
public final class FinDatExTemplates {

    /** A field: its code ({@code 00010_…}), what it holds and how it is coded. */
    public static final class Field {
        public String code;
        public String definition;
        public String codification;
        /** M(andatory), O(ptional), C(onditional), I(ndicative), N(o longer maintained) */
        public String flag;
        /** 0 for the first section of the template */
        public int section;
        /** version, datetime, date, identifier, identifierType, currency, lei, integer, number, text, enum */
        public String type;
        public List<String> values;
        public Integer min;
        public Integer max;
        /** TPT: the CIC categories (0-9, A-F) the field applies to; {@code null} for the other templates */
        public String cic;
        /** For an identifier: the field that says how it is coded */
        public String typeField;
        /** For an identifier: what identification type 1 means ({@code isin} or {@code lei}) */
        public String type1;

        /**
         * @return whether the field is mandatory for every instrument: in a general section of EMT/EET/EPT,
         *         for all CIC categories in TPT
         */
        public boolean isGenerallyMandatory() {
            if (!"M".equals(flag)) {
                return false;
            }
            return cic == null ? section <= 1 : cic.length() == ALL_CIC_CATEGORIES.length();
        }

        /**
         * @param category the instrument's CIC category (the third character of its CIC code), or
         *                 {@code 0} when it is unknown
         * @return whether the field must be filled for such an instrument
         */
        public boolean isMandatoryFor(char category) {
            if (cic == null || category == 0) {
                return isGenerallyMandatory();
            }
            return "M".equals(flag) && cic.indexOf(Character.toUpperCase(category)) >= 0;
        }
    }

    /** The CIC categories: 0-9 and A-F. */
    public static final String ALL_CIC_CATEGORIES = "0123456789ABCDEF";

    /** A template. */
    public static final class Template {
        public String template;
        public String version;
        public List<Field> fields;
        /** The field naming an instrument in messages (TPT: its identification code), or {@code null} */
        public String label;
        private transient Map<String, Field> byCode;

        public Optional<Field> field(String code) {
            if (byCode == null) {
                byCode = fields.stream().collect(Collectors.toMap(f -> f.code, Function.identity(), (a, b) -> a));
            }
            return Optional.ofNullable(byCode.get(code));
        }

        /** @return the code of the version field ({@code 00001_EMT_Version}) */
        public String versionField() {
            return fields.stream().filter(f -> "version".equals(f.type)).map(f -> f.code).findFirst().orElse("");
        }

        /** @return whether a file's version value names this template's version ({@code V7.0}, {@code V7}) */
        public boolean matchesVersion(String value) {
            String wanted = normalize(value);
            return field(versionField()).map(f -> f.values == null ? List.of(version) : f.values).orElse(List.of(version))
                    .stream().anyMatch(v -> normalize(v).equals(wanted));
        }

        /** @return {@code "TPT V8.0"} */
        public String title() {
            return template + " " + version;
        }
    }

    /** {@code " v7.0 "} and {@code "V7"} are the same version. */
    static String normalize(String version) {
        String text = version == null ? "" : version.strip().toUpperCase(java.util.Locale.ROOT);
        while (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text;
    }

    private static final class Catalog {
        String source;
        List<Template> templates;
    }

    private static volatile List<Template> templates;

    private FinDatExTemplates() {
    }

    /** @return EMT, EET, EPT and TPT (each version, oldest first) */
    public static List<Template> all() {
        if (templates == null) {
            try (InputStream in = FinDatExTemplates.class.getResourceAsStream("/plugins/findatex/templates.json")) {
                if (in == null) {
                    throw new IOException("plugins/findatex/templates.json is missing");
                }
                Catalog catalog = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Catalog.class);
                templates = List.copyOf(catalog.templates);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return templates;
    }

    /** @return the templates whose version field is {@code code} (several versions of one template), oldest first */
    public static List<Template> byVersionField(String code) {
        return all().stream().filter(t -> t.versionField().equals(code)).toList();
    }

    /**
     * @return of templates sharing a version field, the one whose version a file names; the newest when
     *         the value names none of them (the check then reports the version)
     */
    public static Template forVersion(List<Template> candidates, String value) {
        return candidates.stream().filter(t -> t.matchesVersion(value)).findFirst().orElse(candidates.getLast());
    }

    /** @return the newest version of a template ({@code EMT}, {@code TPT}) */
    public static Optional<Template> byName(String name) {
        return all().stream().filter(t -> t.template.equals(name)).reduce((first, second) -> second);
    }

    /** @return a template in a given version ({@code TPT}, {@code V7.0}) */
    public static Optional<Template> byName(String name, String version) {
        return all().stream().filter(t -> t.template.equals(name) && t.matchesVersion(version)).findFirst();
    }
}
