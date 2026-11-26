package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "XSD: overview and types" (storyboard {@code videos/xsd-types.json},
 * after {@code docs/guides/design-schemas/overview-view.md} and {@code types.md}).
 *
 * <pre>{@code
 * python3 scripts/make-video.py xsd-types --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsdTypesDocVideo {

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
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xsd = folder.resolve("order.xsd");
        doc.onFx(() -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("OrderType")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("overview", () -> {
                video.spoken(0.1);
                List<Node> modes = modes(host);
                video.click(modes.get(modes.size() - 1));
                video.pause(2000);
                Node table = doc.find("#overview-components");
                if (table != null) {
                    video.zoom(table);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("details", () -> {
                clickRow(video, "CountryCodeType");
                video.pause(1000);
                Node tabs = doc.find("#overview-detail-tabs");
                if (tabs != null) {
                    video.zoom(tabs);
                }
                video.spoken(0.6);
                video.zoomOut();
                Node inspector = doc.find("#inspectorWrapper");
                if (inspector != null && doc.fx(inspector::isVisible)) {
                    video.zoom(inspector);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("filter", () -> {
                Node filter = doc.find("#overview-filter");
                video.click(filter);
                video.typeIntoFocused("Type");
                video.pause(1500);
                video.spoken(0.4);
                javafx.scene.control.TextField field = (javafx.scene.control.TextField) filter;
                doc.onFx(field::clear);
                video.click(doc.<Node>find("#overview-sort"));
                video.pause(1200);
                keys.hidePopups();
                video.spoken(0.7);
                video.moveTo(doc.<Node>find("#overview-move-up"));
                video.pause(500);
                video.moveTo(doc.<Node>find("#overview-move-down"));
                video.spoken(0.95);
            });

            video.scene("rename", () -> {
                Node row = row("PriceType");
                if (row != null) {
                    Bounds b = video.screenBounds(row);
                    video.rightClick(b.getMinX() + 200, b.getCenterY());
                    video.pause(800);
                    Node item = video.menuItem(I18n.t("SchemaOverviewView.rename"));
                    video.moveTo(item);
                    video.spoken(0.7);
                    keys.hidePopups();
                }
                video.spoken(0.95);
            });

            video.scene("new-type", () -> {
                video.click(doc.<Node>find("#overview-append"));
                video.pause(800);
                Node simple = video.inPopup("#overview-append-simple_type");
                video.click(simple);
                video.pause(1500);
                // Append adds "NewSimpleType" and starts editing its name in the table
                Node editor = doc.fx(() -> doc.stage().getScene().getFocusOwner());
                if (!(editor instanceof javafx.scene.control.TextInputControl)) {
                    editor = doc.fx(() -> doc.find("#overview-components") instanceof javafx.scene.Parent table
                            ? table.lookupAll(".text-field").stream().filter(Node::isVisible).findFirst().orElse(null)
                            : null);
                }
                if (editor instanceof javafx.scene.control.TextInputControl field) {
                    Bounds b = video.screenBounds(field);
                    video.zoom(new BoundingBox(b.getMinX() - 300, b.getMinY() - 120, 1100, 320));
                    typeInto(video, field, "CurrencyType");
                    video.pause(300);
                    javafx.scene.control.TextInputControl input = field;
                    doc.onFx(() -> javafx.event.Event.fireEvent(input, new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false)));
                    video.showKey("Enter");
                    video.pause(1000);
                    video.zoomOut();
                } else {
                    System.out.println("[video] no name editor for the new simple type: " + editor);
                }
                video.spoken(0.95);
            });

            video.scene("facets", () -> {
                clickRow(video, "PriceType");
                video.pause(1000);
                Node inspector = doc.find("#inspectorWrapper");
                if (inspector != null && doc.fx(inspector::isVisible)) {
                    video.zoom(inspector);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("add-facet", () -> {
                if (row("CurrencyType") == null) {
                    System.out.println("[video] no CurrencyType row: no facets added");
                    video.spoken(0.95);
                    return;
                }
                clickRow(video, "CurrencyType");
                video.pause(1000);
                Node inspector = doc.find("#inspectorWrapper");
                if (inspector != null && doc.fx(inspector::isVisible)) {
                    video.zoom(inspector);
                }
                Node combo = doc.find("#inspector-facet-type");
                Node value = doc.find("#inspector-facet-value");
                Node add = doc.find("#inspector-facet-add");
                if (combo instanceof javafx.scene.control.ComboBox<?> box && value != null && add != null
                        && doc.fx(combo::isVisible)) {
                    video.click(combo);
                    video.pause(1500);
                    video.spoken(0.2);
                    doc.onFx(() -> {
                        box.hide();
                        selectFacet(box, "enumeration");
                    });
                    for (String v : new String[] {"EUR", "USD"}) {
                        video.click(value);
                        typeInto(video, value, v);
                        video.pause(300);
                        video.click(add);
                        video.pause(900);
                    }
                } else {
                    System.out.println("[video] no facet controls for CurrencyType");
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("list-union", () -> {
                clickRow(video, "OrderIdType");
                video.pause(800);
                video.spoken(0.95);
            });

            video.scene("complex", () -> {
                Node row = row("OrderType");
                if (row != null) {
                    Bounds b = video.screenBounds(row);
                    video.rightClick(b.getMinX() + 200, b.getCenterY());
                    video.pause(800);
                    video.click(video.menuItem(I18n.t("SchemaOverviewView.open-in-type-editor")));
                    video.pause(2500);
                }
                video.spoken(0.95);
            });

            video.scene("derivation", () -> {
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.7));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("type-library", () -> {
                video.click(doc.<Node>find("#activity-schema"));
                video.pause(1500);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.3);
                Node cell = doc.fx(() -> doc.find("#schema-simple-list") instanceof javafx.scene.Parent list
                        ? list.lookupAll(".list-cell").stream()
                                .filter(c -> c instanceof Labeled l && l.getText() != null && l.getText().contains("PriceType"))
                                .findFirst().orElse(null)
                        : null);
                if (cell != null) {
                    video.click(cell);
                    video.pause(500);
                    Bounds b = video.screenBounds(cell);
                    video.rightClick(b.getMinX() + 40, b.getCenterY());
                    video.pause(800);
                    video.spoken(0.5);
                    video.click(video.menuItem(I18n.t("TypeLibraryPanel.find-usage")));
                    video.pause(2500);
                    video.zoomOut();
                    Bounds editor = video.screenBounds(doc.find("#editorHost"));
                    video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.5));
                } else {
                    System.out.println("[video] no PriceType in the type library");
                }
                video.spoken(0.95);
                video.zoomOut();
                video.click(doc.<Node>find("#activity-explorer"));
            });

            video.scene("namespaces", () -> {
                Node schemaTab = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".tab").stream()
                        .filter(t -> t.lookupAll(".tab-label").stream()
                                .anyMatch(l -> l instanceof Labeled label && label.getText() != null
                                        && label.getText().endsWith("order.xsd")))
                        .findFirst().orElse(null));
                if (schemaTab != null) {
                    video.click(schemaTab);
                    video.pause(1200);
                }
                selectTab("#overview-tab-namespaces");
                Node tabs = doc.find("#overview-detail-tabs");
                if (tabs != null) {
                    video.zoom(tabs);
                }
                video.spoken(0.5);
                video.zoomOut();
                video.moveTo(doc.<Node>find("#overview-directive"));
                video.spoken(0.8);
                video.moveTo(doc.<Node>find("#overview-show-included"));
            });

            video.scene("xsd11", () -> {
                selectTab("#overview-tab-assertions");
                Node tabs = doc.find("#overview-detail-tabs");
                if (tabs != null) {
                    video.zoom(tabs);
                }
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    private List<Node> modes(EditorHost host) {
        return doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                .filter(n -> n.isVisible() && n.isManaged()).toList());
    }

    /** The overview table row whose cells mention {@code name}, or {@code null}. */
    private Node row(String name) {
        Node table = doc.find("#overview-components");
        if (table == null) {
            return null;
        }
        return doc.fx(() -> table.lookupAll(".table-row-cell").stream()
                .filter(r -> r.localToScreen(r.getBoundsInLocal()) != null && r.lookupAll(".table-cell").stream()
                        .anyMatch(c -> c instanceof Labeled l && name.equals(l.getText())))
                .findFirst().orElse(null));
    }

    private void clickRow(DocVideoRecorder video, String name) {
        Node r = row(name);
        if (r != null) {
            Bounds b = video.screenBounds(r);
            video.click(b.getMinX() + 200, b.getCenterY());
        } else {
            System.out.println("[video] no overview row " + name);
        }
    }

    private void selectTab(String id) {
        Node tabs = doc.find("#overview-detail-tabs");
        if (tabs instanceof javafx.scene.control.TabPane tp) {
            doc.onFx(() -> tp.getTabs().stream().filter(t -> id.substring(1).equals(t.getId())).findFirst()
                    .ifPresent(t -> tp.getSelectionModel().select(t)));
            WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        }
    }

    private void clickDefault(DocVideoRecorder video, Window dialog) {
        Node ok = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton() && !b.isDisabled())
                .findFirst().orElse(null));
        if (ok != null) {
            video.click(ok);
        } else {
            doc.onFx(dialog::hide);
        }
    }

    @SuppressWarnings("unchecked")
    private static void selectFacet(javafx.scene.control.ComboBox<?> box, String xmlName) {
        for (Object item : box.getItems()) {
            if (item instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType type
                    && type.getXmlName().equals(xmlName)) {
                ((javafx.scene.control.ComboBox<Object>) box).setValue(item);
                return;
            }
        }
        System.out.println("[video] facet not offered: " + xmlName + " in " + box.getItems());
    }

    /** Types {@code text} into a field one character at a time (the field is filled directly). */
    private void typeInto(DocVideoRecorder video, Node field, String text) {
        if (!(field instanceof javafx.scene.control.TextInputControl input)) {
            return;
        }
        doc.onFx(() -> {
            input.requestFocus();
            input.clear();
        });
        for (int i = 1; i <= text.length(); i++) {
            String part = text.substring(0, i);
            doc.onFx(() -> {
                input.setText(part);
                input.positionCaret(part.length());
            });
            video.pause(90);
        }
    }
}
