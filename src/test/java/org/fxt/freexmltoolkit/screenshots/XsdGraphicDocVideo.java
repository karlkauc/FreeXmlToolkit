package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphVideoProbe;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Edit an XSD schema graphically" (storyboard
 * {@code videos/xsd-graphic.json}, after {@code docs/guides/design-schemas/graphic-view.md}): switch
 * the example schema to the Graphic view, open the root card, open the type frame's type in the
 * Type Editor, add an optional element through the Add Element dialog, save it to the schema and
 * find it in the text.
 *
 * <pre>{@code
 * python3 scripts/make-video.py xsd-graphic --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsdGraphicDocVideo {

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
        // before the film starts: the example schema is open as text
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        doc.onFx(() -> host.openFile(folder.resolve("order.xsd")));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("OrderType")).orElse(false));
        doc.settle(2000);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("switch", () -> {
                video.spoken(0.3);
                List<Node> modes = viewSwitch(host);
                Bounds first = video.screenBounds(modes.getFirst());
                Bounds last = video.screenBounds(modes.getLast());
                video.zoom(new BoundingBox(first.getMinX(), first.getMinY(),
                        last.getMaxX() - first.getMinX(), first.getHeight()));
                video.spoken(0.55);
                video.click(modes.get(2)); // Text, Tree, Graphic
                doc.waitUntilFx(30, () -> graph(host) != null
                        && XsdGraphVideoProbe.card(graph(host), "order") != null);
                video.pause(800);
                video.zoomOut();
            });

            video.scene("cards", () -> {
                // the root card is open from the start; open customer with its button
                video.zoom(doc.fx(() -> XsdGraphVideoProbe.frame(graph(host), "OrderType")));
                video.spoken(0.1);
                Bounds button = doc.fx(() -> XsdGraphVideoProbe.expandButton(graph(host),
                        XsdGraphVideoProbe.card(graph(host), "order", "customer")));
                video.click(button.getCenterX(), button.getCenterY());
                doc.waitUntilFx(10, () -> XsdGraphVideoProbe.card(graph(host), "order", "customer", "name") != null);
                video.pause(600);
                video.zoom(doc.fx(() -> {
                    Bounds root = XsdGraphVideoProbe.bounds(graph(host), XsdGraphVideoProbe.card(graph(host), "order"));
                    Bounds frame = XsdGraphVideoProbe.frame(graph(host), "OrderType");
                    return new BoundingBox(root.getMinX(), frame.getMinY(), frame.getMaxX() - root.getMinX() + 280,
                            frame.getHeight());
                }));
                video.spoken(0.35);
                pointAt(video, "order", "customer");
                video.spoken(0.55);
                pointAt(video, "order", "note");
                video.spoken(0.8);
                pointAt(video, "order", "id");
                pointAt(video, "order", "date");
            });

            video.scene("frame", () -> {
                video.spoken(0.3);
                Bounds title = doc.fx(() -> XsdGraphVideoProbe.frameTitle(graph(host), "OrderType"));
                video.moveTo(title.getMinX() + 40, title.getCenterY());
                video.spoken(0.75);
                video.doubleClick(title.getMinX() + 40, title.getCenterY());
                doc.waitUntilFx(20, () -> {
                    XsdGraphView g = graph(host);
                    return g != null && XsdGraphVideoProbe.card(g, "OrderType") != null;
                });
                video.pause(800);
                video.zoomOut();
            });

            video.scene("add", () -> {
                VisualNode type = doc.fx(() -> XsdGraphVideoProbe.card(graph(host), "OrderType"));
                if (!doc.fx(type::isExpanded)) {
                    Bounds button = doc.fx(() -> XsdGraphVideoProbe.expandButton(graph(host), type));
                    video.click(button.getCenterX(), button.getCenterY());
                    video.pause(500);
                }
                video.spoken(0.2);
                VisualNode sequence = doc.fx(() -> XsdGraphVideoProbe.compositor(type));
                Bounds seq = doc.fx(() -> XsdGraphVideoProbe.bounds(graph(host), sequence));
                video.rightClick(seq.getCenterX(), seq.getCenterY());
                video.pause(500);
                video.moveTo(video.menuItem(I18n.t("XsdContextMenuFactory.add")));
                video.pause(700);
                Node element = video.menuItem("Element");
                Bounds item = video.screenBounds(element);
                video.moveTo(item.getMinX() + 10, item.getCenterY());
                // the dialog runs a nested event loop: click from another thread and go on
                video.click(item.getCenterX(), item.getCenterY());
                doc.waitUntilFx(10, () -> video.activeScene().getRoot().lookup("#new-node-name") != null);
                video.pause(500);
                Bounds dialog = doc.fx(() -> {
                    Node root = video.activeScene().getRoot();
                    return root.localToScreen(root.getBoundsInLocal());
                });
                video.zoom(dialog);
                video.spoken(0.45);
                video.typeIntoFocused("deliveryDate");
                video.spoken(0.6);
                Node typeField = video.inDialog("#new-node-type");
                Bounds typeBounds = video.screenBounds(typeField);
                video.click(typeBounds.getMinX() + 60, typeBounds.getCenterY());
                video.typeIntoFocused("xs:date");
                video.pause(700);
                var picker = (org.fxt.freexmltoolkit.controls.shell.schema.TypePickerField) typeField;
                if (doc.fx(picker::isPopupShowing)) {
                    video.key(KeyCode.ENTER, "Enter"); // picks the first match; without the list Enter would be OK
                    video.pause(400);
                }
                video.spoken(0.8);
                javafx.scene.control.Spinner<?> min = (javafx.scene.control.Spinner<?>) video.inDialog("#new-node-min");
                video.click(min.getEditor());
                video.typeIntoFocused("0");
                video.pause(400);
            });

            video.scene("save", () -> {
                Node ok = doc.fx(() -> video.activeScene().getRoot().lookupAll(".button").stream()
                        .filter(b -> b instanceof Button button && button.isDefaultButton()).findFirst().orElseThrow());
                video.click(ok);
                doc.waitUntilFx(10, () -> XsdGraphVideoProbe.card(graph(host), "OrderType", "deliveryDate") != null);
                video.pause(600);
                video.zoom(doc.fx(() -> XsdGraphVideoProbe.bounds(graph(host),
                        XsdGraphVideoProbe.card(graph(host), "OrderType", "deliveryDate"))));
                video.spoken(0.55);
                video.zoomOut();
                Node save = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".button").stream()
                        .filter(b -> b instanceof Labeled l && I18n.t("EditorHost.save-to-schema").equals(l.getText())
                                && b.isVisible() && b.localToScreen(b.getBoundsInLocal()) != null)
                        .findFirst().orElseThrow());
                video.zoom(video.screenBounds(save));
                video.spoken(0.7);
                video.click(save);
                video.pause(1200);
                video.zoomOut();
            });

            video.scene("text", () -> {
                video.spoken(0.1);
                Node schemaTab = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".tab").stream()
                        .filter(t -> t.lookupAll(".tab-label").stream()
                                .anyMatch(l -> l instanceof Labeled label && label.getText() != null
                                        && label.getText().endsWith("order.xsd")))
                        .findFirst().orElseThrow());
                video.click(schemaTab);
                video.pause(600);
                video.click(viewSwitch(host).getFirst());
                doc.waitUntilFx(15, () -> host.getActiveText().orElse("").contains("deliveryDate"));
                video.pause(900);
                String text = host.getActiveText().orElseThrow();
                int from = text.indexOf("<xs:complexType name=\"OrderType\">");
                int to = text.indexOf("</xs:sequence>", from) + "</xs:sequence>".length();
                CodeArea area = doc.findType(CodeArea.class);
                Bounds lines = doc.fx(() -> area.getCharacterBoundsOnScreen(from, to).orElseThrow());
                video.zoom(new BoundingBox(lines.getMinX(), lines.getMinY(), 900, lines.getHeight()));
            });
        } finally {
            video.stop();
        }
    }

    /** Glides the pointer onto a card. */
    private void pointAt(DocVideoRecorder video, String... labels) {
        Bounds card = doc.fx(() -> XsdGraphVideoProbe.bounds(graph(host()), XsdGraphVideoProbe.card(graph(host()), labels)));
        video.moveTo(card.getMinX() + 40, card.getCenterY());
        video.pause(500);
    }

    /**
     * @return the diagram on screen (the schema's Graphic view or a Type Editor), or null; called on
     *         the FX thread. Looked up in the scene: the tab can hold a diagram that is not shown.
     */
    private static XsdGraphView graph(EditorHost host) {
        return visibleGraph(host);
    }

    private static XsdGraphView visibleGraph(javafx.scene.Parent parent) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof XsdGraphView view && view.getScene() != null && view.getWidth() > 0) {
                return view;
            }
            if (child instanceof javafx.scene.Parent p) {
                XsdGraphView found = visibleGraph(p);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private EditorHost host() {
        return doc.host();
    }

    /** The buttons of the view switch that are showing, in their order (Text, Tree, Graphic). */
    private List<Node> viewSwitch(EditorHost host) {
        return doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                .filter(n -> n.isVisible() && n.isManaged()).toList());
    }
}
