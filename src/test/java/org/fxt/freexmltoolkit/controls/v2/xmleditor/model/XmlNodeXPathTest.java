package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for {@link XmlNodeXPath}: a sibling position predicate is added
 * only when it is needed to disambiguate same-name siblings.
 */
class XmlNodeXPathTest {

    @Test
    void singleChildHasNoPositionPredicate() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        XmlElement name = new XmlElement("name");
        doc.addChild(root);
        root.addChild(name);

        assertEquals("/root/name", XmlNodeXPath.positional(name));
    }

    @Test
    void sameNameSiblingsGetPositionPredicate() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        XmlElement items = new XmlElement("items");
        XmlElement item1 = new XmlElement("item");
        XmlElement item2 = new XmlElement("item");
        XmlElement item3 = new XmlElement("item");
        doc.addChild(root);
        root.addChild(items);
        items.addChild(item1);
        items.addChild(item2);
        items.addChild(item3);

        assertEquals("/root/items/item[1]", XmlNodeXPath.positional(item1));
        assertEquals("/root/items/item[2]", XmlNodeXPath.positional(item2));
        assertEquals("/root/items/item[3]", XmlNodeXPath.positional(item3));
    }

    @Test
    void deepPathMixesIndexedAndPlainSteps() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        XmlElement items = new XmlElement("items");
        XmlElement item1 = new XmlElement("item");
        XmlElement item2 = new XmlElement("item");
        XmlElement name = new XmlElement("name");
        doc.addChild(root);
        root.addChild(items);
        items.addChild(item1);
        items.addChild(item2);
        item2.addChild(name); // unique under item2 → no predicate

        assertEquals("/root/items/item[2]/name", XmlNodeXPath.positional(name));
    }

    @Test
    void documentAndNullYieldRoot() {
        assertEquals("/", XmlNodeXPath.positional(null));
        assertEquals("/", XmlNodeXPath.positional(new XmlDocument()));
    }
}
