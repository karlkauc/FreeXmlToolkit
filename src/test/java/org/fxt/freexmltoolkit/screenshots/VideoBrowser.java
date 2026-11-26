package org.fxt.freexmltoolkit.screenshots;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A real browser on the recording's X display, for tutorial videos that show web pages the
 * application writes (the HTML schema documentation). It is Chrome for Testing from the Playwright
 * cache ({@code ~/.cache/ms-playwright/chromium-*}) or {@code -Dfxt.video.chrome}, started as an app
 * window that covers the screen. Pages are steered over the DevTools protocol; the recorder's real
 * pointer clicks into it, so the film shows the pointer as on the application.
 */
final class VideoBrowser implements AutoCloseable {

    private final Process process;
    private final WebSocket socket;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();

    private VideoBrowser(Process process, WebSocket socket) {
        this.process = process;
        this.socket = socket;
    }

    /** @return the browser binary, or {@code null} when none is installed */
    static Path binary() {
        String configured = System.getProperty("fxt.video.chrome");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path cache = Path.of(System.getenv().getOrDefault("FXT_PLAYWRIGHT_CACHE",
                System.getenv("HOME") + "/.cache/ms-playwright"));
        if (!Files.isDirectory(cache)) {
            return null;
        }
        try (Stream<Path> dirs = Files.list(cache)) {
            return dirs.filter(d -> d.getFileName().toString().matches("chromium-\\d+"))
                    .max(Comparator.comparingInt(d -> Integer.parseInt(d.getFileName().toString().substring(9))))
                    .map(d -> d.resolve("chrome-linux64/chrome"))
                    .filter(Files::isExecutable).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Starts the browser on {@code page} (full screen, the given UI language). */
    static VideoBrowser open(Path page, String language, Path profile) throws Exception {
        return open(page.toUri().toString(), language, profile, true);
    }

    /**
     * Starts the browser on {@code url}; {@code shown = false} starts it beside the screen, so it can be
     * brought in later with {@link #visible(boolean)} without its start-up showing in the film.
     */
    static VideoBrowser open(String url, String language, Path profile, boolean shown) throws Exception {
        Path chrome = binary();
        if (chrome == null) {
            throw new IllegalStateException("No browser: install Chrome for Testing (npx playwright install chromium)");
        }
        Files.createDirectories(profile);
        Files.deleteIfExists(profile.resolve("DevToolsActivePort"));
        Process process = new ProcessBuilder(chrome.toString(),
                "--app=" + url, "--window-position=" + (shown ? "0" : "4000") + ",0", "--window-size=1920,1080",
                "--user-data-dir=" + profile, "--remote-debugging-port=0", "--no-first-run",
                "--no-default-browser-check", "--no-sandbox", "--test-type", "--disable-infobars",
                "--disable-features=Translate,InfiniteSessionRestore", "--force-device-scale-factor=1",
                "--hide-crash-restore-bubble", "--lang=" + language)
                .redirectErrorStream(true).redirectOutput(profile.resolve("chrome.log").toFile()).start();
        Path portFile = profile.resolve("DevToolsActivePort");
        for (int i = 0; i < 100 && !Files.exists(portFile); i++) {
            Thread.sleep(100);
        }
        String port = Files.readAllLines(portFile).getFirst().strip();
        HttpClient http = HttpClient.newHttpClient();
        String wsUrl = null;
        for (int i = 0; i < 50 && wsUrl == null; i++) {
            String list = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json")).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            for (var target : JsonParser.parseString(list).getAsJsonArray()) {
                JsonObject t = target.getAsJsonObject();
                if ("page".equals(t.get("type").getAsString())) {
                    wsUrl = t.get("webSocketDebuggerUrl").getAsString();
                }
            }
            if (wsUrl == null) {
                Thread.sleep(100);
            }
        }
        VideoBrowser[] self = new VideoBrowser[1];
        WebSocket socket = http.newWebSocketBuilder().buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                text.append(data);
                if (last) {
                    JsonObject message = JsonParser.parseString(text.toString()).getAsJsonObject();
                    text.setLength(0);
                    if (message.has("id") && self[0] != null) {
                        CompletableFuture<JsonObject> f = self[0].pending.remove(message.get("id").getAsInt());
                        if (f != null) {
                            f.complete(message);
                        }
                    }
                }
                ws.request(1);
                return null;
            }
        }).get(10, TimeUnit.SECONDS);
        self[0] = new VideoBrowser(process, socket);
        self[0].send("Page.enable", new JsonObject());
        self[0].waitLoaded();
        return self[0];
    }

    /** Sends a DevTools command and waits for its answer. */
    JsonObject send(String method, JsonObject params) {
        int id = ids.incrementAndGet();
        CompletableFuture<JsonObject> answer = new CompletableFuture<>();
        pending.put(id, answer);
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("method", method);
        message.add("params", params);
        socket.sendText(message.toString(), true).join();
        try {
            return answer.get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("No answer to " + method, e);
        }
    }

    /** Evaluates a JavaScript expression in the page; @return its value as JSON (null if none) */
    com.google.gson.JsonElement eval(String expression) {
        JsonObject params = new JsonObject();
        params.addProperty("expression", expression);
        params.addProperty("returnByValue", true);
        params.addProperty("awaitPromise", true);
        JsonObject result = send("Runtime.evaluate", params);
        JsonObject inner = result.getAsJsonObject("result").getAsJsonObject("result");
        return inner.has("value") ? inner.get("value") : null;
    }

    /** Opens a page of the site (a URL or a path relative to the current page). */
    void navigate(String url) {
        eval("location.href = new URL(" + quote(url) + ", location.href).href; true");
        sleep(500);
        waitLoaded();
    }

    /** Waits until the page is loaded and its deferred scripts ran. */
    void waitLoaded() {
        for (int i = 0; i < 100; i++) {
            com.google.gson.JsonElement state = eval("document.readyState");
            if (state != null && "complete".equals(state.getAsString())) {
                sleep(600);
                return;
            }
            sleep(100);
        }
    }

    /** @return the screen bounds of the first element matching {@code selector}, or {@code null} */
    Bounds screenBounds(String selector) {
        com.google.gson.JsonElement r = eval("(() => { const e = document.querySelector(" + quote(selector) + ");"
                + " if (!e) return null; const b = e.getBoundingClientRect();"
                + " const top = window.screenY + (window.outerHeight - window.innerHeight);"
                + " const left = window.screenX + (window.outerWidth - window.innerWidth) / 2;"
                + " return [left + b.left, top + b.top, b.width, b.height]; })()");
        if (r == null || r.isJsonNull()) {
            return null;
        }
        var a = r.getAsJsonArray();
        return new BoundingBox(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble());
    }

    /** Scrolls the element into view (smoothly, its top near the top of the window). */
    void scrollTo(String selector) {
        eval("(() => { const e = document.querySelector(" + quote(selector) + "); if (e) {"
                + " const y = e.getBoundingClientRect().top + window.scrollY - 90;"
                + " window.scrollTo({top: y, behavior: 'smooth'}); } return true; })()");
        sleep(1200);
    }

    /** Scrolls to the first heading or label whose text starts with {@code text}. */
    void scrollToText(String text) {
        eval("(() => { const e = Array.from(document.querySelectorAll('h1,h2,h3,h4,dt,strong,summary'))"
                + ".find(x => x.textContent.trim().startsWith(" + quote(text) + ")); if (e) {"
                + " const y = e.getBoundingClientRect().top + window.scrollY - 90;"
                + " window.scrollTo({top: y, behavior: 'smooth'}); } return !!e; })()");
        sleep(1200);
    }

    /** @return the screen bounds of the first {@code tags} element whose text starts with {@code text}, or null */
    Bounds boundsOfText(String tags, String text) {
        com.google.gson.JsonElement r = eval("(() => { const e = Array.from(document.querySelectorAll(" + quote(tags) + "))"
                + ".find(x => x.textContent.trim().startsWith(" + quote(text) + ")); if (!e) return null;"
                + " const b = e.getBoundingClientRect();"
                + " const top = window.screenY + (window.outerHeight - window.innerHeight);"
                + " const left = window.screenX + (window.outerWidth - window.innerWidth) / 2;"
                + " return [left + b.left, top + b.top, b.width, b.height]; })()");
        if (r == null || r.isJsonNull()) {
            return null;
        }
        var a = r.getAsJsonArray();
        return new BoundingBox(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble());
    }

    /** Scrolls by {@code pixels} (smoothly). */
    void scrollBy(int pixels) {
        eval("window.scrollBy({top: " + pixels + ", behavior: 'smooth'}); true");
        sleep(1000);
    }

    /** Types {@code text} into the focused field, one character at a time. */
    void type(String text) {
        for (char c : text.toCharArray()) {
            JsonObject params = new JsonObject();
            params.addProperty("text", String.valueOf(c));
            send("Input.insertText", params);
            sleep(110);
        }
    }

    /** Moves the window off the screen (the application shows again) or back. */
    void visible(boolean shown) {
        JsonObject window = send("Browser.getWindowForTarget", new JsonObject()).getAsJsonObject("result");
        JsonObject bounds = new JsonObject();
        bounds.addProperty("left", shown ? 0 : 4000);
        bounds.addProperty("top", 0);
        JsonObject params = new JsonObject();
        params.addProperty("windowId", window.get("windowId").getAsInt());
        params.add("bounds", bounds);
        send("Browser.setWindowBounds", params);
        sleep(400);
        if (shown) {
            raise();
        }
    }

    /** X has no window manager here: put the browser's windows above the application's. */
    private void raise() {
        String script = """
                import ctypes, re, subprocess, sys
                pid = sys.argv[1]
                tree = subprocess.run(['xwininfo', '-root', '-children'], capture_output=True, text=True).stdout
                x = ctypes.cdll.LoadLibrary('libX11.so.6')
                x.XOpenDisplay.restype = ctypes.c_void_p
                d = ctypes.c_void_p(x.XOpenDisplay(None))
                for w in re.findall(r'^\\s+(0x[0-9a-f]+)', tree, re.M):
                    prop = subprocess.run(['xprop', '-id', w, '_NET_WM_PID'], capture_output=True, text=True).stdout
                    if prop.strip().endswith(' ' + pid):
                        x.XRaiseWindow(d, ctypes.c_ulong(int(w, 16)))
                x.XFlush(d)
                """;
        try {
            new ProcessBuilder("python3", "-c", script, String.valueOf(process.pid()))
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                    .waitFor(10, TimeUnit.SECONDS);
        } catch (IOException e) {
            System.out.println("[video] could not raise the browser: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sleep(300);
    }

    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // closing anyway
        }
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
    }

    private static String quote(String text) {
        return new com.google.gson.JsonPrimitive(text).toString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
