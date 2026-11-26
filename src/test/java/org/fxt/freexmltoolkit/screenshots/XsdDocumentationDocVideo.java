package org.fxt.freexmltoolkit.screenshots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import org.fxmisc.richtext.model.TwoDimensional;
import org.fxt.freexmltoolkit.controls.shell.editor.DocumentationVideoProbe;
import org.fxt.freexmltoolkit.controls.shell.editor.DocumentationView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Document a schema as a website" (storyboard
 * {@code videos/xsd-documentation.json}, after {@code docs/guides/design-schemas/documentation.md}).
 * The generator runs in the application; the site it writes is shown in a real browser
 * ({@link VideoBrowser}, Chrome for Testing) on the same screen.
 *
 * <pre>{@code
 * python3 scripts/make-video.py xsd-documentation --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsdDocumentationDocVideo {

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
        Path work = DocFiles.work("documentation");
        Path xsd = work.resolve("shop-order.xsd");
        try (var in = getClass().getResourceAsStream("/videos/shop-order.xsd")) {
            Files.write(xsd, in.readAllBytes());
        }
        Path cli = work.resolve("fxt-cli-xsd-doc.txt");
        try (var in = getClass().getResourceAsStream("/videos/fxt-cli-xsd-doc.txt")) {
            Files.write(cli, in.readAllBytes());
        }
        Path site = work.resolve("shop-order-docs");
        deleteTree(site);
        doc.onFx(() -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("uniqueSku")).orElse(false));
        doc.settle(1500);

        String lang = System.getProperty("fxt.locale", "en");
        // a file page to start on: a data: page may not navigate to file: pages
        Path blankPage = work.resolve("blank.html");
        Files.writeString(blankPage, "<!DOCTYPE html><html><body style='background:#f8fafc'></body></html>");
        String blank = blankPage.toUri().toString();
        VideoBrowser browser = VideoBrowser.open(blank, lang, work.resolve("browser-profile"), false);
        focusApplication();
        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("why", () -> video.spoken(0.95));

            video.scene("schema", () -> {
                showLine(host, "name=\"email\"", 2);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.8));
                video.spoken(0.45);
                showLine(host, "name=\"fax\"", 2);
                video.spoken(0.95);
                video.zoomOut();
            });

            DocumentationView[] view = new DocumentationView[1];
            video.scene("generator", () -> {
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                video.click(video.inPopup("#menu-tools-generate-docs"));
                video.pause(2000);
                view[0] = doc.findType(DocumentationView.class);
                doc.onFx(() -> DocumentationVideoProbe.prepare(view[0], xsd.toFile(), site.toFile()));
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.6));
                video.moveTo(doc.<Node>find("#docgen-xsd-name"));
                video.spoken(0.45);
                video.moveTo(doc.<Node>find("#docgen-output-name"));
                video.pause(800);
                video.moveTo(doc.<Node>find("#html"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("options", () -> {
                for (String id : new String[] {"#docgen-markdown-mode", "#docgen-show-constraints", "#svgOverview",
                        "#dedupDataDictionary", "#docgen-favicon-name", "#imageFormat"}) {
                    Node n = doc.find(id);
                    if (n != null) {
                        reveal(n);
                        video.moveTo(n);
                        video.pause(1500);
                    }
                }
                video.spoken(0.95);
            });

            video.scene("languages", () -> {
                Node scan = doc.find("#docgen-scan-languages");
                reveal(scan);
                video.click(scan);
                doc.waitUntilFx(20, () -> doc.<Parent>find("#languagesPane").getChildrenUnmodifiable().size() >= 2);
                video.pause(800);
                Bounds pane = video.screenBounds(doc.find("#languagesPane"));
                video.zoom(new BoundingBox(pane.getMinX() - 60, pane.getMinY() - 120, 900, 380));
                video.moveTo(doc.<Node>find("#languagesPane"));
                video.spoken(0.6);
                ComboBox<String> fallback = doc.find("#fallbackLanguage");
                video.moveTo(fallback);
                doc.onFx(() -> fallback.getSelectionModel().select("de"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("generate", () -> {
                Node generate = doc.find("#docgen-generate");
                reveal(generate);
                video.click(generate);
                doc.waitUntilFx(120, () -> Files.isRegularFile(site.resolve("index.html"))
                        && !doc.<Node>find("#docgen-generate").isDisabled());
                video.pause(800);
                Node progress = doc.find("#docgen-progress");
                reveal(progress);
                video.zoom(video.screenBounds(progress));
                video.moveTo(progress);
                video.spoken(0.7);
                video.zoomOut();
                video.moveTo(doc.<Node>find("#docgen-status"));
                System.out.println("[video] status: " + doc.fx(() -> DocumentationVideoProbe.status(view[0])));
                video.spoken(0.95);
            });

            // ----- the website, in a real browser -----
            video.scene("start-page", () -> {
                browser.navigate(site.resolve("index.html").toUri().toString());
                browser.visible(true);
                video.pause(1000);
                for (String selector : new String[] {"a[href='complexTypes.html']", "a[href='simpleTypes.html']",
                        "a[href='dataDictionary.html']", "#search-input", "#lang-switcher-nav"}) {
                    pointAt(video, browser.screenBounds(selector));
                    video.pause(700);
                }
                video.spoken(0.4);
                pointAt(video, browser.screenBounds("a[href*='details/order_']"));
                video.pause(800);
                browser.scrollToText("Schema Information");
                video.spoken(0.75);
                browser.scrollToText("Filter");
                video.spoken(0.95);
            });

            video.scene("element-page", () -> {
                browser.scrollTo("body");
                click(video, browser.screenBounds("a[href*='details/order_']"));
                browser.waitLoaded();
                Bounds diagram = browser.screenBounds("#diagram");
                if (diagram != null) {
                    video.zoom(new BoundingBox(0, 0, 1920, Math.min(1080, diagram.getMaxY() + 420)));
                }
                video.spoken(0.3);
                browser.scrollTo("#documentation");
                video.spoken(0.7);
                browser.scrollTo("body");
                video.zoomOut();
                click(video, browser.screenBounds("#diagram ~ * a[href*='customer_'], svg a[href*='customer_']"));
                browser.waitLoaded();
                video.spoken(0.95);
            });

            video.scene("details", () -> {
                browser.scrollTo("#properties");
                video.spoken(0.25);
                browser.scrollTo("#child-elements-attributes");
                pointAt(video, browser.boundsOfText("td", "orders@northwind.example"));
                video.spoken(0.6);
                browser.scrollTo("#element-definition");
                video.spoken(0.85);
                browser.scrollTo("body");
                pointAt(video, browser.boundsOfText("a", "Schema"));
                video.spoken(0.95);
            });

            video.scene("notes", () -> {
                browser.navigate(page(site, "details/order_"));
                browser.scrollToText("Since");
                video.spoken(0.3);
                browser.navigate(page(site, "details/address_"));
                browser.scrollToText("See Also");
                pointAt(video, browser.boundsOfText("a", "country"));
                video.spoken(0.6);
                browser.navigate(page(site, "details/fax_"));
                browser.scrollToText("Deprecated");
                pointAt(video, browser.boundsOfText("h2,h3,strong,p", "Deprecated"));
                video.spoken(0.95);
            });

            video.scene("language", () -> {
                browser.navigate(page(site, "details/order_"));
                browser.scrollTo("#documentation");
                Bounds select = browser.screenBounds(".lang-switcher");
                pointAt(video, select);
                video.pause(600);
                browser.eval("(() => { const s = document.querySelector('.lang-switcher'); s.value = 'en';"
                        + " s.dispatchEvent(new Event('change', {bubbles: true})); return true; })()");
                video.spoken(0.5);
                browser.eval("(() => { const s = document.querySelector('.lang-switcher'); s.value = 'de';"
                        + " s.dispatchEvent(new Event('change', {bubbles: true})); return true; })()");
                video.spoken(0.95);
            });

            video.scene("constraints", () -> {
                browser.scrollTo("#identity-constraints");
                video.spoken(0.5);
                browser.navigate(page(site, "details/item_"));
                browser.scrollToText("Assertions");
                video.spoken(0.95);
            });

            video.scene("types", () -> {
                browser.navigate(site.resolve("complexTypes.html").toUri().toString());
                video.spoken(0.35);
                browser.navigate(site.resolve("simpleTypes/CountryCodeType.html").toUri().toString());
                browser.scrollToText("Restrictions");
                video.spoken(0.7);
                browser.scrollToText("Used In");
                video.spoken(0.95);
            });

            video.scene("dictionary", () -> {
                browser.navigate(site.resolve("dataDictionary.html").toUri().toString());
                video.spoken(0.4);
                pointAt(video, browser.screenBounds("a[href='dataDictionary.xlsx']"));
                video.spoken(0.8);
                browser.scrollBy(500);
                video.spoken(0.95);
            });

            video.scene("overview", () -> {
                browser.navigate(site.resolve("schema-svg.html").toUri().toString());
                Bounds container = browser.screenBounds("#svg-container");
                if (container != null) {
                    video.moveTo(container.getCenterX(), container.getCenterY());
                    java.awt.Robot wheel = new java.awt.Robot();
                    for (int i = 0; i < 4; i++) {
                        wheel.mouseWheel(-1);
                        video.pause(350);
                    }
                }
                video.spoken(0.6);
                Bounds fit = browser.boundsOfText("button", "Fit");
                if (fit != null) {
                    click(video, fit);
                }
                video.pause(600);
                pointAt(video, browser.boundsOfText("button", "Export"));
                video.spoken(0.95);
            });

            video.scene("search", () -> {
                browser.navigate(site.resolve("index.html").toUri().toString());
                click(video, browser.screenBounds("#search-input"));
                video.pause(300);
                browser.type("price");
                video.pause(1000);
                pointAt(video, browser.screenBounds("#search-results > *:first-child"));
                video.spoken(0.5);
                click(video, browser.screenBounds("#search-results > *:nth-child(2)"));
                browser.waitLoaded();
                video.spoken(0.95);
            });

            video.scene("publish", () -> {
                browser.navigate(site.resolve("index.html").toUri().toString());
                browser.scrollToText("Schema Information");
                pointAt(video, browser.screenBounds("a[href$='.xsd']"));
                video.spoken(0.95);
            });

            // ----- back in the application -----
            video.scene("pdf-word", () -> {
                browser.visible(false);
                focusApplication();
                video.pause(600);
                Node pdf = doc.find("#pdf");
                reveal(pdf);
                video.click(pdf);
                video.pause(800);
                for (String id : new String[] {"#docgen-page-size", "#coverPage", "#tableOfContents", "#docgen-color-scheme"}) {
                    Node n = doc.find(id);
                    if (n != null) {
                        reveal(n);
                        video.moveTo(n);
                        video.pause(900);
                    }
                }
                video.spoken(0.6);
                Node watermark = doc.find("#docgen-watermark");
                reveal(watermark);
                video.click(watermark);
                video.pause(1500);
                if (watermark instanceof ComboBox<?> box) {
                    doc.onFx(box::hide);
                }
                video.spoken(0.95);
                video.click(doc.<Node>find("#html"));
            });

            video.scene("cli", () -> {
                doc.open(cli, "fxt-cli xsd-doc");
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.75, editor.getHeight() * 0.55));
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
            browser.close();
        }
    }

    /** Gives the keyboard and menus back to the application (the browser takes the X focus when it starts). */
    private void focusApplication() {
        doc.onFx(() -> {
            doc.stage().toFront();
            doc.stage().requestFocus();
        });
        WaitForAsyncUtils.waitForFxEvents();
        doc.settle(500);
    }

    /** Glides the pointer to the middle of {@code bounds} (nothing when the element is missing). */
    private static void pointAt(DocVideoRecorder video, Bounds bounds) {
        if (bounds == null) {
            System.out.println("[video] element not found in the browser");
            return;
        }
        video.moveTo(bounds.getMinX() + Math.min(bounds.getWidth() / 2, 160), bounds.getCenterY());
    }

    /** Clicks the middle of {@code bounds} with the real pointer. */
    private static void click(DocVideoRecorder video, Bounds bounds) {
        if (bounds == null) {
            System.out.println("[video] link not found in the browser");
            return;
        }
        video.click(bounds.getMinX() + Math.min(bounds.getWidth() / 2, 160), bounds.getCenterY());
    }

    /** @return the URL of the generated page whose file name starts with {@code prefix} */
    private static String page(Path site, String prefix) throws IOException {
        Path folder = site.resolve(prefix.substring(0, prefix.lastIndexOf('/')));
        String name = prefix.substring(prefix.lastIndexOf('/') + 1);
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(f -> f.getFileName().toString().startsWith(name)).findFirst().orElseThrow()
                    .toUri().toString();
        }
    }

    /** Shows the line containing {@code marker} near the top of the editor. */
    private void showLine(EditorHost host, String marker, int above) {
        var area = host.getActiveCodeArea();
        int at = host.getActiveText().orElse("").indexOf(marker);
        if (area != null && at > 0) {
            doc.onFx(() -> area.showParagraphAtTop(Math.max(0,
                    area.offsetToPosition(at, TwoDimensional.Bias.Forward).getMajor() - above)));
            WaitForAsyncUtils.waitForFxEvents();
        }
    }

    /** Scrolls the generator's page so that {@code node} is visible. */
    private void reveal(Node node) {
        if (node == null) {
            return;
        }
        doc.onFx(() -> {
            for (Node p = node.getParent(); p != null; p = p.getParent()) {
                if (p instanceof ScrollPane scroll && scroll.getContent() != null) {
                    double contentHeight = scroll.getContent().getBoundsInLocal().getHeight();
                    double viewport = scroll.getViewportBounds().getHeight();
                    if (contentHeight <= viewport) {
                        return;
                    }
                    Bounds inContent = scroll.getContent().sceneToLocal(node.localToScene(node.getBoundsInLocal()));
                    double target = Math.max(0, inContent.getMinY() - viewport / 3);
                    scroll.setVvalue(Math.min(1, target / (contentHeight - viewport)));
                    return;
                }
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        doc.settle(300);
    }

    private static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
