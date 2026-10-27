package org.fxt.freexmltoolkit.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Attribute declarations of an element in the documentation model: the {@code @…} children
 * the {@link XsdDocumentationData} lists under the element's map entry. Shared by the
 * Inspector's schema adapter and the editor's attribute completion.
 */
public final class XsdAttributeLookup {

    private XsdAttributeLookup() {
    }

    /**
     * @param data    the documentation model
     * @param element the element whose attributes are wanted (may be null)
     * @return the element's attribute declarations in schema order (never null)
     */
    public static List<XsdExtendedElement> declaredAttributes(XsdDocumentationData data, XsdExtendedElement element) {
        if (data == null || element == null || element.getChildren() == null) {
            return Collections.emptyList();
        }
        Map<String, XsdExtendedElement> map = data.getExtendedXsdElementMap();
        if (map == null) {
            return Collections.emptyList();
        }
        List<XsdExtendedElement> out = new ArrayList<>();
        for (String child : element.getChildren()) {
            if (child == null || !child.substring(child.lastIndexOf('/') + 1).startsWith("@")) {
                continue;
            }
            XsdExtendedElement attribute = map.get(child);
            if (attribute != null) {
                out.add(attribute);
            }
        }
        return out;
    }

    /** @return the local attribute name, without "@" and prefix */
    public static String attributeName(XsdExtendedElement attribute) {
        String name = attribute.getElementName() == null ? "" : attribute.getElementName();
        if (name.startsWith("@")) {
            name = name.substring(1);
        }
        return name.substring(name.indexOf(':') + 1);
    }
}
