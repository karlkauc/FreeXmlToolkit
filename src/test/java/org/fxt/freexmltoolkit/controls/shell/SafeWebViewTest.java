package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.sun.net.httpserver.HttpServer;

/** HTML previews run no scripts and load nothing from the network unless allowed. */
@ExtendWith(ApplicationExtension.class)
class SafeWebViewTest {

    private StackPane root;

    @Start
    void start(Stage stage) {
        root = new StackPane();
        stage.setScene(new Scene(root, 600, 400));
        stage.show();
    }

    @Test
    void thePolicyGoesIntoTheHead() {
        String meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"" + SafeWebView.POLICY + "\">";
        assertEquals("<html><HEAD lang=\"en\">" + meta + "<title>t</title></HEAD></html>",
                SafeWebView.withPolicy("<html><HEAD lang=\"en\"><title>t</title></HEAD></html>"));
        assertEquals("<html><head>" + meta + "</head><body/></html>", SafeWebView.withPolicy("<html><body/></html>"));
        assertEquals("<head>" + meta + "</head><p>x</p>", SafeWebView.withPolicy("<p>x</p>"));
        assertTrue(SafeWebView.isRemote("HTTPS://example.org"));
        assertFalse(SafeWebView.isRemote("file:///tmp/a.html"));
    }

    @Test
    void noScriptsAndNoRemoteRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String html = "<html><head><title>before</title><link rel=\"stylesheet\" href=\"" + base + "/a.css\"></head>"
                    + "<body><img src=\"" + base + "/pixel.png\"><script>document.title = 'script ran';</script>"
                    + "<iframe src=\"" + base + "/frame\"></iframe></body></html>";
            WebView view = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                WebView v = SafeWebView.create();
                root.getChildren().setAll(v);
                SafeWebView.load(v, html);
                return v;
            });
            WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> view.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED));
            Thread.sleep(500);
            assertEquals("before", WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.getEngine().getTitle()),
                    "the script must not run");
            assertEquals(0, requests.get(), "nothing may be loaded from the network");
            assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.getEngine().isJavaScriptEnabled()));
        } finally {
            server.stop(0);
        }
    }
}
