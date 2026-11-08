package org.fxt.freexmltoolkit.service.fundsxml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonResourceAccess;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Adds grid formulas and saved filters to a FundsXML document with
 * {@code plugins/fundsxml/fundsxml-grid-setup.xsl}: counts, sums per currency, weights, the largest
 * position and breakdowns per fund, portfolio, asset master data, transactions and share classes;
 * filters for positions over 5 %, assets without ISIN and transactions settling before their entry.
 * Running it again changes nothing.
 */
public final class FundsXmlGridSetup {

    private static final Processor PROCESSOR = SaxonResourceAccess.restrictedProcessor();
    private static volatile XsltExecutable stylesheet;

    private FundsXmlGridSetup() {
    }

    private static XsltExecutable stylesheet() throws SaxonApiException, IOException {
        if (stylesheet == null) {
            try (InputStream in = FundsXmlGridSetup.class.getResourceAsStream("/plugins/fundsxml/fundsxml-grid-setup.xsl")) {
                if (in == null) {
                    throw new IOException("plugins/fundsxml/fundsxml-grid-setup.xsl is missing");
                }
                stylesheet = PROCESSOR.newXsltCompiler().compile(new StreamSource(in));
            }
        }
        return stylesheet;
    }

    /**
     * @param withFilters whether saved filters are added too (formulas always)
     * @return the document with the formulas and filters
     * @throws IOException when the document is not well-formed or the stylesheet fails
     */
    public static String apply(String xml, boolean withFilters) throws IOException {
        try {
            Xslt30Transformer transformer = stylesheet().load30();
            transformer.setStylesheetParameters(java.util.Map.of(new QName("with-filters"),
                    new XdmAtomicValue(withFilters)));
            StringWriter out = new StringWriter();
            Serializer serializer = PROCESSOR.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "no");
            transformer.transform(new StreamSource(new StringReader(xml)), serializer);
            return out.toString();
        } catch (SaxonApiException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** @return whether the document is signed (adding formulas breaks the signature) */
    public static boolean isSigned(String xml) {
        return xml != null && (xml.contains("<Signature") || xml.contains(":Signature"))
                && xml.contains("http://www.w3.org/2000/09/xmldsig#");
    }
}
