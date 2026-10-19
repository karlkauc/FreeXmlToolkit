package org.fxt.freexmltoolkit.test;

import java.io.File;

/**
 * Large test documents for UI-freeze regression tests, generated instead of checked in. The XSD is the
 * real 1.4 MB FundsXML 3.0.6 schema the telemetry stalls were reported for.
 */
public final class LargeDocuments {

    /** FundsXML 3.0.6: about 1.4 MB, thousands of global types (src/test/resources). */
    public static final File FUNDSXML_XSD = new File("src/test/resources/FundsXML_306.xsd");

    private LargeDocuments() {
    }

    /**
     * A pretty-printed XML document of roughly {@code targetChars} characters: a list of records with a
     * few nested fields each, the shape of typical data exports.
     */
    public static String xml(int targetChars) {
        StringBuilder xml = new StringBuilder(targetChars + 256);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<orders>\n");
        int i = 0;
        while (xml.length() < targetChars) {
            xml.append("  <order id=\"").append(i).append("\" status=\"open\">\n")
                    .append("    <customer>\n")
                    .append("      <name>Customer ").append(i).append("</name>\n")
                    .append("      <city>Vienna</city>\n")
                    .append("    </customer>\n")
                    .append("    <amount currency=\"EUR\">").append(i % 1000).append(".50</amount>\n")
                    .append("    <note>Line item note for order ").append(i).append("</note>\n")
                    .append("  </order>\n");
            i++;
        }
        return xml.append("</orders>\n").toString();
    }

    /** A pretty-printed JSON document of roughly {@code targetChars} characters (an array of records). */
    public static String json(int targetChars) {
        StringBuilder json = new StringBuilder(targetChars + 256);
        json.append("{\n  \"orders\": [\n");
        int i = 0;
        while (json.length() < targetChars) {
            if (i > 0) {
                json.append(",\n");
            }
            json.append("    {\n")
                    .append("      \"id\": ").append(i).append(",\n")
                    .append("      \"customer\": { \"name\": \"Customer ").append(i).append("\", \"city\": \"Vienna\" },\n")
                    .append("      \"amount\": ").append(i % 1000).append(".5,\n")
                    .append("      \"note\": \"Line item note for order ").append(i).append("\"\n")
                    .append("    }");
            i++;
        }
        return json.append("\n  ]\n}\n").toString();
    }
}
