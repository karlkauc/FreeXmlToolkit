package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlDocumentsDialog;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Documents: link, embed, extract", film 7 of "FundsXML for developers" (storyboard
 * {@code videos/fundsxml/dev-documents.json}): the Documents block of the realistic documents delivery
 * (a prospectus as a link, a PRIIPs KID and a factsheet embedded as base64), then the Documents tool of the
 * FundsXML panel: the list, saving the embedded KID (shown in the PDF preview) and embedding a new factsheet,
 * which the FundsXML validation accepts. The file choosers are replaced by the dialog's test hooks.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-documents --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevDocumentsDocVideo {

    static final String FILE = "Fund-Documents_2025-10.xml";
    private static final String KID_FILE = "EX-Multi-Asset_PRIIPs-KID_2025-02_en.pdf";
    private static final String NEW_FILE = "EX-Multi-Asset_Factsheet_ATXMPLF03024_2025-10_en.pdf";
    private static final String NEW_ISIN = "ATXMPLF03024";

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        FundsXmlVideoFiles.enableExtension();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path folder = FundsXmlVideoFiles.folder("dev-documents");
        doc.open(folder.resolve(FILE), "<Documents>");
        doc.settle(1500);

        // the PDF to embed: a fictitious factsheet (the bytes of the delivery's factsheet under the new name)
        Path newPdf = folder.resolve(NEW_FILE);
        Files.write(newPdf, FundsXmlDocuments.decode(Files.readString(folder.resolve(FILE)), 3).bytes());
        Path saved = folder.resolve("saved");
        Files.createDirectories(saved);
        FundsXmlDocumentsDialog.openFileChooser = title -> newPdf.toFile();
        FundsXmlDocumentsDialog.saveFileChooser = (title, name) -> saved.resolve(name).toFile();

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            // ----- the Documents block
            video.scene("where", () -> {
                // from the bottom up: a fold changes nothing above it
                kit.fold(documentLine(kit, 3));
                kit.fold(documentLine(kit, 2));
                kit.fold(documentLine(kit, 1));
                kit.fold(kit.line("<ControlData>"));
                video.moveTo(1300, 760); // away from the text: no hover tooltip
                video.pause(400);
                video.zoom(kit.lines(kit.line("<FundsXML4 "), kit.line("</FundsXML4>")));
                video.spoken(0.55);
                video.zoom(kit.lines(documentLine(kit, 1), documentLine(kit, 3)));
            });

            video.scene("type", () -> {
                video.zoomOut();
                kit.unfoldAll();
                int at = show(kit, "<Documents>");
                int type = kit.line("<Type>", at);
                video.zoom(kit.lines(type, type + 2));
                video.spoken(0.4);
                video.zoom(kit.textBounds("<ListedType>Prospectus</ListedType>"));
            });

            video.scene("typelist", () -> {
                video.zoomOut();
                video.spoken(0.1);
                kit.clickInto("Prospectus</ListedType>", 0);
                video.pause(900);
                video.zoom(facets());
                video.moveTo(doc.<Node>find("#inspector-facets")); // away from the text
                video.spoken(0.97);
                video.zoomOut();
            });

            video.scene("lang", () -> {
                int language = kit.line("<Language>en</Language>", documentLine(kit, 1));
                int countries = kit.line("</PublicationCountries>", language);
                video.zoom(kit.lines(language, language));
                video.spoken(0.25);
                video.zoom(kit.lines(language + 1, language + 1));
                video.spoken(0.4);
                video.zoom(kit.lines(language + 2, countries));
                video.spoken(0.62);
                int kid = show(kit, "<ListedType>PRIIPS-KID</ListedType>");
                video.zoom(kit.lines(kid - 1, kit.line("</PublicationCountries>", kid)));
            });

            video.scene("assign", () -> {
                video.zoomOut();
                int fund = show(kit, "<Fund>");
                video.zoom(kit.lines(fund, kit.line("</Fund>", fund)));
                video.spoken(0.6);
                int classes = show(kit, "<ShareClasses>");
                int end = kit.line("</ShareClasses>", classes);
                video.zoom(kit.lines(classes, end));
            });

            video.scene("meta", () -> {
                video.zoomOut();
                int kid = kit.line("<ListedType>PRIIPS-KID</ListedType>");
                int name = kit.line("<Name>PRIIPs KID", kid);
                show(kit, "<Name>PRIIPs KID");
                int size = kit.line("<SizeInBytes>", name);
                video.zoom(kit.lines(name, name + 1));
                video.spoken(0.22);
                video.zoom(kit.lines(name + 2, name + 3));
                video.spoken(0.5);
                video.zoom(kit.lines(name + 4, name + 6));
                video.spoken(0.9);
                video.zoom(kit.lines(size, size));
            });

            // ----- link or embed
            video.scene("link", () -> {
                video.zoomOut();
                int url = show(kit, "<DocumentURL>");
                video.spoken(0.2);
                video.zoom(kit.textBounds("<DocumentURL>"));
                video.spoken(0.35);
                video.zoom(kit.lines(url, url));
            });

            video.scene("embed", () -> {
                video.zoomOut();
                int binary = kit.line("<BinaryData>");
                int end = kit.line("</BinaryData>", binary);
                kit.scrollTo(binary - 3);
                video.zoom(kit.lines(binary - 1, binary + 14));
                video.spoken(0.3);
                // scroll slowly through the base64 block under the zoomed region
                CodeArea area = kit.area();
                int first = doc.fx(area::firstVisibleParToAllParIndex);
                int last = Math.max(first, end - 16);
                for (int p = first; p <= last; p++) {
                    int top = p;
                    doc.onFx(() -> area.showParagraphAtTop(top));
                    video.pause(220);
                }
                video.spoken(0.97);
                video.zoomOut();
            });

            // ----- the Documents tool
            video.scene("tool", () -> {
                video.click(doc.<Node>find("#activity-fundsxml"));
                doc.waitUntilFx(10, () -> doc.find("#fundsxml-documents") != null);
                video.pause(600);
                Bounds button = video.screenBounds(doc.find("#fundsxml-documents"));
                video.zoom(new BoundingBox(button.getMinX() - 20, button.getMinY() - 60, 520, 160));
                video.spoken(0.12);
                video.click(doc.<Node>find("#fundsxml-documents"));
                TableView<?> table = documentsTable();
                video.pause(500);
                video.zoom(video.inDialog("#documents-dialog"));
                video.spoken(0.3);
                video.moveTo(header(table, I18n.t("FundsXmlPanel.documents-col-type")));
                video.spoken(0.38);
                video.moveTo(header(table, I18n.t("FundsXmlPanel.documents-col-language")));
                video.spoken(0.46);
                video.moveTo(header(table, I18n.t("FundsXmlPanel.documents-col-assigned")));
                video.spoken(0.56);
                video.moveTo(header(table, I18n.t("FundsXmlPanel.documents-col-storage")));
                video.spoken(0.66);
                video.moveTo(header(table, I18n.t("FundsXmlPanel.documents-col-size")));
                video.spoken(0.78);
                video.click(row(table, 0));
                video.pause(300);
                video.moveTo(video.inDialog("#documents-open-link"));
            });

            video.scene("save", () -> {
                TableView<?> table = documentsTable();
                video.spoken(0.08);
                video.click(row(table, 1));
                video.spoken(0.18);
                video.click(video.inDialog("#documents-save"));
                Path kid = saved.resolve(KID_FILE);
                doc.waitUntilFx(20, () -> Files.isRegularFile(kid) && Files.size(kid) > 0);
                Label message = (Label) video.inDialog("#documents-message");
                doc.waitUntilFx(10, () -> message.getText() != null && message.getText().contains(KID_FILE));
                video.pause(400);
                Bounds m = video.screenBounds(message);
                video.zoom(new BoundingBox(m.getMinX() - 20, m.getMinY() - 70, Math.max(m.getWidth(), 760) + 40, 120));
                video.spoken(0.72);
                video.zoomOut();
                video.click(closeButton());
                doc.waitUntilFx(10, () -> !dialogShowing());
                doc.onFx(() -> host.openPdfPreview(kid.toFile()));
                doc.waitUntilFx(20, () -> host.getActiveDocument().isEmpty() && doc.find(".tab-pane") != null);
                video.pause(1500);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight()));
                video.spoken(0.97);
                video.zoomOut();
            });

            video.scene("embedfile", () -> {
                // back to the delivery, the Documents tool again
                doc.onFx(host::closeActiveTab);
                doc.waitUntilFx(10, () -> host.getActiveText().map(t -> t.contains("<Documents>")).orElse(false));
                video.pause(300);
                video.click(doc.<Node>find("#fundsxml-documents"));
                TableView<?> table = documentsTable();
                video.pause(400);
                video.click(video.inDialog("#documents-embed"));
                doc.waitUntilFx(10, () -> video.activeScene().getRoot().lookup("#documents-embed-type") != null);
                video.pause(500);
                video.zoom(video.inDialog("#documents-embed-form"));
                video.click(video.inDialog("#documents-embed-type"));
                video.pause(400);
                video.click(popupCell("Factsheet"));
                video.pause(300);
                video.click(video.inDialog("#documents-embed-language"));
                video.typeIntoFocused("en");
                video.pause(300);
                video.click(video.inDialog("#documents-embed-assignment"));
                video.pause(400);
                video.click(popupCell(NEW_ISIN));
                video.pause(500);
                video.click(defaultButton());
                doc.waitUntilFx(20, () -> host.getActiveText().map(t -> t.contains(NEW_FILE)).orElse(false));
                doc.waitUntilFx(20, () -> table.getItems().size() == 4);
                video.zoom(video.inDialog("#documents-dialog"));
                video.pause(1500);
                video.click(closeButton());
                doc.waitUntilFx(10, () -> !dialogShowing());
                // the new Document in the text
                int fileName = kit.line("<FileName>" + NEW_FILE);
                int start = documentLine(kit, 4);
                kit.scrollTo(start - 1);
                video.zoom(kit.lines(start, fileName + 6));
                video.spoken(0.72);
                video.pause(1200);
                video.zoom(kit.lines(fileName + 1, fileName + 10));
                video.spoken(0.84);
                video.pause(1200);
                video.zoomOut();
                video.click(doc.<Node>find("#fundsxml-validate"));
                String valid = I18n.t("FundsXmlRunner.valid-against-fundsxml-schema", "").replace(".", "").strip();
                Label[] status = new Label[1];
                doc.waitUntilFx(60, () -> {
                    for (Node n : doc.stage().getScene().getRoot().lookupAll(".label")) {
                        if (n instanceof Label l && l.getText() != null && l.getText().startsWith(valid) && l.isVisible()) {
                            status[0] = l;
                            return true;
                        }
                    }
                    return false;
                });
                Bounds s = video.screenBounds(status[0]);
                video.zoom(new BoundingBox(s.getMinX() - 20, s.getMinY() - 120, Math.max(s.getWidth(), 420) + 40, 200));
                video.spoken(1);
                video.zoomOut();
            });
        } finally {
            FundsXmlDocumentsDialog.openFileChooser = null;
            FundsXmlDocumentsDialog.saveFileChooser = null;
            video.stop();
        }
    }

    /** Scrolls so that {@code snippet}'s line is near the top and returns that line. */
    private static int show(FundsXmlVideoKit kit, String snippet) {
        int line = kit.line(snippet);
        kit.scrollTo(Math.max(1, line - 2));
        return line;
    }

    /** The line of the {@code n}-th {@code <Document>} (1-based). */
    private static int documentLine(FundsXmlVideoKit kit, int n) {
        int at = -1;
        for (int i = 0; i < n; i++) {
            at = kit.offset("<Document>", at + 1);
        }
        return kit.lineOf(at);
    }

    /**
     * The facets (the allowed values) in the Properties panel: scrolled to the top of the panel's view and
     * returned as the region to zoom to.
     */
    private Bounds facets() throws Exception {
        doc.waitUntilFx(10, () -> doc.find("#inspector-facets") != null);
        Node box = doc.find("#facetBox");
        doc.onFx(() -> {
            for (Node n = box.getParent(); n != null; n = n.getParent()) {
                if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
                    Node content = sp.getContent();
                    double y = content.sceneToLocal(box.localToScene(0, 0)).getY() - 40;
                    double range = content.getBoundsInLocal().getHeight() - sp.getViewportBounds().getHeight();
                    sp.setVvalue(range <= 0 ? 0 : Math.min(1, Math.max(0, y / range)));
                    break;
                }
            }
        });
        doc.settle(500);
        Bounds panel = doc.fx(() -> doc.<Node>find("#inspectorWrapper").localToScreen(
                doc.<Node>find("#inspectorWrapper").getBoundsInLocal()));
        Bounds b = doc.fx(() -> box.localToScreen(box.getBoundsInLocal()));
        double top = Math.max(panel.getMinY(), b.getMinY() - 30);
        double bottom = Math.min(panel.getMaxY(), b.getMaxY() + 10);
        return new BoundingBox(panel.getMinX(), top, panel.getWidth(), bottom - top);
    }

    /** The table of the Documents dialog, once it shows its rows. */
    private TableView<?> documentsTable() throws Exception {
        TableView<?>[] table = new TableView<?>[1];
        doc.waitUntilFx(20, () -> {
            for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                if (w.isShowing() && w.getScene() != null
                        && w.getScene().getRoot().lookup("#documents-table") instanceof TableView<?> t
                        && !t.getItems().isEmpty()) {
                    table[0] = t;
                    return true;
                }
            }
            return false;
        });
        return table[0];
    }

    private boolean dialogShowing() {
        return javafx.stage.Window.getWindows().stream().anyMatch(w -> w.isShowing() && w.getScene() != null
                && w.getScene().getRoot().lookup("#documents-table") != null);
    }

    private Node header(TableView<?> table, String text) {
        return doc.fx(() -> table.lookupAll(".column-header").stream()
                .filter(h -> h.lookupAll(".label").stream()
                        .anyMatch(l -> l instanceof Label label && text.equals(label.getText())))
                .findFirst().orElseThrow(() -> new IllegalStateException("No column " + text)));
    }

    private Node row(TableView<?> table, int index) {
        return doc.fx(() -> table.lookupAll(".table-row-cell").stream()
                .filter(r -> r instanceof TableRow<?> tr && tr.getIndex() == index && !tr.isEmpty())
                .findFirst().orElseThrow(() -> new IllegalStateException("No row " + index)));
    }

    /** The Close button of the Documents dialog. */
    private Node closeButton() {
        String close = I18n.t("FundsXmlPanel.documents-close");
        return doc.fx(() -> javafx.stage.Window.getWindows().stream()
                .filter(w -> w.isShowing() && w.getScene() != null && w.getScene().getRoot().lookup("#documents-table") != null)
                .flatMap(w -> w.getScene().getRoot().lookupAll(".button").stream())
                .filter(b -> b instanceof Button button && close.equals(button.getText()))
                .findFirst().orElseThrow());
    }

    /** The default (OK) button of the newest dialog. */
    private Node defaultButton() {
        javafx.scene.Scene scene = doc.fx(() -> javafx.stage.Window.getWindows().stream()
                .filter(w -> w.isShowing() && w.getScene() != null
                        && w.getScene().getRoot().lookup("#documents-embed-form") != null)
                .map(javafx.stage.Window::getScene).findFirst().orElseThrow());
        return doc.fx(() -> scene.getRoot().lookupAll(".button").stream()
                .filter(b -> b instanceof Button button && button.isDefaultButton())
                .findFirst().orElseThrow());
    }

    /** The cell of an open combo box list whose text contains {@code text}. */
    private Node popupCell(String text) throws Exception {
        Node[] found = new Node[1];
        doc.waitUntilFx(5, () -> {
            for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                if (w instanceof javafx.stage.PopupWindow && w.isShowing() && w.getScene() != null) {
                    for (Node n : w.getScene().getRoot().lookupAll(".list-cell")) {
                        if (n instanceof javafx.scene.control.ListCell<?> c && c.getText() != null
                                && c.getText().contains(text) && n.localToScreen(n.getBoundsInLocal()) != null) {
                            found[0] = n;
                            return true;
                        }
                    }
                }
            }
            return false;
        });
        return found[0];
    }
}
