package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.SimpleTypeChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The type-aware inline editors write XSD's lexical form whatever the user's locale: a decimal keeps its
 * fraction (the FundsXML {@code TotalPercentage} bug: under a German locale "3.5" became 3), nothing is rounded,
 * clamped or replaced by 0 / today, and the date/time pickers keep timezone and fractional seconds.
 */
@ExtendWith(ApplicationExtension.class)
class TypedEditWidgetsTest {

    private Locale previous;

    @Start
    void start(Stage stage) {
        // controls only, no window needed
    }

    @BeforeEach
    void germanLocale() {
        previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previous);
    }

    private static <T> T fx(Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(10000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Spinner<String> spinnerOf(NumericSpinner widget) {
        return (Spinner<String>) widget.getNode();
    }

    private static String typeInto(NumericSpinner widget, String text) {
        spinnerOf(widget).getEditor().setText("");
        spinnerOf(widget).getEditor().replaceSelection(text);
        return widget.getValue();
    }

    @Test
    void decimalsKeepTheirFractionUnderAGermanLocale() {
        var decimal = SimpleTypeChecker.builtin("decimal").orElseThrow();
        assertEquals("3.5", fx(() -> {
            NumericSpinner w = new NumericSpinner("1", null, Map.of(), NumericSpinner.Kind.DECIMAL, decimal::check);
            return typeInto(w, "3.5");
        }));
        assertEquals("3.5", fx(() -> {
            NumericSpinner w = new NumericSpinner("1", null, Map.of(), NumericSpinner.Kind.DECIMAL, decimal::check);
            return typeInto(w, "3,5");
        }), "a comma is the decimal separator a German user types");
        assertEquals("12345678901234567890.123456789", fx(() -> new NumericSpinner(
                "12345678901234567890.123456789", null, Map.of(), NumericSpinner.Kind.DECIMAL, null).getValue()),
                "no double rounding");
        assertEquals("0.035", fx(() -> new NumericSpinner("0.035", null, Map.of(), false).getValue()));
    }

    @Test
    void emptyStaysEmptyAndValuesAreNotClamped() {
        assertEquals("", fx(() -> new NumericSpinner("", null, Map.of(), true).getValue()));
        assertEquals("500", fx(() -> new NumericSpinner("500", null, Map.of("maxInclusive", "100"), true).getValue()),
                "an out-of-range value is shown and flagged, not changed");
        assertEquals("9223372036854775808",
                fx(() -> new NumericSpinner("9223372036854775808", null, Map.of(), true).getValue()));
        assertFalse(fx(() -> new NumericSpinner("500", null, Map.of("maxInclusive", "100"), true)
                .getValidationResult().isValid()));
    }

    @Test
    void invalidValuesAreFlaggedNotBlocked() {
        var unsignedByte = SimpleTypeChecker.builtin("unsignedByte").orElseThrow();
        NumericSpinner w = fx(() -> new NumericSpinner("300", null, Map.of(), NumericSpinner.Kind.INTEGER,
                unsignedByte::check));
        assertEquals("300", fx(w::getValue));
        assertFalse(fx(() -> w.getValidationResult().isValid()));
        assertTrue(fx(() -> w.getNode().getStyleClass().contains(WidgetFeedback.ERROR_CLASS)));
        assertNotNull(fx(() -> spinnerOf(w).getTooltip()), "the reason is shown");
    }

    @Test
    void arrowsStepExactlyWithinTheBounds() {
        Map<String, String> facets = Map.of("fractionDigits", "2", "maxInclusive", "1.26");
        assertEquals("1.26", fx(() -> {
            NumericSpinner w = new NumericSpinner("1.25", null, facets, false);
            spinnerOf(w).increment();
            return w.getValue();
        }));
        assertEquals("1.26", fx(() -> {
            NumericSpinner w = new NumericSpinner("1.26", null, facets, false);
            spinnerOf(w).increment();
            return w.getValue();
        }), "no step past maxInclusive");
        assertEquals("1", fx(() -> {
            NumericSpinner w = new NumericSpinner("", null, Map.of("minInclusive", "1"), true);
            spinnerOf(w).increment();
            return w.getValue();
        }), "an empty spinner starts at the lowest allowed value");
    }

    @Test
    void floatingPointTakesExponentsAndSpecialValues() {
        var dbl = SimpleTypeChecker.builtin("double").orElseThrow();
        assertEquals("1.5E3", fx(() -> typeInto(
                new NumericSpinner("", null, Map.of(), NumericSpinner.Kind.FLOATING, dbl::check), "1.5E3")));
        assertTrue(fx(() -> new NumericSpinner("-INF", null, Map.of(), NumericSpinner.Kind.FLOATING, dbl::check)
                .getValidationResult().isValid()));
    }

    @Test
    void datePickerKeepsTheTimezoneAndUnchangedValues() {
        assertEquals("2024-01-31-05:00", fx(() -> new XmlDatePicker("2024-01-31-05:00", null, null).getValue()));
        assertEquals("2024-02-01-05:00", fx(() -> {
            XmlDatePicker w = new XmlDatePicker("2024-01-31-05:00", null, null);
            ((DatePicker) w.getNode()).setValue(java.time.LocalDate.of(2024, 2, 1));
            return w.getValue();
        }));
        assertEquals("not a date", fx(() -> new XmlDatePicker("not a date", null, null).getValue()),
                "an unreadable value is kept until a date is picked");
        assertEquals("", fx(() -> new XmlDatePicker("", null, null).getValue()));
    }

    @Test
    void timePickerAlwaysWritesSecondsAndKeepsTheRest() {
        assertEquals("12:01:00", fx(() -> {
            XmlTimePicker w = new XmlTimePicker("12:00:00", null);
            spinner((HBox) w.getNode(), 1).getValueFactory().setValue(1);
            return w.getValue();
        }), "ISO_LOCAL_TIME wrote 12:01, which is no xs:time");
        assertEquals("13:00:00.125Z", fx(() -> {
            XmlTimePicker w = new XmlTimePicker("12:00:00.125Z", null);
            spinner((HBox) w.getNode(), 0).getValueFactory().setValue(13);
            return w.getValue();
        }));
        assertEquals("24:00:00", fx(() -> new XmlTimePicker("24:00:00", null).getValue()));
    }

    @Test
    void dateTimePickerNeverInventsADate() {
        assertEquals("", fx(() -> new XmlDateTimePicker("", null).getValue()), "was today's date");
        assertEquals("2024-01-01T10:00:00.5-05:00",
                fx(() -> new XmlDateTimePicker("2024-01-01T10:00:00.5-05:00", null).getValue()),
                "a negative offset used to be unreadable and replaced by today, 00:00:00");
        assertEquals("2024-01-01T11:00:00.5-05:00", fx(() -> {
            XmlDateTimePicker w = new XmlDateTimePicker("2024-01-01T10:00:00.5-05:00", null);
            spinner((HBox) w.getNode(), 0).getValueFactory().setValue(11);
            return w.getValue();
        }));
    }

    @Test
    void comboReturnsTypedText() {
        assertEquals("CUSIP", fx(() -> new EnumerationComboBox(java.util.List.of("CUSIP", "SEDOL"), "CUSIP", null)
                .getValue()));
        assertEquals("X", fx(() -> {
            EnumerationComboBox w = new EnumerationComboBox(java.util.List.of("CUSIP", "SEDOL"), "CUSIP", null);
            ((javafx.scene.control.ComboBox<?>) w.getNode()).getEditor().setText("X");
            return w.getValue();
        }), "typed but not yet committed text counts");
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> spinner(HBox box, int index) {
        return (Spinner<Integer>) box.getChildren().stream().filter(n -> n instanceof Spinner<?>)
                .skip(index).findFirst().orElseThrow();
    }
}
