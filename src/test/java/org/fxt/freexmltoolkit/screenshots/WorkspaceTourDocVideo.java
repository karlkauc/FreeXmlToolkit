package org.fxt.freexmltoolkit.screenshots;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "A tour of the workspace" (storyboard
 * {@code videos/workspace-tour.json}, after {@code docs/get-started/workspace-tour.md}): open the
 * example from the start page, visit three activities, switch the views, put the caret on an
 * element for the Properties pane, look at the status bar and collapse both side panes.
 *
 * <pre>{@code
 * python3 scripts/make-video.py workspace-tour --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class WorkspaceTourDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        // a first start: without recent files the start page offers "Open an Example"
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
        doc.settle(1500);
        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("welcome-cards", () -> {
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.7));
                video.spoken(0.3);
                video.moveTo(doc.<Node>find("#welcome-new"));
                video.pause(500);
                video.moveTo(doc.<Node>find("#welcome-open"));
                video.pause(500);
                video.moveTo(doc.<Node>find("#welcome-openfolder"));
                video.spoken(0.65);
                video.moveTo(doc.<Node>find("#welcome-tasks"));
            });

            video.scene("welcome", () -> {
                video.spoken(0.6);
                Node card = doc.find("#welcome-example");
                video.zoom(card);
                video.click(card);
                WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                        () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
                video.pause(500);
                video.zoomOut();
            });

            video.scene("overview", () -> {
                video.spoken(0.12);
                moveToIfShown(video, "#activityBarHost");
                video.spoken(0.3);
                moveToIfShown(video, "#leftPanelWrapper");
                video.spoken(0.5);
                moveToIfShown(video, "#editorHost");
                video.spoken(0.65);
                moveToIfShown(video, "#inspectorWrapper");
                video.spoken(0.8);
                moveToIfShown(video, "#shell-menu-bar");
                video.spoken(0.92);
                video.moveTo(doc.<Node>find("#status-encoding"));
            });

            video.scene("activities", () -> {
                Bounds bar = video.screenBounds(doc.find("#activityBarHost"));
                Bounds panel = video.screenBounds(doc.find("#leftPanelWrapper"));
                video.zoom(new BoundingBox(bar.getMinX(), bar.getMinY(), panel.getMaxX() - bar.getMinX(),
                        Math.min(bar.getHeight(), 640)));
                video.spoken(0.3);
                video.moveTo(doc.<Node>find("#activity-explorer"));
                for (String[] step : new String[][] {{"0.42", "search"}, {"0.55", "validation"}, {"0.68", "transform"},
                        {"0.8", "schema"}, {"0.92", "pdf"}}) {
                    video.spoken(Double.parseDouble(step[0]));
                    video.click(doc.<Node>find("#activity-" + step[1]));
                }
            });

            video.scene("pages", () -> {
                video.zoomOut();
                video.spoken(0.25);
                video.click(doc.<Node>find("#activity-schema-library"));
                video.pause(1500);
                video.spoken(0.5);
                video.click(doc.<Node>find("#activity-settings"));
                video.pause(1500);
                video.spoken(0.8);
                video.click(doc.<Node>find("#activity-explorer"));
                video.pause(400);
                video.click(tabHeader("order.xml"));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
            });

            video.scene("open-editors", () -> {
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.15);
                video.moveTo(doc.<Node>find("#explorer-open-editors-scroll"));
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#explorer-open-folder"));
            });

            video.scene("favorites", () -> {
                video.spoken(0.2);
                video.showKey("de".equals(System.getProperty("fxt.locale")) ? "Strg+D" : "Ctrl+D");
                doc.onFx(() -> doc.shell().addActiveDocumentToFavorites());
                video.pause(800);
                video.spoken(0.45);
                video.click(doc.<Node>find("#explorer-tab-favorites"));
                video.pause(1200);
                video.click(doc.<Node>find("#explorer-tab-recent"));
                video.spoken(0.85);
                video.zoomOut();
                video.moveTo(doc.<Node>find("#header-favorites"));
            });

            video.scene("editor", () -> {
                Bounds tabs = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(tabs.getMinX(), tabs.getMinY() - 50, tabs.getWidth() * 0.6, 360));
                video.spoken(0.25);
                Bounds tab = video.screenBounds(tabHeader("order.xml"));
                video.rightClick(tab.getCenterX(), tab.getCenterY());
                video.spoken(0.92);
                video.key(KeyCode.ESCAPE, "Esc");
                doc.onFx(() -> javafx.stage.Window.getWindows().stream()
                        .filter(w -> w instanceof javafx.scene.control.ContextMenu && w.isShowing()).toList()
                        .forEach(javafx.stage.Window::hide));
                video.zoomOut();
            });

            video.scene("views", () -> {
                int at = host.getActiveText().orElseThrow().indexOf("<customer>") + 2;
                CodeArea area = doc.findType(CodeArea.class);
                Bounds tag = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElseThrow());
                video.click(tag.getCenterX(), tag.getCenterY());
                Bounds tabs = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(tabs.getMinX(), tabs.getMinY() - 50, tabs.getWidth(), tabs.getHeight() * 0.6));
                video.spoken(0.3);
                List<Node> modes = viewSwitch(host);
                video.click(modes.get(1)); // Tree
                video.pause(2000);
                video.spoken(0.6);
                video.click(modes.get(2)); // Graphic
                video.pause(2000);
                video.spoken(0.9);
                video.click(modes.get(0)); // Text
                video.pause(500);
                video.zoomOut();
            });

            video.scene("inspector", () -> {
                video.spoken(0.1);
                int at = host.getActiveText().orElseThrow().indexOf("<item sku=\"BK-101\"") + 3;
                CodeArea area = doc.findType(CodeArea.class);
                Bounds tag = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElseThrow());
                video.click(tag.getCenterX(), tag.getCenterY());
                video.pause(900);
                video.zoom(doc.<Node>find("#inspectorWrapper"));
                video.spoken(0.9);
                video.zoomOut();
            });

            video.scene("status", () -> {
                Bounds encoding = video.screenBounds(doc.find("#status-encoding"));
                video.zoom(new BoundingBox(0, encoding.getMinY() - 6, encoding.getMaxX() + 40, encoding.getHeight() + 12));
                video.spoken(0.3);
                video.moveTo(doc.<Node>find("#status-schema"));
                video.spoken(0.8);
                video.moveTo(encoding.getCenterX(), encoding.getCenterY());
            });

            video.scene("header", () -> {
                video.zoomOut();
                Node crumb = doc.find("#breadcrumb");
                Bounds b = video.screenBounds(crumb);
                Bounds stage = video.screenBounds(doc.stage().getScene().getRoot());
                video.zoom(new BoundingBox(stage.getMinX(), b.getMinY() - 30, stage.getWidth(), 220));
                video.moveTo(crumb);
                video.spoken(0.25);
                video.moveTo(doc.<Node>find("#searchPill"));
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#header-favorites"));
                video.pause(500);
                video.moveTo(doc.<Node>find("#header-help"));
                video.spoken(0.7);
                Node theme = doc.find("#themeToggleButton");
                video.click(theme);
                video.pause(1800);
                video.zoomOut();
                video.pause(1200);
                video.click(theme);
                video.spoken(0.97);
            });

            video.scene("menus", () -> {
                video.zoomOut();
                video.click(doc.<Node>find("#menu-file"));
                video.spoken(0.45);
                video.key(KeyCode.ESCAPE, "Esc");
                hideMenus();
                video.pause(300);
                video.click(doc.<Node>find("#menu-help"));
                video.pause(600);
                video.click(video.inPopup("#menu-help-shortcuts"));
                video.pause(1200);
                javafx.scene.Scene dialog = video.activeScene();
                javafx.stage.Window window = doc.fx(dialog::getWindow);
                if (window != doc.stage()) {
                    video.zoom(doc.fx(() -> new BoundingBox(window.getX(), window.getY(), window.getWidth(), window.getHeight())));
                }
                video.spoken(0.97);
                video.key(KeyCode.ESCAPE, "Esc");
                doc.onFx(() -> {
                    if (window != doc.stage()) {
                        window.hide();
                    }
                });
                video.zoomOut();
            });

            video.scene("room", () -> {
                video.spoken(0.2);
                video.click(doc.<Node>find("#toggle-left-panel"));
                video.pause(900);
                video.click(doc.<Node>find("#toggle-inspector"));
                video.spoken(0.75);
                video.click(doc.<Node>find("#toggle-left-panel"));
                video.pause(700);
                video.click(doc.<Node>find("#toggle-inspector"));
            });
        } finally {
            video.stop();
        }
    }

    /** Glides to the node when it is on screen (the welcome page of a first start hides some panels). */
    private void moveToIfShown(DocVideoRecorder video, String selector) {
        Node node = doc.find(selector);
        if (node != null && doc.fx(() -> node.localToScreen(node.getBoundsInLocal()) != null && node.isVisible())) {
            video.moveTo(node);
        } else {
            System.out.println("[video] not shown: " + selector);
        }
    }

    /** Hides any open menu popup (Escape reaches the menu bar's popups only with a window manager). */
    private void hideMenus() {
        doc.onFx(() -> javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.PopupWindow && w.isShowing()).toList()
                .forEach(javafx.stage.Window::hide));
    }

    /** @return the header of the editor tab whose title starts with {@code name} */
    private Node tabHeader(String name) {
        EditorHost host = doc.host();
        return doc.fx(() -> host.lookupAll(".tab").stream()
                .filter(t -> t.lookupAll(".tab-label").stream().anyMatch(l -> l instanceof javafx.scene.control.Labeled lb
                        && lb.getText() != null && lb.getText().startsWith(name)))
                .filter(t -> t.localToScreen(t.getBoundsInLocal()) != null)
                .findFirst().orElseThrow());
    }

    /** The buttons of the view switch that are showing, in their order (Text, Tree, Graphic). */
    private List<Node> viewSwitch(EditorHost host) {
        return doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                .filter(n -> n.isVisible() && n.isManaged()).toList());
    }
}
