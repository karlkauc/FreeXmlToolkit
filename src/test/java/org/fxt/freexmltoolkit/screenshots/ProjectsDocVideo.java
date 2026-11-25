package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ProjectActions;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the tutorial video "Projects" (storyboard {@code videos/projects.json}, after
 * {@code docs/guides/projects.md}): an open project in the Explorer, its bindings in Project
 * Settings, a file validated against the schema the project binds, Validate Project, and the bound
 * scenario run from the Transform panel. The project and its files are synthetic.
 *
 * <pre>{@code
 * python3 scripts/make-video.py projects --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ProjectsDocVideo {

    private static final String PROJECT = """
            {
              "name": "Shop orders",
              "folders": ["orders", "schemas", "stylesheets"],
              "schemaBindings": [
                { "files": "orders/*.xml", "schema": "schemas/order.xsd",
                  "schematron": "schemas/order-rules.sch", "scenario": "Order summary" }
              ],
              "catalogs": [],
              "scenarios": [
                { "name": "Order summary", "stylesheet": "stylesheets/order-summary.xsl", "input": "${file}",
                  "parameters": {}, "outputFormat": "HTML", "output": "out/${name}.html",
                  "expected": "expected/${name}.html" }
              ]
            }
            """;

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @AfterEach
    void closeProject() {
        doc.onFx(() -> {
            ProjectService.getInstance().close();
            ProjectService.getInstance().clearRecent();
        });
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        // before the film starts: the project is written and open
        Path dir = DocFiles.work("shop-orders");
        Path projectFile = dir.resolve("shop.fxtproj");
        Files.writeString(projectFile, PROJECT);
        Path orders = Files.createDirectories(dir.resolve("orders"));
        String order = Files.readString(DocFiles.gettingStarted("order.xml"))
                .replaceAll("\\s+xmlns:xsi=\"[^\"]*\"", "")
                .replaceAll("\\s+xsi:noNamespaceSchemaLocation=\"[^\"]*\"", "");
        for (int i = 0; i < 3; i++) {
            String text = order.replace("PO-1042", "PO-" + (1042 + i));
            if (i == 2) {
                text = text.replace("price=\"24.90\"", "price=\"-24.90\"");
            }
            Files.writeString(orders.resolve("order-" + (1042 + i) + ".xml"), text);
        }
        Path schemas = Files.createDirectories(dir.resolve("schemas"));
        Files.copy(DocFiles.gettingStarted("order.xsd"), schemas.resolve("order.xsd"));
        Files.writeString(schemas.resolve("order-rules.sch"), """
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <sch:pattern>
                    <sch:rule context="item">
                      <sch:assert test="number(@price) gt 0">An item needs a price above zero.</sch:assert>
                    </sch:rule>
                  </sch:pattern>
                </sch:schema>
                """);
        Path stylesheets = Files.createDirectories(dir.resolve("stylesheets"));
        Files.copy(DocFiles.gettingStarted("order-summary.xslt"), stylesheets.resolve("order-summary.xsl"));
        Path expected = Files.createDirectories(dir.resolve("expected"));
        Files.writeString(expected.resolve("order-1043.html"), """
                <html><head><title>Order PO-1043</title></head><body><h1>Order PO-1043</h1>
                <p><b>Order total: 125.30</b></p></body></html>
                """);
        doc.onFx(() -> ProjectActions.openProject(projectFile));
        doc.settle(2000);
        // without an open document the start page is full width: show the Explorer as a click on it would
        doc.onFx(() -> doc.shell().toggleLeftPanel());

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("explorer", () -> {
                video.spoken(0.2);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.9);
            });

            video.scene("create", () -> {
                video.zoomOut();
                video.click(doc.<Node>find("#menu-file"));
                video.pause(500);
                Bounds project = video.screenBounds(video.inPopup("#menu-file-project"));
                video.moveTo(project.getMinX() + 40, project.getCenterY());
                video.pause(800);
                Bounds create = video.screenBounds(video.inPopup("#menu-project-new"));
                video.moveTo(create.getMinX() + 12, project.getCenterY());
                video.moveTo(create.getMinX() + 12, create.getCenterY());
                video.spoken(0.4);
                Bounds open = video.screenBounds(video.inPopup("#menu-project-open"));
                video.moveTo(open.getMinX() + 12, open.getCenterY());
                video.spoken(0.7);
                Bounds recent = video.screenBounds(video.inPopup("#menu-project-recent"));
                video.moveTo(recent.getMinX() + 12, recent.getCenterY());
                video.spoken(0.95);
                hidePopups();
            });

            video.scene("general", () -> {
                openSettings(video);
                selectTab(video, "ProjectSettingsDialog.project-settings-general.text");
                video.spoken(0.4);
                video.moveTo(video.inDialog("#project-settings-folders"));
                video.spoken(0.75);
                video.moveTo(video.inDialog("#project-settings-folder-add"));
            });

            video.scene("settings", () -> {
                selectTab(video, "ProjectSettingsDialog.project-settings-bindings.text");
                video.spoken(0.4);
                video.moveTo(video.inDialog("#project-settings-binding-table"));
                video.spoken(0.95);
            });

            video.scene("binding", () -> {
                Node table = video.inDialog("#project-settings-binding-table");
                Node row = doc.fx(() -> table.lookupAll(".table-row-cell").stream()
                        .filter(r -> r instanceof javafx.scene.control.TableRow<?> tr && tr.getIndex() == 0)
                        .findFirst().orElse(null));
                if (row != null) {
                    Bounds r = video.screenBounds(row);
                    video.doubleClick(r.getMinX() + 80, r.getCenterY());
                    video.pause(1500);
                    javafx.stage.Window inner = topWindow();
                    if (inner != null) {
                        video.zoom(doc.fx(() -> new BoundingBox(inner.getX(), inner.getY(), inner.getWidth(), inner.getHeight())));
                    }
                    video.spoken(0.9);
                    cancelTopDialog(video);
                    video.pause(500);
                }
                video.spoken(0.97);
                cancelTopDialog(video);
                video.zoomOut();
            });

            video.scene("tooltip", () -> {
                Node cell = doc.fx(() -> doc.find("#leftPanelWrapper").lookupAll(".tree-cell").stream()
                        .filter(c -> c instanceof Labeled l && "order-1043.xml".equals(l.getText()))
                        .findFirst().orElse(null));
                if (cell != null) {
                    Bounds c = video.screenBounds(cell);
                    video.zoom(doc.<Node>find("#leftPanelWrapper"));
                    video.moveTo(c.getMinX() + 60, c.getCenterY());
                    video.pause(2500);
                    video.spoken(0.6);
                    video.rightClick(c.getMinX() + 60, c.getCenterY());
                    video.pause(1500);
                    video.spoken(0.95);
                    hidePopups();
                }
                video.zoomOut();
            });

            video.scene("open", () -> {
                video.spoken(0.1);
                Node cell = doc.fx(() -> doc.find("#leftPanelWrapper").lookupAll(".tree-cell").stream()
                        .filter(c -> c instanceof Labeled l && "order-1043.xml".equals(l.getText()))
                        .findFirst().orElse(null));
                if (cell != null) {
                    Bounds c = video.screenBounds(cell);
                    video.doubleClick(c.getMinX() + 60, c.getCenterY());
                } else {
                    doc.onFx(() -> host.openFile(orders.resolve("order-1043.xml")));
                }
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("PO-1043"));
                video.pause(800);
                Bounds status = video.screenBounds(doc.find("#status-schema"));
                video.zoom(new BoundingBox(status.getMinX() - 300, status.getMinY() - 8, 700, status.getHeight() + 16));
                video.spoken(0.55);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                video.pause(1500);
            });

            video.scene("all", () -> {
                video.click(doc.<Node>find("#menu-file"));
                video.pause(500);
                Bounds v = projectMenuItem(video, "#menu-project-validate");
                video.click(v.getCenterX(), v.getCenterY());
                video.pause(2500);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
            });

            video.scene("scenario-def", () -> {
                openSettings(video);
                selectTab(video, "ProjectSettingsDialog.project-settings-scenarios.text");
                video.spoken(0.5);
                Node table = doc.fx(() -> video.activeScene().getRoot().lookupAll(".table-view").stream()
                        .filter(t -> t.localToScreen(t.getBoundsInLocal()) != null && t.isVisible()).findFirst().orElse(null));
                if (table != null) {
                    video.moveTo(table);
                }
                video.spoken(0.95);
                cancelTopDialog(video);
                video.zoomOut();
            });

            video.scene("scenario", () -> {
                video.zoomOut();
                video.click(doc.<Node>find("#activity-transform"));
                video.pause(800);
                Node section = doc.find("#transform-scenario-section");
                video.zoom(section);
                video.spoken(0.55);
                video.click(doc.<Node>find("#transform-scenario-run"));
                doc.waitUntilFx(60, () -> Files.exists(dir.resolve("out/order-1043.html")));
                video.pause(1200);
                video.spoken(0.75);
                video.moveTo(doc.<Node>find("#transform-scenario-save"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("check", () -> {
                Node section = doc.find("#transform-scenario-section");
                video.zoom(section);
                video.spoken(0.3);
                video.click(doc.<Node>find("#transform-scenario-check"));
                try {
                    doc.waitUntilFx(60, () -> {
                        Node actions = doc.stage().getScene().getRoot().lookup("#transform-check-actions");
                        return actions != null && actions.isVisible();
                    });
                } catch (Exception e) {
                    Node info = doc.stage().getScene().getRoot().lookup("#transform-scenario-info");
                    System.out.println("[video] no check actions; info: "
                            + doc.fx(() -> info instanceof Labeled l ? l.getText() : "-"));
                }
                video.pause(1500);
                video.spoken(0.95);
            });

            video.scene("differences", () -> {
                Node differences = doc.find("#transform-check-differences");
                if (differences != null && doc.fx(differences::isVisible)) {
                    video.click(differences);
                }
                video.pause(2500);
                video.zoomOut();
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.7));
                video.spoken(0.5);
                video.zoomOut();
                video.zoom(doc.<Node>find("#transform-scenario-section"));
                Node accept = doc.find("#transform-check-accept");
                if (accept != null && doc.fx(accept::isVisible)) {
                    video.click(accept);
                }
                video.pause(1500);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("share", () -> {
                doc.onFx(() -> host.openFile(projectFile));
                video.pause(2000);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.6));
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    private void openSettings(DocVideoRecorder video) throws Exception {
        video.click(doc.<Node>find("#menu-file"));
        video.pause(500);
        Bounds s = projectMenuItem(video, "#menu-project-settings");
        video.click(s.getCenterX(), s.getCenterY());
        doc.waitUntilFx(10, () -> video.activeScene().getRoot().lookup("#project-settings-tabs") != null);
        video.pause(600);
        Bounds dialog = doc.fx(() -> {
            Node root = video.activeScene().getRoot();
            return root.localToScreen(root.getBoundsInLocal());
        });
        video.zoom(dialog);
    }

    private void selectTab(DocVideoRecorder video, String key) {
        Node tab = doc.fx(() -> video.activeScene().getRoot().lookupAll(".tab").stream()
                .filter(t -> t.lookupAll(".tab-label").stream().anyMatch(l -> l instanceof Labeled label
                        && I18n.t(key).equals(label.getText())))
                .findFirst().orElseThrow());
        video.click(tab);
        video.pause(600);
    }

    private javafx.stage.Window topWindow() {
        return doc.fx(() -> javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.Stage st && st != doc.stage() && st.isShowing())
                .reduce((a, b) -> b).orElse(null));
    }

    private void cancelTopDialog(DocVideoRecorder video) {
        javafx.stage.Window w = topWindow();
        if (w == null) {
            return;
        }
        Node cancel = doc.fx(() -> w.getScene().getRoot().lookupAll(".button").stream()
                .filter(b -> b instanceof Button button && button.isCancelButton()).findFirst().orElse(null));
        if (cancel != null) {
            video.click(cancel);
        } else {
            doc.onFx(w::hide);
        }
        video.pause(400);
    }

    private void hidePopups() {
        doc.onFx(() -> javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.PopupWindow && w.isShowing()).toList()
                .forEach(javafx.stage.Window::hide));
        // a menu bar menu closed by hiding its popup still counts as showing: close the menus themselves
        doc.onFx(() -> doc.stage().getScene().getRoot().lookupAll(".menu-bar").forEach(n -> {
            if (n instanceof javafx.scene.control.MenuBar bar) {
                bar.getMenus().forEach(javafx.scene.control.Menu::hide);
            }
        }));
    }

    /**
     * Opens File → Project (the File menu is open) and glides to an item of it: first along the
     * Project row into the submenu, then up or down in it - a slant over other items would close it.
     */
    private Bounds projectMenuItem(DocVideoRecorder video, String id) throws Exception {
        Bounds project = video.screenBounds(video.inPopup("#menu-file-project"));
        video.moveTo(project.getMinX() + 40, project.getCenterY());
        video.pause(800);
        Bounds item = video.screenBounds(video.inPopup(id));
        video.moveTo(item.getMinX() + 12, project.getCenterY());
        video.pause(200);
        video.moveTo(item.getMinX() + 12, item.getCenterY());
        video.pause(300);
        return item;
    }
}
