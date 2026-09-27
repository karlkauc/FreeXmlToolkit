package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

/** One fact of a report section ("XSD version" → "1.1"). */
public record KeyValue(String key, String value, Tone tone) {

    public KeyValue {
        key = key == null ? "" : key;
        value = value == null ? "" : value;
        tone = tone == null ? Tone.NONE : tone;
    }

    public static KeyValue of(String key, Object value) {
        return new KeyValue(key, value == null ? "" : String.valueOf(value), Tone.NONE);
    }

    public static KeyValue of(String key, Object value, Tone tone) {
        return new KeyValue(key, value == null ? "" : String.valueOf(value), tone);
    }
}
