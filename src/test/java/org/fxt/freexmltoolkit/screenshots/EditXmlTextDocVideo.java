package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Write XML in the text editor" (storyboard
 * {@code videos/edit-xml-text.json}, after {@code docs/guides/edit-xml/}): the New File dialog,
 * schema-driven completion of an element and its attributes, a template, format and fold, go to
 * definition, bookmarks, find and replace, find in files and the tree view.
 *
 * <pre>{@code
 * python3 scripts/make-video.py edit-xml-text --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class EditXmlTextDocVideo {

    private final DocShell doc = new DocShell("video");
    private final boolean german = "de".equals(System.getProperty("fxt.locale"));

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(3000); // the schema binding (IntelliSense) loads in the background
        String original = host.getActiveText().orElseThrow();

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("new", () -> {
                video.spoken(0.1);
                shortcut(video, KeyCode.N, false, "N");
                video.pause(1500);
                Window dialog = dialogWindow();
                if (dialog != null) {
                    video.zoom(windowBounds(dialog));
                }
                video.spoken(0.85);
                closeDialogs(video);
                video.zoomOut();
            });

            video.scene("why-schema", () -> {
                video.spoken(0.2);
                CodeArea area = area();
                int at = host.getActiveText().orElseThrow().indexOf("xsi:noNamespaceSchemaLocation");
                video.zoom(lines(area, at, 3));
                video.spoken(0.5);
                video.zoomOut();
                Node schema = doc.find("#status-schema");
                Bounds sb = video.screenBounds(schema);
                video.zoom(new BoundingBox(0, sb.getMinY() - 8, sb.getMaxX() + 300, sb.getHeight() + 16));
                video.moveTo(schema);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("complete-element", () -> {
                CodeArea area = area();
                String text = host.getActiveText().orElseThrow();
                int lastItem = text.indexOf("</item>", text.indexOf("BK-318")) + "</item>".length();
                video.zoom(lines(area, lastItem, 6));
                video.spoken(0.35);
                clickAt(video, area, lastItem);
                typeIntoArea(video, area, "\n        ");
                video.spoken(0.55);
                typeIntoArea(video, area, "<");
                video.pause(1800);
                video.spoken(0.85);
                pressInArea(video, area, KeyCode.ENTER, "Enter");
                video.pause(1200);
                System.out.println("[video] after element: " + snippet(host, lastItem));
            });

            video.scene("complete-attributes", () -> {
                CodeArea area = area();
                // the caret goes into the new start tag, after "item"
                int tag = host.getActiveText().orElseThrow().indexOf("<item></item>");
                if (tag >= 0) {
                    doc.onFx(() -> area.moveTo(tag + "<item".length()));
                }
                typeIntoArea(video, area, " ");
                video.pause(1800);
                video.spoken(0.4);
                pressInArea(video, area, KeyCode.ENTER, "Enter");
                video.pause(800);
                typeIntoArea(video, area, "19.90");
                video.spoken(0.75);
                video.showKey(german ? "Strg+Leertaste" : "Ctrl+Space");
                fire(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, true, false, false));
                video.pause(1800);
                pressInArea(video, area, KeyCode.ESCAPE, "Esc");
                System.out.println("[video] after attributes: " + snippet(host, area.getCaretPosition() - 40));
            });

            video.scene("undo", () -> {
                CodeArea area = area();
                for (int i = 0; i < 12 && host.getActiveText().orElse("").contains("19.90")
                        || i < 12 && countItems(host) > 3; i++) {
                    shortcutOn(area, KeyCode.Z, false);
                    video.pause(150);
                }
                video.showKey(german ? "Strg+Z" : "Ctrl+Z");
                video.pause(600);
                video.zoomOut();
            });

            video.scene("templates", () -> {
                CodeArea area = area();
                String text = host.getActiveText().orElseThrow();
                int noteEnd = text.indexOf("</note>") + "</note>".length();
                clickAt(video, area, noteEnd);
                video.spoken(0.15);
                shortcut(video, KeyCode.T, false, "T");
                video.pause(1500);
                Window dialog = dialogWindow();
                if (dialog != null) {
                    video.zoom(windowBounds(dialog));
                    Node list = doc.fx(() -> dialog.getScene().getRoot().lookup(".list-view"));
                    selectListItem(video, list, "xml-comment");
                }
                video.spoken(0.6);
                pressDefault(video);
                video.pause(1200);
                Window params = dialogWindow();
                if (params != null) {
                    video.zoom(windowBounds(params));
                    video.pause(1500);
                    pressDefault(video);
                    video.pause(800);
                }
                video.zoomOut();
                video.zoom(lines(area(), noteEnd, 6));
                video.spoken(0.95);
                shortcutOn(area(), KeyCode.Z, false);
            });

            video.scene("format", () -> {
                // the scenes before leave their edits behind: start from the example's text again
                String now = host.getActiveText().orElse("");
                if (!now.equals(original)) {
                    System.out.println("[video] restoring the example text before format");
                    doc.onFx(() -> host.replaceActiveTextRegion(0, now.length(), original));
                    video.pause(300);
                }
                video.zoomOut();
                video.spoken(0.1);
                Node format = doc.find("#action-format");
                video.click(doc.fx(() -> format.lookup(".arrow-button")));
                video.pause(600);
                video.click(video.inPopup("#action-minify"));
                video.pause(2000);
                video.spoken(0.55);
                Bounds fb = video.screenBounds(format);
                video.click(fb.getMinX() + 30, fb.getCenterY());
                try {
                    doc.waitUntilFx(10, () -> host.getActiveText().orElse("").lines().count() >= 25);
                } catch (Exception timeout) {
                    System.out.println("[video] not formatted after 10 s: "
                            + host.getActiveText().orElse("").substring(0, 300).replace("\n", "⏎"));
                }
                if (host.getActiveText().orElse("").lines().count() < 25) {
                    System.out.println("[video] format click did not format: calling formatActive");
                    var future = doc.fx(() -> host.formatActive());
                    System.out.println("[video] formatActive applied=" + future.get(20, TimeUnit.SECONDS));
                    video.pause(500);
                }
                System.out.println("[video] lines after format: " + host.getActiveText().orElse("").lines().count());
            });

            video.scene("fold", () -> {
                CodeArea area = area();
                int customer = host.getActiveText().orElseThrow().indexOf("<customer>");
                video.zoom(lines(area, customer, 10));
                Node toggle = foldToggle(area, customer);
                video.spoken(0.2);
                if (toggle != null) {
                    video.click(toggle);
                }
                video.spoken(0.7);
                Node again = foldToggle(area(), host.getActiveText().orElseThrow().indexOf("<customer>"));
                if (again != null) {
                    video.click(again);
                }
                video.pause(600);
            });

            video.scene("zoom", () -> {
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
                CodeArea area = area();
                clickAt(video, area, host.getActiveText().orElseThrow().indexOf("<customer>") + 2);
                video.spoken(0.1);
                for (int i = 0; i < 4; i++) {
                    shortcut(video, KeyCode.ADD, false, "+");
                    video.pause(500);
                }
                video.spoken(0.35);
                shortcut(video, KeyCode.SUBTRACT, false, "-");
                video.pause(700);
                shortcut(video, KeyCode.DIGIT0, false, "0");
                video.pause(600);
                video.spoken(0.95);
            });

            video.scene("definition", () -> {
                CodeArea area = area();
                int at = host.getActiveText().orElseThrow().indexOf("<customer>") + 3;
                video.spoken(0.2);
                Bounds ch = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElseThrow());
                video.moveTo(ch.getCenterX(), ch.getCenterY());
                video.showKey(german ? "Strg+Klick" : "Ctrl+Click");
                ctrlClick(area, ch);
                video.pause(2500);
                System.out.println("[video] after ctrl-click: " + doc.fx(() -> host.getActiveDocument()
                        .map(d -> d.getDisplayName()).orElse("-")));
                video.zoomOut();
                video.spoken(0.95);
            });

            video.scene("generate-schema", () -> {
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(700);
                Node item = video.inPopup("#menu-tools-generate-xsd");
                video.moveTo(item);
                video.spoken(0.12);
                video.click(item);
                try {
                    doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("xs:schema"));
                } catch (Exception e) {
                    System.out.println("[video] no generated schema");
                }
                video.pause(1200);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.75));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("xpath", () -> {
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
                video.pause(500);
                CodeArea area = area();
                int item = host.getActiveText().orElseThrow().indexOf("<item ") + 2;
                clickAt(video, area, item);
                video.pause(800);
                video.zoom(doc.<Node>find("#inspectorWrapper"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("bookmarks", () -> {
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
                video.pause(600);
                CodeArea area = area();
                String text = host.getActiveText().orElseThrow();
                int items = text.indexOf("<items>");
                int customer = text.indexOf("<customer>");
                video.zoom(lines(area, customer, 14));
                video.spoken(0.15);
                clickBookmarkSlot(video, area, customer);
                video.pause(500);
                clickBookmarkSlot(video, area, items);
                video.spoken(0.45);
                clickAt(video, area, 0);
                pressInArea(video, area, KeyCode.F2, "F2");
                video.pause(900);
                pressInArea(video, area, KeyCode.F2, "F2");
                video.spoken(0.75);
                video.zoomOut();
                video.click(doc.<Node>find("#activity-bookmarks"));
                video.pause(1500);
            });

            video.scene("find", () -> {
                video.click(doc.<Node>find("#activity-explorer"));
                video.spoken(0.1);
                clickAt(video, area(), 0);
                shortcut(video, KeyCode.F, false, "F");
                video.pause(800);
                typeIntoFocused(video, "Cookbook");
                video.spoken(0.6);
                pressFocused(video, KeyCode.ENTER, "Enter");
                video.pause(1500);
            });

            video.scene("replace", () -> {
                shortcut(video, KeyCode.H, false, "H");
                video.pause(800);
                Node search = searchBar();
                javafx.scene.control.TextField find = (javafx.scene.control.TextField) doc.fx(() -> search.lookupAll(".text-field")
                        .stream().findFirst().orElseThrow());
                doc.onFx(() -> find.setText("Pocket"));
                Node replace = doc.fx(() -> search.lookupAll(".text-field").stream().skip(1).findFirst().orElseThrow());
                video.click(replace);
                typeIntoFocused(video, "Compact");
                video.spoken(0.55);
                Node all = doc.fx(() -> search.lookupAll(".button").stream()
                        .filter(n -> n instanceof javafx.scene.control.Labeled l && l.getText() != null
                                && (l.getText().equals("Alle") || l.getText().equals("All")))
                        .findFirst().orElseThrow());
                video.click(all);
                video.pause(1200);
                video.spoken(0.85);
                shortcutOn(area(), KeyCode.Z, false);
                video.pause(500);
                pressFocused(video, KeyCode.ESCAPE, "Esc");
                doc.onFx(() -> search.setVisible(false));
            });

            video.scene("search-files", () -> {
                video.spoken(0.05);
                shortcut(video, KeyCode.F, true, "F");
                video.pause(800);
                typeIntoFocused(video, "Nutshell");
                pressFocused(video, KeyCode.ENTER, "Enter");
                video.pause(2500);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.9);
                video.zoomOut();
                video.click(doc.<Node>find("#activity-explorer"));
            });

            video.scene("tree", () -> {
                List<Node> modes = doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                        .filter(n -> n.isVisible() && n.isManaged()).toList());
                video.click(modes.get(1));
                video.pause(1500);
                var tree = doc.findType(org.fxt.freexmltoolkit.controls.shell.schema.XmlInstanceTreeView.class);
                System.out.println("[video] tree=" + tree + " modes=" + modes.size() + " view=" + doc.fx(() ->
                        host.getActiveDocument().map(d -> String.valueOf(d.getDisplayName())).orElse("-")));
                if (tree != null) {
                    doc.onFx(() -> expandAll(tree.getRoot()));
                    video.pause(800);
                }
                int rowIndex = tree == null ? -1 : doc.fx(() -> {
                    for (int i = 0; i < tree.getExpandedItemCount(); i++) {
                        var item = tree.getTreeItem(i);
                        if (item != null && item.getValue() instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e
                                && "customer".equals(e.getQualifiedName())) {
                            tree.scrollTo(Math.max(0, i - 2));
                            return i;
                        }
                    }
                    return -1;
                });
                video.pause(600);
                Node row = rowIndex < 0 ? null : doc.fx(() -> tree.lookupAll(".tree-cell").stream()
                        .filter(n -> n instanceof javafx.scene.control.TreeCell<?> c && c.getIndex() == rowIndex)
                        .filter(n -> n.localToScreen(n.getBoundsInLocal()) != null)
                        .findFirst().orElse(null));
                System.out.println("[video] tree row index " + rowIndex + " cell " + row);
                video.spoken(0.3);
                if (row != null) {
                    Bounds b = video.screenBounds(row);
                    video.click(b.getMinX() + 120, b.getCenterY());
                    video.pause(500);
                    video.rightClick(b.getMinX() + 120, b.getCenterY());
                    video.spoken(0.75);
                    pressFocused(video, KeyCode.ESCAPE, "Esc");
                    hidePopups();
                } else {
                    System.out.println("[video] no tree row 'customer'; nodes: " + doc.fx(() -> host.lookupAll("*").stream()
                            .filter(n -> n instanceof javafx.scene.control.Labeled l && l.getText() != null
                                    && l.getText().contains("customer") && shown(n))
                            .map(n -> n.getClass().getSimpleName() + n.getStyleClass() + "<" + (n.getParent() == null ? ""
                                    : n.getParent().getClass().getSimpleName() + n.getParent().getStyleClass()))
                            .limit(8).toList()));
                }
                video.spoken(0.95);
                video.click(modes.get(0));
            });
            video.scene("save", () -> {
                video.spoken(0.1);
                Node tab = doc.fx(() -> host.lookupAll(".tab").stream().filter(t -> t.localToScreen(t.getBoundsInLocal()) != null)
                        .findFirst().orElse(null));
                if (tab != null) {
                    Bounds tb = video.screenBounds(tab);
                    video.zoom(new BoundingBox(tb.getMinX() - 20, tb.getMinY() - 20, 600, 120));
                    video.moveTo(tab);
                }
                video.spoken(0.6);
                video.zoomOut();
            });

        } finally {
            video.stop();
        }
    }

    private static void expandAll(javafx.scene.control.TreeItem<?> item) {
        if (item == null) {
            return;
        }
        item.setExpanded(true);
        item.getChildren().forEach(EditXmlTextDocVideo::expandAll);
    }

    // ----- input -----

    /** Shows the shortcut as key cap and fires it later, so a modal dialog it opens does not block the scenario. */
    private void shortcut(DocVideoRecorder video, KeyCode code, boolean shift, String key) {
        video.showKey((german ? "Strg+" : "Ctrl+") + (shift ? (german ? "Umschalt+" : "Shift+") : "") + key);
        Scene scene = doc.stage().getScene();
        Platform.runLater(() -> {
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, true, false, false));
        });
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
    }

    private void shortcutOn(Node target, KeyCode code, boolean shift) {
        doc.onFx(() -> {
            target.requestFocus();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, true, false, false));
        });
    }

    /** Types {@code text} into the code area one key at a time (KEY_TYPED, like a keyboard). */
    private void typeIntoArea(DocVideoRecorder video, CodeArea area, String text) {
        for (char c : text.toCharArray()) {
            String ch = String.valueOf(c);
            doc.onFx(() -> {
                area.requestFocus();
                if (c == '\n') {
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                } else {
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_TYPED, ch, "", KeyCode.UNDEFINED, false, false, false, false));
                }
            });
            video.pause(c == ' ' && text.isBlank() ? 20 : 140);
        }
    }

    private void pressInArea(DocVideoRecorder video, CodeArea area, KeyCode code, String label) {
        video.showKey(label);
        doc.onFx(() -> {
            area.requestFocus();
            Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", code == KeyCode.ENTER ? "\r" : "", code,
                    false, false, false, false));
            Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
        });
    }

    private void pressFocused(DocVideoRecorder video, KeyCode code, String label) {
        video.key(code, label);
    }

    private void typeIntoFocused(DocVideoRecorder video, String text) {
        video.typeIntoFocused(text);
    }

    private void fire(Node target, Event event) {
        doc.onFx(() -> Event.fireEvent(target, event));
    }

    private void clickAt(DocVideoRecorder video, CodeArea area, int offset) {
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(offset, offset + 1).orElse(null));
        if (b != null) {
            video.click(b.getMinX() + 1, b.getCenterY());
        }
        doc.onFx(() -> {
            area.requestFocus();
            area.moveTo(offset);
        });
    }

    private void ctrlClick(CodeArea area, Bounds screen) {
        doc.onFx(() -> {
            // MouseEvent's x/y are scene coordinates here; dispatch converts them to the target's
            javafx.geometry.Point2D local = area.localToScene(area.screenToLocal(screen.getCenterX(), screen.getCenterY()));
            for (javafx.event.EventType<MouseEvent> type : List.of(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED,
                    MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
                Event.fireEvent(area, new MouseEvent(type, local.getX(), local.getY(), screen.getCenterX(),
                        screen.getCenterY(), MouseButton.PRIMARY, 1, false, true, false, false,
                        type != MouseEvent.MOUSE_MOVED, false, false, true, false, true, null));
            }
        });
    }

    private void pressDefault(DocVideoRecorder video) {
        Window window = dialogWindow();
        if (window == null) {
            return;
        }
        Node ok = doc.fx(() -> window.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton() && !b.isDisabled())
                .findFirst().orElse(null));
        if (ok != null) {
            video.click(ok);
        } else {
            System.out.println("[video] no default button");
        }
    }

    private void selectListItem(DocVideoRecorder video, Node list, String text) {
        if (!(list instanceof javafx.scene.control.ListView<?> view)) {
            return;
        }
        int index = doc.fx(() -> {
            for (int i = 0; i < view.getItems().size(); i++) {
                if (String.valueOf(view.getItems().get(i)).contains(text)) {
                    return i;
                }
            }
            return 0;
        });
        doc.onFx(() -> view.scrollTo(Math.max(0, index - 2)));
        video.pause(300);
        Node cell = doc.fx(() -> view.lookupAll(".list-cell").stream()
                .filter(n -> n instanceof javafx.scene.control.ListCell<?> c && c.getIndex() == index)
                .findFirst().orElse(null));
        if (cell != null) {
            video.click(cell);
        } else {
            doc.onFx(() -> view.getSelectionModel().select(index));
        }
        video.pause(800);
    }

    private void clickBookmarkSlot(DocVideoRecorder video, CodeArea area, int offset) {
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(offset, offset + 1).orElseThrow());
        Node slot = doc.fx(() -> area.lookupAll(".bookmark-gutter-slot").stream()
                .filter(n -> {
                    Bounds b = n.localToScreen(n.getBoundsInLocal());
                    return b != null && b.getMinY() <= line.getCenterY() && b.getMaxY() >= line.getCenterY();
                }).findFirst().orElse(null));
        if (slot == null) {
            System.out.println("[video] no bookmark slot; classes: " + doc.fx(() -> area.getParent() == null ? "" : ""));
            return;
        }
        Bounds b = video.screenBounds(slot);
        video.moveTo(b.getCenterX(), b.getCenterY());
        doc.onFx(() -> Event.fireEvent(slot, new MouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1, b.getCenterX(), b.getCenterY(),
                MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, true, null)));
    }

    // ----- lookups -----

    private CodeArea area() {
        EditorHost host = doc.host();
        return doc.fx(() -> host.lookupAll(".code-area").stream()
                .filter(n -> n instanceof CodeArea c && shown(c) && c.getText().contains("<order"))
                .map(CodeArea.class::cast).findFirst().orElseThrow());
    }

    private Node foldToggle(CodeArea area, int offset) {
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(offset, offset + 1).orElse(null));
        if (line == null) {
            return null;
        }
        return doc.fx(() -> area.lookupAll(".fxt-fold-toggle").stream()
                .filter(n -> {
                    Bounds b = n.localToScreen(n.getBoundsInLocal());
                    return b != null && b.getMinY() - 4 <= line.getCenterY() && b.getMaxY() + 4 >= line.getCenterY();
                }).findFirst().orElse(null));
    }

    private Node searchBar() {
        return doc.findType(org.fxt.freexmltoolkit.controls.unified.UnifiedSearchBar.class);
    }

    private Window dialogWindow() {
        return doc.fx(() -> Window.getWindows().stream()
                .filter(w -> w instanceof Stage s && s != doc.stage() && s.isShowing())
                .reduce((a, b) -> b).orElse(null));
    }

    private void closeDialogs(DocVideoRecorder video) {
        Window dialog = dialogWindow();
        if (dialog != null) {
            video.key(KeyCode.ESCAPE, "Esc");
            video.pause(400);
            doc.onFx(() -> {
                if (dialog.isShowing()) {
                    dialog.hide();
                }
            });
        }
    }

    private void hidePopups() {
        doc.onFx(() -> Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.PopupWindow && w.isShowing()).toList()
                .forEach(Window::hide));
    }

    private Bounds windowBounds(Window w) {
        return doc.fx(() -> new BoundingBox(w.getX(), w.getY(), w.getWidth(), w.getHeight()));
    }

    /** A region of {@code count} lines starting at the line of {@code offset}, over the editor's width. */
    private Bounds lines(CodeArea area, int offset, int count) {
        Bounds editor = doc.fx(() -> area.localToScreen(area.getBoundsInLocal()));
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(offset, offset + 1).orElse(null));
        double top = line == null ? editor.getMinY() : Math.max(editor.getMinY(), line.getMinY() - line.getHeight() * 2);
        double height = (line == null ? 20 : line.getHeight()) * (count + 3);
        return new BoundingBox(editor.getMinX(), top, Math.min(editor.getWidth(), 1100), height);
    }

    private static int countItems(EditorHost host) {
        String text = host.getActiveText().orElse("");
        return text.split("<item\\b", -1).length - 1;
    }

    private static String snippet(EditorHost host, int from) {
        String text = host.getActiveText().orElse("");
        int start = Math.max(0, from);
        return text.substring(start, Math.min(text.length(), start + 120)).replace("\n", "⏎");
    }

    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }
}
