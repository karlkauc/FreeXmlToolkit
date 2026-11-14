/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Prepares a report for the FMA Incoming Platform: the file name of the FMA's convention
 * ({@code pk.modul.stichtag.lfd-nr[.zusatz].xml}, lower case, e.g. {@code 34.pfq.20190331.00001.test.xml}),
 * the next running number in a folder, a copy without grid formulas, encoded in UTF-8, packed as
 * ZIP of the same name.
 */
public final class PkSubmission {

    /** The FMA's file name: pension fund, module, reference date, running number, optional suffix. */
    static final Pattern NAME = Pattern.compile("(\\d{1,3})\\.(pf[qae])\\.(\\d{8})\\.(\\d{5})(?:\\.([a-z]+))?\\.(xml|zip)");
    private static final Pattern DECLARATION = Pattern.compile("\\A\\uFEFF?<\\?xml[^?]*\\?>");
    private static final Pattern ENCODING = Pattern.compile("encoding\\s*=\\s*([\"'])[^\"']*\\1");

    private PkSubmission() {
    }

    /**
     * @return the reference date of the report as {@code YYYYMMDD} (Q1 → 0331 … Q4 → 1231; {@code JAHR},
     *         the annual and the ECB report → 1231), or {@code null} when the year or period is missing
     */
    public static String referenceDate(PkReport report) {
        String year = report.year();
        if (year == null || !year.matches("\\d{4}")) {
            return null;
        }
        String period = report.period() == null ? "" : report.period().strip().toUpperCase(Locale.ROOT);
        String day = switch (period) {
            case "Q1" -> "0331";
            case "Q2" -> "0630";
            case "Q3" -> "0930";
            case "Q4", "J", "JAHR" -> "1231";
            default -> report.module() == PkModule.PFA ? "1231" : null;
        };
        return day == null ? null : year + day;
    }

    /** @return the suffix proposed for a report: {@code test} for a test report, else none */
    public static String proposedSuffix(PkReport report) {
        String type = report.reportType();
        return type != null && type.toLowerCase(Locale.ROOT).contains("test") ? "test" : "";
    }

    /**
     * @param sequence the running number (1 … 99999)
     * @param suffix   the optional suffix (letters only; empty for none)
     * @return the XML file name, or {@code null} when the report lacks the pension fund number or the date
     */
    public static String fileName(PkReport report, int sequence, String suffix) {
        String date = referenceDate(report);
        String pk = report.pkNumber();
        if (date == null || pk == null || !pk.strip().matches("\\d{1,3}") || sequence < 1 || sequence > 99_999) {
            return null;
        }
        String extra = suffix == null ? "" : suffix.strip().toLowerCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%02d.%s.%s.%05d%s.xml", Integer.parseInt(pk.strip()), report.module().code(),
                date, sequence, extra.isEmpty() ? "" : "." + extra);
    }

    /** @return whether a suffix is allowed (letters only; empty means none) */
    public static boolean validSuffix(String suffix) {
        return suffix == null || suffix.isBlank() || suffix.strip().matches("[A-Za-z]+");
    }

    /**
     * @return the next running number for the report in a folder: one more than the highest number of
     *         a file (XML or ZIP) of the same pension fund, module and date there, else 1
     */
    public static int nextSequence(Path folder, PkReport report) {
        String date = referenceDate(report);
        if (folder == null || date == null || !Files.isDirectory(folder)) {
            return 1;
        }
        int highest = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher m = NAME.matcher(file.getFileName().toString().toLowerCase(Locale.ROOT));
                if (m.matches() && samePk(m.group(1), report.pkNumber()) && m.group(2).equals(report.module().code())
                        && m.group(3).equals(date)) {
                    highest = Math.max(highest, Integer.parseInt(m.group(4)));
                }
            }
        } catch (IOException e) {
            return 1;
        }
        return Math.min(highest + 1, 99_999);
    }

    private static boolean samePk(String a, String b) {
        try {
            return b != null && Integer.parseInt(a) == Integer.parseInt(b.strip());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Checks a file name against the convention and the report.
     *
     * @return what does not match (for the dialog); empty when the name is right
     */
    public static List<String> checkName(String fileName, PkReport report) {
        List<String> issues = new ArrayList<>();
        if (fileName == null || !fileName.equals(fileName.toLowerCase(Locale.ROOT))) {
            issues.add(I18n.t("PkSubmission.lower-case"));
        }
        Matcher m = NAME.matcher(fileName == null ? "" : fileName.toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            issues.add(I18n.t("PkSubmission.pattern"));
            return issues;
        }
        if (!samePk(m.group(1), report.pkNumber())) {
            issues.add(I18n.t("PkSubmission.pk", m.group(1), report.pkNumber()));
        }
        if (!m.group(2).equals(report.module().code())) {
            issues.add(I18n.t("PkSubmission.module", m.group(2), report.module().code()));
        }
        String date = referenceDate(report);
        if (date != null && !m.group(3).equals(date)) {
            issues.add(I18n.t("PkSubmission.date", m.group(3), date));
        }
        return issues;
    }

    /**
     * @return the report as it is submitted: without grid formulas and filters, with an XML
     *         declaration that names UTF-8
     */
    public static String clean(String xml) throws IOException {
        String text = PkGridSetup.hasAnnotations(xml) ? PkGridSetup.remove(xml) : xml;
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        Matcher declaration = DECLARATION.matcher(text);
        if (declaration.find()) {
            String decl = declaration.group();
            String fixed = ENCODING.matcher(decl).find() ? ENCODING.matcher(decl).replaceFirst("encoding=\"UTF-8\"")
                    : decl.replaceFirst("\\?>$", " encoding=\"UTF-8\"?>");
            return fixed + text.substring(declaration.end());
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + text;
    }

    /**
     * Writes the report as UTF-8 XML into a ZIP: {@code <name>.zip} holding {@code <name>.xml}, in
     * the folder. An existing file of that name is not overwritten.
     *
     * @param xmlName the XML file name ({@link #fileName})
     * @return the ZIP file
     * @throws IOException when the ZIP exists already or cannot be written
     */
    public static Path writeZip(String cleanXml, Path folder, String xmlName) throws IOException {
        if (!xmlName.endsWith(".xml")) {
            throw new IllegalArgumentException("not an XML file name: " + xmlName);
        }
        Path zip = folder.resolve(xmlName.substring(0, xmlName.length() - 4) + ".zip");
        if (Files.exists(zip)) {
            throw new IOException(zip.getFileName() + " exists already.");
        }
        Files.createDirectories(folder);
        Path temp = Files.createTempFile(folder, ".pk-submission", ".zip.tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp); ZipOutputStream zipOut = new ZipOutputStream(out)) {
                zipOut.putNextEntry(new ZipEntry(xmlName));
                zipOut.write(cleanXml.getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
            Files.move(temp, zip, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return zip;
    }
}
