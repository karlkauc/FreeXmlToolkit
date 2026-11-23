package org.fxt.freexmltoolkit.screenshots;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;

/**
 * The steps the FundsXML tutorial scenarios share on the active text editor: find text, mark lines,
 * scroll smoothly to a line, fold to a level, pick a problem row.
 */
final class FundsXmlVideoKit {

    private final DocShell doc;
    private final DocVideoRecorder video;

    FundsXmlVideoKit(DocShell doc, DocVideoRecorder video) {
        this.doc = doc;
        this.video = video;
    }

    EditorHost host() {
        return doc.host();
    }

    String text() {
        return host().getActiveText().orElseThrow();
    }

    CodeArea area() {
        return doc.fx(() -> host().getActiveCodeArea());
    }

    /** The offset of {@code snippet} in the active text (the first one at or after {@code from}). */
    int offset(String snippet, int from) {
        int at = text().indexOf(snippet, from);
        if (at < 0) {
            throw new IllegalStateException("Not in the document: " + snippet);
        }
        return at;
    }

    int offset(String snippet) {
        return offset(snippet, 0);
    }

    /** The 1-based line of an offset. */
    int lineOf(int offset) {
        String t = text();
        int line = 1;
        for (int i = 0; i < offset && i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** The screen region of {@code snippet} (it must be on the screen). */
    Bounds textBounds(String snippet) {
        int at = offset(snippet);
        return chars(at, at + snippet.length());
    }

    Bounds chars(int from, int to) {
        CodeArea area = area();
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(from, to).orElseThrow());
        return new BoundingBox(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }

    /** A region over the lines {@code from}..{@code to} (1-based), from the text's left edge, at least 760 px wide. */
    Bounds lines(int from, int to) {
        CodeArea area = area();
        return doc.fx(() -> {
            int a = area.getAbsolutePosition(from - 1, 0);
            int b = area.getAbsolutePosition(to - 1, area.getParagraphLength(to - 1));
            Bounds first = area.getCharacterBoundsOnScreen(a, Math.min(b, a + 1)).orElseThrow();
            Bounds last = area.getCharacterBoundsOnScreen(Math.max(a, b - 1), Math.max(a + 1, b)).orElseThrow();
            Bounds whole = area.getCharacterBoundsOnScreen(a, b).orElse(first);
            double left = area.localToScreen(area.getBoundsInLocal()).getMinX();
            double right = Math.max(whole.getMaxX(), left + 760);
            return new BoundingBox(left, first.getMinY(), right - left + 40, last.getMaxY() - first.getMinY());
        });
    }

    /** Scrolls the text so that {@code line} (1-based) is near the top, in small steps. */
    void scrollTo(int line) {
        CodeArea area = area();
        int target = Math.max(0, line - 3);
        int start = doc.fx(area::firstVisibleParToAllParIndex);
        int steps = Math.max(1, Math.min(12, Math.abs(target - start) / 4));
        for (int i = 1; i <= steps; i++) {
            int p = start + (target - start) * i / steps;
            doc.onFx(() -> area.showParagraphAtTop(p));
            video.pause(25);
        }
        doc.onFx(() -> area.showParagraphAtTop(target));
        video.pause(300);
    }

    /** Places the caret at the start of {@code snippet} with a click (it must be on the screen). */
    void clickInto(String snippet, int from) {
        int at = offset(snippet, from);
        Bounds b = chars(at + 1, at + 2);
        video.click(b.getCenterX(), b.getCenterY());
    }

    XmlCodeEditorV2 editor() {
        return doc.fx(() -> doc.stage().getScene().getRoot().lookupAll("*").stream()
                .filter(n -> n instanceof XmlCodeEditorV2 && n.isVisible() && n.getScene() != null)
                .map(XmlCodeEditorV2.class::cast).findFirst().orElseThrow());
    }

    /** Folds every element at an indentation level (0 = the root). */
    void foldLevel(int level) {
        XmlCodeEditorV2 editor = editor();
        doc.onFx(() -> editor.getFoldingManager().foldLevel(level));
        video.pause(400);
    }

    /**
     * Folds the element that starts on {@code line} (1-based); the pointer first glides to the line's
     * fold arrow in the gutter, so the fold looks like the click it stands for.
     */
    void fold(int line) {
        CodeArea area = area();
        int at = doc.fx(() -> area.getAbsolutePosition(line - 1, 0));
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElse(null));
        if (b != null) { // a line off the screen is folded without the pointer
            video.moveTo(b.getMinX() - 50, b.getCenterY());
            video.pause(250);
        }
        XmlCodeEditorV2 editor = editor();
        doc.onFx(() -> editor.getFoldingManager().fold(line - 1));
        video.pause(500);
    }

    void unfoldAll() {
        XmlCodeEditorV2 editor = editor();
        doc.onFx(editor::unfoldAll);
        video.pause(400);
    }

    /** Whether the active document has problems other than hints. */
    boolean hasErrorsOrWarnings() {
        return host().getActiveProblems().stream().anyMatch(p -> "error".equalsIgnoreCase(p.severity())
                || "warning".equalsIgnoreCase(p.severity()) || "fatal".equalsIgnoreCase(p.severity()));
    }

    /** The row of the problems list whose text contains {@code text}. */
    Node problemRow(String text) throws Exception {
        ListView<?> list = doc.find("#problems-panel-list");
        Node[] found = new Node[1];
        doc.waitUntilFx(10, () -> {
            for (Node cell : list.lookupAll(".list-cell")) {
                boolean match = cell instanceof Labeled l && l.getText() != null && l.getText().contains(text)
                        || cell.lookupAll(".label").stream().anyMatch(x -> x instanceof Labeled l2
                                && l2.getText() != null && l2.getText().contains(text));
                if (match) {
                    found[0] = cell;
                    return true;
                }
            }
            return false;
        });
        return found[0];
    }
}
