package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Editor schema loads defer sample generation ({@link XsdExtendedElement#setSampleDataSupplier}): the
 * sample is produced on the first read, exactly once, and an explicit value still wins.
 */
class XsdExtendedElementLazySampleTest {

    @Test
    void supplierRunsOnFirstReadOnly() {
        XsdExtendedElement element = new XsdExtendedElement();
        AtomicInteger calls = new AtomicInteger();
        element.setSampleDataSupplier(() -> {
            calls.incrementAndGet();
            return "AB12";
        });
        assertEquals(0, calls.get(), "nothing is generated before the sample is read");

        assertEquals("AB12", element.getSampleData());
        assertEquals("AB12", element.getDisplaySampleData());
        assertEquals(1, calls.get(), "the sample is generated once and kept");
    }

    @Test
    void explicitSampleReplacesAPendingSupplier() {
        XsdExtendedElement element = new XsdExtendedElement();
        AtomicInteger calls = new AtomicInteger();
        element.setSampleDataSupplier(() -> {
            calls.incrementAndGet();
            return "generated";
        });
        element.setSampleData("fixed");

        assertEquals("fixed", element.getSampleData());
        assertEquals(0, calls.get());
    }

    @Test
    void lazySampleIsCleanedLikeAnEagerOne() {
        XsdExtendedElement element = new XsdExtendedElement();
        element.setSampleDataSupplier(() -> "a\u0001b");
        assertEquals("ab", element.getSampleData());
        assertFalse(element.getSampleData().contains("\u0001"));
    }
}
