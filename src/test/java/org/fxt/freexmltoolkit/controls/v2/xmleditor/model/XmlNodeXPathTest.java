package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

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
    void positionsCountOnlySameNameSiblingsAcrossOtherNames() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        doc.addChild(root);
        XmlElement a1 = new XmlElement("a");
        XmlElement b = new XmlElement("b");
        XmlElement a2 = new XmlElement("a");
        XmlElement c = new XmlElement("c");
        root.addChild(a1);
        root.addChild(b);
        root.addChild(a2);
        root.addChild(c);

        assertEquals("/root/a[1]", XmlNodeXPath.positional(a1));
        assertEquals("/root/b", XmlNodeXPath.positional(b));
        assertEquals("/root/a[2]", XmlNodeXPath.positional(a2));
        assertEquals("/root/c", XmlNodeXPath.positional(c));
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

    @Test
    void theCacheGivesWhatPositionalGives() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        doc.addChild(root);
        List<XmlNode> nodes = new ArrayList<>();
        nodes.add(root);
        for (int i = 0; i < 5; i++) {
            XmlElement record = new XmlElement(i % 3 == 0 ? "other" : "record");
            root.addChild(record);
            nodes.add(record);
            XmlElement single = new XmlElement("name");
            record.addChild(single);
            nodes.add(single);
            for (int j = 0; j < 3; j++) {
                XmlElement value = new XmlElement("value");
                record.addChild(value);
                nodes.add(value);
            }
            XmlText text = new XmlText("t" + i);
            record.addChild(text);
            nodes.add(text);
        }
        nodes.add(null);
        nodes.add(doc);

        XmlNodeXPath.Cache cache = new XmlNodeXPath.Cache();
        for (XmlNode node : nodes) {
            assertEquals(XmlNodeXPath.positional(node), cache.positional(node), String.valueOf(node));
        }
    }

    @Test
    void theCacheKeysManySameNameSiblingsInLinearTime() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        doc.addChild(root);
        List<XmlElement> records = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            XmlElement record = new XmlElement("record");
            root.addChild(record);
            records.add(record);
        }
        long start = System.nanoTime();
        XmlNodeXPath.Cache cache = new XmlNodeXPath.Cache();
        for (XmlElement record : records) {
            cache.positional(record);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        // uncached: 200,000² / 2 sibling visits = minutes (seen as a 139 s UI stall)
        assertTrue(ms < 5_000, ms + " ms");
        assertEquals("/root/record[200000]", cache.positional(records.get(199_999)));
    }
}
