package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints;
import org.fxt.freexmltoolkit.controls.v2.model.XsdField;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKey;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKeyRef;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSelector;
import org.junit.jupiter.api.Test;

/** The line a card shows for a constraint, and the details behind it. */
class ConstraintRowTest {

    private static SchemaConstraints.ConstraintInfo infoOf(org.fxt.freexmltoolkit.controls.v2.model.XsdNode parent) {
        return SchemaConstraints.collect(parent).getFirst();
    }

    private static XsdKey key() {
        XsdKey key = new XsdKey("bookKey");
        key.addChild(new XsdSelector("book"));
        key.addChild(new XsdField("@isbn"));
        key.addChild(new XsdField("@lang"));
        return key;
    }

    @Test
    void aKeyShowsItsNameAndFields() {
        var parent = new org.fxt.freexmltoolkit.controls.v2.model.XsdElement("library");
        parent.addChild(key());

        ConstraintRow row = ConstraintRow.identity(infoOf(parent), null);

        assertEquals(ConstraintRow.Kind.KEY, row.kind());
        assertEquals("bookKey  @isbn, @lang", row.text());
        assertEquals("key bookKey\nselector: book\nfields: @isbn, @lang", row.tooltip());
    }

    @Test
    void aKeyrefPointsAtWhatItRefers() {
        var parent = new org.fxt.freexmltoolkit.controls.v2.model.XsdElement("library");
        XsdKeyRef keyRef = new XsdKeyRef("loanRef");
        keyRef.setRefer("bookKey");
        parent.addChild(keyRef);

        ConstraintRow row = ConstraintRow.identity(infoOf(parent), "from element library");

        assertEquals(ConstraintRow.Kind.KEYREF, row.kind());
        assertEquals("loanRef → bookKey", row.text());
        assertTrue(row.tooltip().endsWith("from element library"), row.tooltip());
    }

    @Test
    void anAssertionIsOneShortLineWithTheFullTestInTheTooltip() {
        String test = "count(book)\n      gt 0 and " + "every $b in book satisfies $b/@isbn ".repeat(3);

        ConstraintRow row = ConstraintRow.assertion(test, "from type LibraryType");

        assertEquals(ConstraintRow.MAX_TEXT_LENGTH, row.text().length());
        assertTrue(row.text().startsWith("count(book) gt 0"), row.text());
        assertTrue(row.text().endsWith("…"), row.text());
        assertEquals("assert\n" + test.strip() + "\nfrom type LibraryType", row.tooltip());
    }

    @Test
    void aShortAssertionIsNotShortened() {
        assertEquals("@a gt 0", ConstraintRow.assertion("  @a gt 0 ", null).text());
    }
}
