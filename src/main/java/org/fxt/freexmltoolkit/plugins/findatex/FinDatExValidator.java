package org.fxt.freexmltoolkit.plugins.findatex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers;

/**
 * Checks FinDatEx template files (EMT, EET, EPT, TPT as CSV) against the field definitions of the
 * official specification: unknown and missing columns, the template version, the format of every
 * value (dates, Y/N and other closed lists, numbers, ISO 4217 currencies, ISO 3166 countries, CIC
 * codes, LEIs, ISINs and LEIs when the identification type is 1, text lengths) and the mandatory
 * fields: those of the template's general sections, in TPT those of the instrument's CIC category.
 */
public final class FinDatExValidator implements DocumentValidator {

    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+([.,]\\d*)?|[.,]\\d+)([eE][-+]?\\d+)?");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    /** CIC: country of listing (or XL not listed, XT not tradable), category 0-9/A-F, subcategory. */
    private static final Pattern CIC = Pattern.compile("[A-Z]{2}[0-9A-F][0-9A-Z]");
    private static final Set<String> COUNTRIES = Set.of(java.util.Locale.getISOCountries());
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** At most this many value problems are reported per file. */
    static final int LIMIT = 500;

    @Override
    public String name() {
        return "FinDatEx";
    }

    @Override
    public boolean accepts(String content, String documentName) {
        return FinDatExFile.parse(content).isPresent();
    }

    @Override
    public List<Problem> validate(String content, String documentName) {
        FinDatExFile file = FinDatExFile.parse(content).orElse(null);
        if (file == null) {
            return List.of(new Problem("error", 0, "This is no EMT, EET, EPT or TPT file (no version field found)."));
        }
        FinDatExTemplates.Template template = file.template();
        List<Problem> problems = new ArrayList<>();
        int headerLine = file.records().isEmpty() || file.transposed() ? 1
                : Math.max(1, file.records().getFirst().cells().getFirst().line() - 1);
        Set<String> seen = new HashSet<>();
        for (String code : file.codes()) {
            if (code.isEmpty()) {
                continue;
            }
            if (!seen.add(code)) {
                problems.add(new Problem("error", headerLine, "The field " + code + " appears twice."));
            } else if (template.field(code).isEmpty()) {
                problems.add(new Problem("warning", headerLine, code + " is no field of " + template.template + " "
                        + template.version + "."));
            }
        }
        List<String> missing = template.fields.stream().filter(FinDatExTemplates.Field::isGenerallyMandatory)
                .map(f -> f.code).filter(c -> !seen.contains(c)).toList();
        if (!missing.isEmpty()) {
            problems.add(new Problem("warning", headerLine, "Mandatory field" + (missing.size() == 1 ? " " : "s ")
                    + String.join(", ", missing) + (missing.size() == 1 ? " is" : " are") + " missing."));
        }
        if (file.records().isEmpty()) {
            problems.add(new Problem("warning", headerLine, "The file has no instruments."));
        }
        String identifierCode = template.label != null ? template.label : code(template, "identifier");
        String identifierTypeCode = code(template, "identifierType");
        String cicCode = code(template, "cic");
        int valueProblems = 0;
        for (FinDatExFile.Record record : file.records()) {
            String instrument = identifierCode == null ? "" : file.cell(record, identifierCode).value().strip();
            char category = cicCode == null ? 0 : category(file.cell(record, cicCode).value());
            String where = (file.transposed() ? "Instrument " : "Row ") + record.number()
                    + (instrument.isEmpty() ? "" : " (" + instrument + ")") + ": ";
            for (int i = 0; i < file.codes().size() && i < record.cells().size(); i++) {
                FinDatExTemplates.Field field = template.field(file.codes().get(i)).orElse(null);
                FinDatExFile.Cell cell = record.cells().get(i);
                String value = cell.value().strip();
                if (field == null) {
                    continue;
                }
                String problem = value.isEmpty()
                        ? field.isMandatoryFor(category) ? mandatoryEmpty(field, category) : null
                        : check(field, value, file, record, identifierTypeCode);
                if (problem != null) {
                    if (++valueProblems > LIMIT) {
                        problems.add(new Problem("warning", 0, "More than " + LIMIT + " value problems; the rest is not listed."));
                        return problems;
                    }
                    boolean empty = value.isEmpty();
                    problems.add(new Problem(empty || "version".equals(field.type) ? "warning" : "error", cell.line(),
                            where + field.code + (empty ? ": " : " \"" + value + "\": ") + problem + "."));
                }
            }
        }
        return problems;
    }

    /** @return the CIC category of a CIC code (its third character), or {@code 0} */
    static char category(String cic) {
        String value = cic == null ? "" : cic.strip().toUpperCase(java.util.Locale.ROOT);
        return CIC.matcher(value).matches() ? value.charAt(2) : 0;
    }

    private static String mandatoryEmpty(FinDatExTemplates.Field field, char category) {
        return field.cic != null && category != 0 && !field.isGenerallyMandatory()
                ? "the field is mandatory for CIC category " + category + " and empty"
                : "the mandatory field is empty";
    }

    private static String code(FinDatExTemplates.Template template, String type) {
        return template.fields.stream().filter(f -> type.equals(f.type)).map(f -> f.code).findFirst().orElse(null);
    }

    /** @return why the value does not fit the field, or {@code null} */
    static String check(FinDatExTemplates.Field field, String value, FinDatExFile file, FinDatExFile.Record record,
                        String identifierTypeCode) {
        return switch (field.type == null ? "text" : field.type) {
            case "version" -> field.values == null || field.values.contains(value) ? null
                    : "the checks follow " + String.join(" / ", field.values) + "; other versions may differ";
            case "datetime" -> parses(() -> LocalDateTime.parse(value.replaceAll("\\s+", " "), DATE_TIME))
                    ? null : "expected a date and time like 2026-01-31 14:30:00";
            case "date" -> parses(() -> LocalDate.parse(value)) ? null : "expected a date like 2026-01-31";
            case "enum" -> field.values == null || field.values.contains(value) ? null
                    : "expected one of " + String.join(", ", field.values);
            case "currency" -> CURRENCY.matcher(value).matches() ? null : "expected an ISO 4217 currency code like EUR";
            case "lei" -> FinancialIdentifiers.checkLei(value) == null ? null : "not a valid LEI";
            case "identifier" -> identifier(field, value, file, record, identifierTypeCode);
            case "cic" -> cic(value);
            case "country" -> COUNTRIES.contains(value) ? null : "expected an ISO 3166 country code like DE";
            case "integer" -> integer(field, value);
            case "number" -> NUMBER.matcher(value).matches() ? null : "expected a number (decimal point, 5% = 0.05)";
            default -> field.max != null && value.length() > field.max
                    ? "longer than " + field.max + " characters" : null;
        };
    }

    /** Type 1 of the identification type field: an ISIN (instruments, funds) or an LEI (issuers). */
    private static String identifier(FinDatExTemplates.Field field, String value, FinDatExFile file,
                                     FinDatExFile.Record record, String identifierTypeCode) {
        String typeCode = field.typeField != null ? field.typeField : identifierTypeCode;
        if (typeCode == null || !file.cell(record, typeCode).value().strip().equals("1")) {
            return null;
        }
        if ("lei".equals(field.type1)) {
            return FinancialIdentifiers.checkLei(value) == null ? null : "not a valid LEI (identification type 1)";
        }
        return FinancialIdentifiers.checkIsin(value) == null ? null : "not a valid ISIN (identification type 1)";
    }

    /** A CIC code: two letters (ISO country, XL or XT), the category 0-9/A-F and the subcategory. */
    private static String cic(String value) {
        if (!CIC.matcher(value).matches()) {
            return "expected a CIC code like DE31 (country or XL/XT, category 0-9 or A-F, subcategory)";
        }
        String country = value.substring(0, 2);
        return COUNTRIES.contains(country) || country.equals("XL") || country.equals("XT") || country.equals("EU")
                ? null : country + " is no ISO 3166 country code nor XL/XT";
    }

    private static String integer(FinDatExTemplates.Field field, String value) {
        try {
            BigDecimal number = new BigDecimal(value);
            if (number.stripTrailingZeros().scale() > 0) {
                return "expected a whole number";
            }
            if ((field.min != null && number.intValue() < field.min) || (field.max != null && number.intValue() > field.max)) {
                return "expected a number from " + field.min + " to " + field.max;
            }
            return null;
        } catch (NumberFormatException e) {
            return "expected a whole number";
        }
    }

    private static boolean parses(Runnable parse) {
        try {
            parse.run();
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
