package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * What a problem is about: a security (ISIN), a legal entity (LEI), a reported asset (an internal asset id) or a
 * record of the report. Problems with the same subject are grouped in the PROBLEMS panel, so a security that occurs
 * a hundred times is fixed once.
 *
 * @param kind  what the key identifies
 * @param key   the identifier (ISIN, LEI, asset id, record key)
 * @param label a readable name (from ESMA FIRDS / GLEIF, the position's title), or {@code null}
 */
public record ProblemSubject(Kind kind, String key, String label) {

    /** What a subject's key identifies. */
    public enum Kind { ISIN, LEI, ASSET, RECORD }

    public ProblemSubject {
        if (kind == null || key == null || key.isBlank()) {
            throw new IllegalArgumentException("a subject needs a kind and a key");
        }
        key = key.strip();
        label = label == null || label.isBlank() ? null : label.strip();
    }

    /** @return a subject for an ISIN, an LEI or another asset id, by the key's form */
    public static ProblemSubject ofAsset(String key, String label) {
        String k = key.strip();
        if (org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers.checkIsin(k) == null) {
            return new ProblemSubject(Kind.ISIN, k, label);
        }
        if (org.fxt.freexmltoolkit.service.finance.FinancialIdentifiers.checkLei(k) == null) {
            return new ProblemSubject(Kind.LEI, k, label);
        }
        return new ProblemSubject(Kind.ASSET, k, label);
    }

    /** @return the group key: kind and key (two problems about the same thing share it) */
    public String groupKey() {
        return kind + ":" + key;
    }

    /** @return "ISIN XS1234567890 · Name"; an asset or record without the kind ("VRG 1", "FUND-0815 · Name") */
    public String display() {
        String head = kind == Kind.ISIN || kind == Kind.LEI ? kind + " " + key : key;
        return head + (label == null ? "" : " · " + label);
    }
}
