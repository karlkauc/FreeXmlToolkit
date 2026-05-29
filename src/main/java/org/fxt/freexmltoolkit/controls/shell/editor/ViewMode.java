package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * The editor view modes per the Figma design: Text, Tree and Graphic.
 * There is no standalone Grid mode — the grid is part of Graphic (embedded for
 * repeating same-type nodes). Structured modes (Tree/Graphic) apply to XSD.
 */
public enum ViewMode {
    TEXT("Text", "bi-code-slash"),
    TREE("Tree", "bi-list-nested"),
    GRAPHIC("Graphic", "bi-diagram-3");

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
