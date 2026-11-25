package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.diff.MergeView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.FolderCompareView;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Compare and merge" (storyboard {@code videos/compare-merge.json},
 * after {@code docs/guides/compare-merge.md}): two versions of the example order as text and as XML, the
 * XML options, a folder comparison and a three-way merge with a conflict.
 *
 * <pre>{@code
 * python3 scripts/make-video.py compare-merge --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class CompareMergeDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path dir = DocFiles.work("compare");
        String original = Files.readString(DocFiles.gettingStarted("order.xml"));
        Path v1 = dir.resolve("order-v1.xml");
        Path v2 = dir.resolve("order-v2.xml");
        Files.writeString(v1, original);
        // the other system: indented with tabs, attributes in another order, one price changed
        String other = original.replace("    ", "\t")
                .replace("id=\"PO-1042\" date=\"2026-03-14\"", "date=\"2026-03-14\" id=\"PO-1042\"")
                .replace("sku=\"BK-207\" quantity=\"1\" price=\"39.50\"", "price=\"34.50\" sku=\"BK-207\" quantity=\"1\"");
        Files.writeString(v2, other);
        // folders: two deliveries
        Path left = Files.createDirectories(dir.resolve("delivery-march"));
        Path right = Files.createDirectories(dir.resolve("delivery-april"));
        for (String n : new String[] {"order-1042", "order-1043", "order-1044"}) {
            Files.writeString(left.resolve(n + ".xml"), original.replace("PO-1042", "PO-" + n.substring(6)));
        }
        Files.writeString(right.resolve("order-1042.xml"), original);
        Files.writeString(right.resolve("order-1043.xml"), other.replace("PO-1042", "PO-1043"));
        Files.writeString(right.resolve("order-1045.xml"), original.replace("PO-1042", "PO-1045"));
        // merge: a base, two changed versions with one conflict
        Path base = dir.resolve("base.xml");
        Path mine = dir.resolve("mine.xml");
        Path theirs = dir.resolve("theirs.xml");
        Files.writeString(base, original);
        Files.writeString(mine, original.replace("<city>Portsmouth</city>", "<city>Southampton</city>")
                .replace("quantity=\"3\"", "quantity=\"4\""));
        Files.writeString(theirs, original.replace("Please deliver before Friday.", "Please deliver before Monday.")
                .replace("quantity=\"3\"", "quantity=\"5\""));
        Path merged = dir.resolve("merged.xml");

        doc.onFx(() -> host.openFile(v1));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("why", () -> {
                video.spoken(0.4);
                doc.onFx(() -> host.openFile(v2));
                video.pause(1500);
                video.spoken(0.8);
                doc.onFx(() -> host.openFile(v1));
                video.pause(800);
            });

            video.scene("open", () -> {
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                Node item = video.inPopup("#menu-tools-compare");
                video.moveTo(item);
                video.spoken(0.4);
                keys.hidePopups();
                doc.onFx(() -> host.openDiffOfFiles(v1.toFile(), v2.toFile()));
                doc.waitUntilFx(20, () -> doc.stage().getScene().getRoot().lookup("#diff-mode-text") != null);
                video.pause(1500);
                video.spoken(0.95);
            });

            video.scene("text-mode", () -> {
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.7));
                video.spoken(0.35);
                Node next = button(I18n.t("DiffView.next"));
                if (next != null) {
                    video.click(next);
                    video.pause(900);
                    video.click(next);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("xml-mode", () -> {
                video.click(doc.<Node>find("#diff-mode-xml"));
                video.pause(2500);
                Node table = doc.find("#diff-xml-table");
                if (table != null) {
                    video.zoom(table);
                }
                video.spoken(0.6);
                Node row = table == null ? null : doc.fx(() -> table.lookupAll(".table-row-cell").stream()
                        .filter(r -> r instanceof javafx.scene.control.TableRow<?> tr && tr.getIndex() == 0 && !tr.isEmpty())
                        .findFirst().orElse(null));
                if (row != null) {
                    video.click(row);
                    video.pause(1200);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("similar", () -> {
                Node similar = doc.find("#diff-xml-hide-similar");
                if (similar != null) {
                    video.moveTo(similar);
                    video.spoken(0.6);
                    video.click(similar);
                    video.pause(1200);
                    video.click(similar);
                }
            });

            video.scene("ignore", () -> {
                video.click(doc.<Node>find("#diff-xml-options"));
                video.pause(1000);
                video.spoken(0.4);
                Node ignore = video.menuItem(I18n.t("DiffView.ignore-xpaths"));
                video.moveTo(ignore);
                video.pause(800);
                video.spoken(0.7);
                Node tolerance = video.menuItem(I18n.t("DiffView.numeric-tolerance"));
                video.moveTo(tolerance);
                video.spoken(0.95);
                keys.hidePopups();
            });

            video.scene("folder", () -> {
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                Node item = video.inPopup("#menu-tools-compare-folders");
                if (item != null) {
                    video.moveTo(item);
                }
                video.pause(500);
                keys.hidePopups();
                doc.onFx(() -> host.openToolTab(I18n.t("UnifiedShellView.compare", left.getFileName().toString(),
                        right.getFileName().toString()), "bi-folder2-open", new FolderCompareView(host, left, right)));
                video.pause(1500);
                Node run = doc.find("#folder-compare-run");
                if (run != null) {
                    video.click(run);
                }
                video.pause(2500);
                Node table = doc.find("#folder-compare-table");
                if (table != null) {
                    video.zoom(table);
                }
                video.spoken(0.6);
                Node xml = doc.find("#folder-compare-xml");
                if (xml != null) {
                    video.zoomOut();
                    video.click(xml);
                    video.pause(2000);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("merge", () -> {
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                Node item = video.inPopup("#menu-tools-merge");
                if (item != null) {
                    video.moveTo(item);
                }
                video.pause(500);
                keys.hidePopups();
                MergeView view = MergeView.open(base, mine, theirs, merged).get(30, TimeUnit.SECONDS);
                doc.onFx(() -> host.openToolTab(I18n.t("UnifiedShellView.merge", mine.getFileName()), "bi-sign-merge-left", view));
                video.pause(2000);
                video.spoken(0.95);
            });

            video.scene("conflict", () -> {
                Node next = doc.find("#merge-next");
                if (next != null) {
                    video.click(next);
                    video.pause(1200);
                }
                video.spoken(0.3);
                Node useLeft = doc.find("#merge-use-left");
                if (useLeft != null) {
                    video.moveTo(useLeft);
                    video.pause(600);
                    video.moveTo(doc.<Node>find("#merge-use-right"));
                    video.pause(600);
                    video.click(useLeft);
                    video.pause(1200);
                }
                video.spoken(0.75);
                video.moveTo(doc.<Node>find("#merge-save"));
                video.spoken(0.95);
            });

            video.scene("cli", () -> {
                video.zoomOut();
                video.spoken(0.95);
            });
        } finally {
            video.stop();
        }
    }

    /** The visible button whose text is {@code text}, or {@code null}. */
    private Node button(String text) {
        return doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && text.equals(b.getText()) && n.isVisible()
                        && n.localToScreen(n.getBoundsInLocal()) != null)
                .findFirst().orElse(null));
    }
}
