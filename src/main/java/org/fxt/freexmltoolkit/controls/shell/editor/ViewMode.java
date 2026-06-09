package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * The editor view modes: Text, Tree, Graphic and Grid.
 * <ul>
 *   <li><b>Tree</b> — XSD schema tree / JSON tree / XML-instance DOM tree.</li>
 *   <li><b>Graphic</b> — the Canvas-based XSD schema diagram editor (XSD only).</li>
 *   <li><b>Grid</b> — the XMLSpy-style instance grid (XML-family instances only).</li>
 * </ul>
 */
public enum ViewMode {
    TEXT("Text", "bi-code-slash"),
    TREE("Tree", "bi-list-nested"),
    GRAPHIC("Graphic", "bi-diagram-3"),
    GRID("Grid", "bi-table");

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
}
