package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * The editor view modes: Text, Tree, Graphic and Preview (per the Figma mockup
 * "Redesign · Unified — Graphic + Grid" there is no standalone Grid mode).
 * <ul>
 *   <li><b>Tree</b> — XSD schema tree / JSON tree / XML-instance DOM tree.</li>
 *   <li><b>Graphic</b> — the Canvas-based XSD schema diagram for schemas, and
 *       the XMLSpy-style editable instance grid for XML-family instances
 *       (one switch position, one mental model: "the structured visual view").</li>
 *   <li><b>Preview</b> — the read-only rendered WebView for HTML documents
 *       (e.g. XSLT transform results); editing happens in Text mode.</li>
 * </ul>
 */
public enum ViewMode {
    TEXT("Text", "bi-type"),
    TREE("Tree", "bi-list-nested"),
    GRAPHIC("Graphic", "bi-diagram-2"),
    PREVIEW("Preview", "bi-eye");

    private final String label;
    private final String icon;

    ViewMode(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }

    /**
     * @return {@code true} for the structured, model-backed editable views
     *         (Tree/Graphic) — not Text and not the read-only Preview
     */
    public boolean isStructured() {
        return this == TREE || this == GRAPHIC;
    }
}
