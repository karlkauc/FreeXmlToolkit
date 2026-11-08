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
 * The field definitions of the FinDatEx templates EMT, EET and EPT, extracted from their official
 * specifications by {@code scripts/findatex-fields.py} into {@code plugins/findatex/templates.json}.
 */
public final class FinDatExTemplates {

    /** A field: its code ({@code 00010_…}), what it holds and how it is coded. */
    public static final class Field {
        public String code;
        public String definition;
        public String codification;
        /** M(andatory), O(ptional), C(onditional), I(ndicative) */
        public String flag;
        /** 0 for the first section of the template */
        public int section;
        /** version, datetime, date, identifier, identifierType, currency, lei, integer, number, text, enum */
        public String type;
        public List<String> values;
        public Integer min;
        public Integer max;

        /** @return whether the field is mandatory in one of the template's general sections */
        public boolean isGenerallyMandatory() {
            return "M".equals(flag) && section <= 1;
        }
    }

    /** A template. */
    public static final class Template {
        public String template;
        public String version;
        public List<Field> fields;
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
    }

    private static final class Catalog {
        String source;
        List<Template> templates;
    }

    private static volatile List<Template> templates;

    private FinDatExTemplates() {
    }

    /** @return EMT, EET and EPT */
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

    /** @return the template whose version field is {@code code} */
    public static Optional<Template> byVersionField(String code) {
        return all().stream().filter(t -> t.versionField().equals(code)).findFirst();
    }

    public static Optional<Template> byName(String name) {
        return all().stream().filter(t -> t.template.equals(name)).findFirst();
    }
}
