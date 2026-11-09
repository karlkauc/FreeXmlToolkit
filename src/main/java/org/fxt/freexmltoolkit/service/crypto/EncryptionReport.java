package org.fxt.freexmltoolkit.service.crypto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.gson.GsonBuilder;

/**
 * What an encrypted document holds, read without any key: every EncryptedData (where, type,
 * algorithm, cipher value or reference, properties) with the keys that open it (EncryptedKeys
 * with their method, recipient and key information, KeyName, password derivation), plus warnings
 * about legacy algorithms.
 */
public record EncryptionReport(List<Data> data, List<String> warnings) {

    /** One EncryptedData. */
    public record Data(String id, String location, String type, String mimeType, String algorithm, String cipher,
                       List<KeyRef> keys, Map<String, String> properties) {
    }

    /** One way to the data key. */
    public record KeyRef(String kind, String id, String method, String recipient, String carriedName, String detail) {
    }

    /** Reads the report of a document. */
    public static EncryptionReport of(Document document) {
        List<Data> data = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Element ed : XmlEncryptionService.encryptedData(document)) {
            String algorithm = XmlEncryptionService.encryptionMethod(ed);
            warn(algorithm, warnings);
            String type = ed.getAttribute("Type");
            List<KeyRef> keys = new ArrayList<>();
            String keyName = XmlEncryptionService.keyName(ed);
            if (keyName != null) {
                keys.add(new KeyRef("KeyName", "", "symmetric key", "", keyName, ""));
            }
            for (Element ek : keysOf(document, ed)) {
                String method = XmlEncryptionService.encryptionMethod(ek);
                warn(method, warnings);
                Element keyInfo = XmlEncryptionService.keyInfoOf(ek);
                keys.add(new KeyRef("EncryptedKey", ek.getAttribute("Id"), label(method), ek.getAttribute("Recipient"),
                        text(XmlEncryptionService.firstChild(ek, XmlEncryptionService.XENC_NS, "CarriedKeyName")),
                        keyDetail(keyInfo, warnings)));
            }
            Element cipherData = XmlEncryptionService.firstChild(ed, XmlEncryptionService.XENC_NS, "CipherData");
            Element reference = XmlEncryptionService.firstChild(cipherData, XmlEncryptionService.XENC_NS, "CipherReference");
            Element value = XmlEncryptionService.firstChild(cipherData, XmlEncryptionService.XENC_NS, "CipherValue");
            String cipher = reference != null ? "CipherReference " + reference.getAttribute("URI")
                    : value != null ? "CipherValue (" + value.getTextContent().trim().length() + " Base64 characters)" : "";
            data.add(new Data(ed.getAttribute("Id"), location(ed),
                    type.endsWith("#Element") ? "Element" : type.endsWith("#Content") ? "Content"
                            : type.isEmpty() ? "data" : type,
                    ed.getAttribute("MimeType"), label(algorithm), cipher, keys, properties(ed)));
        }
        return new EncryptionReport(data, List.copyOf(new java.util.LinkedHashSet<>(warnings)));
    }

    /** @return a readable report */
    public String toText() {
        StringBuilder out = new StringBuilder();
        if (data.isEmpty()) {
            return "The document holds no EncryptedData.\n";
        }
        out.append(data.size()).append(" EncryptedData element(s)\n");
        int n = 0;
        for (Data d : data) {
            out.append('\n').append(++n).append(". ").append(d.id().isEmpty() ? "(no Id)" : d.id())
                    .append(" at ").append(d.location()).append('\n');
            out.append("   Type:       ").append(d.type()).append('\n');
            if (!d.mimeType().isEmpty()) {
                out.append("   MimeType:   ").append(d.mimeType()).append('\n');
            }
            out.append("   Algorithm:  ").append(d.algorithm()).append('\n');
            out.append("   Cipher:     ").append(d.cipher()).append('\n');
            if (d.keys().isEmpty()) {
                out.append("   Key:        not named (agreed out of band)\n");
            }
            for (KeyRef k : d.keys()) {
                out.append("   Key:        ").append(k.kind());
                if (!k.id().isEmpty()) {
                    out.append(' ').append(k.id());
                }
                out.append(" - ").append(k.method());
                if (!k.recipient().isEmpty()) {
                    out.append(", recipient ").append(k.recipient());
                }
                if (!k.carriedName().isEmpty()) {
                    out.append(", name ").append(k.carriedName());
                }
                if (!k.detail().isEmpty()) {
                    out.append(", ").append(k.detail());
                }
                out.append('\n');
            }
            d.properties().forEach((key, val) -> out.append("   Property:   ").append(key).append(" = ").append(val).append('\n'));
        }
        if (!warnings.isEmpty()) {
            out.append("\nWarnings\n");
            warnings.forEach(w -> out.append(" - ").append(w).append('\n'));
        }
        return out.toString();
    }

    /** @return the report as JSON */
    public String toJson() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(this) + "\n";
    }

    private static List<Element> keysOf(Document document, Element ed) {
        List<Element> keys = new ArrayList<>();
        Element keyInfo = XmlEncryptionService.keyInfoOf(ed);
        if (keyInfo != null) {
            for (Node n = keyInfo.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && "EncryptedKey".equals(e.getLocalName())) {
                    keys.add(e);
                } else if (n instanceof Element e && "RetrievalMethod".equals(e.getLocalName())
                        && e.getAttribute("URI").startsWith("#")) {
                    String id = e.getAttribute("URI").substring(1);
                    XmlEncryptionService.elements(document, XmlEncryptionService.XENC_NS, "EncryptedKey").stream()
                            .filter(k -> id.equals(k.getAttribute("Id"))).findFirst().ifPresent(keys::add);
                }
            }
        }
        String id = ed.getAttribute("Id");
        if (!id.isEmpty()) {
            for (Element ek : XmlEncryptionService.elements(document, XmlEncryptionService.XENC_NS, "EncryptedKey")) {
                Element list = XmlEncryptionService.firstChild(ek, XmlEncryptionService.XENC_NS, "ReferenceList");
                if (list != null && !keys.contains(ek)) {
                    for (Node n = list.getFirstChild(); n != null; n = n.getNextSibling()) {
                        if (n instanceof Element ref && ("#" + id).equals(ref.getAttribute("URI"))) {
                            keys.add(ek);
                            break;
                        }
                    }
                }
            }
        }
        return keys;
    }

    private static String keyDetail(Element keyInfo, List<String> warnings) {
        if (keyInfo == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Node n = keyInfo.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e)) {
                continue;
            }
            switch (e.getLocalName()) {
                case "KeyName" -> parts.add("key name " + text(e));
                case "X509Data" -> parts.add("certificate " + x509(e));
                case "KeyValue" -> parts.add("public key value");
                case "AgreementMethod" -> {
                    warn(e.getAttribute("Algorithm"), warnings);
                    Element kdm = XmlEncryptionService.firstChild(e, XmlEncryptionService.XENC11_NS, "KeyDerivationMethod");
                    parts.add(label(e.getAttribute("Algorithm"))
                            + (kdm != null ? " with " + label(kdm.getAttribute("Algorithm")) : ""));
                }
                case "DerivedKey" -> {
                    Element kdm = XmlEncryptionService.firstChild(e, XmlEncryptionService.XENC11_NS, "KeyDerivationMethod");
                    Element params = XmlEncryptionService.firstChild(kdm, XmlEncryptionService.XENC11_NS, "PBKDF2-params");
                    Element count = XmlEncryptionService.firstChild(params, XmlEncryptionService.XENC11_NS, "IterationCount");
                    parts.add("password (" + (kdm == null ? "?" : label(kdm.getAttribute("Algorithm")))
                            + (count != null ? ", " + text(count) + " iterations" : "") + ")");
                }
                default -> parts.add(e.getLocalName());
            }
        }
        return String.join("; ", parts);
    }

    private static String x509(Element x509Data) {
        for (Node n = x509Data.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                switch (e.getLocalName()) {
                    case "X509IssuerSerial" -> {
                        Element issuer = XmlEncryptionService.firstChild(e, XmlEncryptionService.DSIG_NS, "X509IssuerName");
                        Element serial = XmlEncryptionService.firstChild(e, XmlEncryptionService.DSIG_NS, "X509SerialNumber");
                        return "issuer " + text(issuer) + ", serial " + text(serial);
                    }
                    case "X509SubjectName" -> {
                        return "subject " + text(e);
                    }
                    case "X509SKI" -> {
                        return "SKI " + text(e);
                    }
                    case "X509Certificate" -> {
                        return "(embedded certificate)";
                    }
                    default -> {
                        // next
                    }
                }
            }
        }
        return "";
    }

    private static Map<String, String> properties(Element ed) {
        Map<String, String> map = new LinkedHashMap<>();
        Element properties = XmlEncryptionService.firstChild(ed, XmlEncryptionService.XENC_NS, "EncryptionProperties");
        if (properties == null) {
            return map;
        }
        int n = 0;
        for (Node p = properties.getFirstChild(); p != null; p = p.getNextSibling()) {
            if (p instanceof Element property) {
                for (Node v = property.getFirstChild(); v != null; v = v.getNextSibling()) {
                    if (v instanceof Element value) {
                        String name = XmlEncryptionService.PROPERTY_NS.equals(value.getNamespaceURI())
                                && value.hasAttribute("Name") ? value.getAttribute("Name") : value.getLocalName();
                        map.put(map.containsKey(name) ? name + "#" + (++n) : name, text(value));
                    }
                }
            }
        }
        return map;
    }

    /** An XPath-like location: element names with positions up to the root. */
    private static String location(Element element) {
        List<String> steps = new ArrayList<>();
        for (Node n = element.getParentNode(); n instanceof Element e; n = e.getParentNode()) {
            int position = 1;
            for (Node s = e.getPreviousSibling(); s != null; s = s.getPreviousSibling()) {
                if (s instanceof Element sibling && sibling.getNodeName().equals(e.getNodeName())) {
                    position++;
                }
            }
            steps.addFirst(e.getNodeName() + "[" + position + "]");
        }
        return steps.isEmpty() ? "/ (the document)" : "/" + String.join("/", steps);
    }

    private static void warn(String uri, List<String> warnings) {
        XmlEncryptionAlgorithms.byUri(uri).filter(Algorithm::legacy)
                .ifPresent(a -> warnings.add(XmlEncryptionService.legacyWarning(a)));
    }

    private static String label(String uri) {
        return XmlEncryptionAlgorithms.byUri(uri).map(Algorithm::toString).orElse(uri.isEmpty() ? "(none)" : uri);
    }

    private static String text(Element e) {
        return e == null ? "" : e.getTextContent().trim();
    }
}
