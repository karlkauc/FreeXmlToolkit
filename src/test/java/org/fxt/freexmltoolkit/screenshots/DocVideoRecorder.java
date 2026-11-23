package org.fxt.freexmltoolkit.screenshots;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.testfx.util.WaitForAsyncUtils;

/**
 * Records a tutorial video's screen part: {@code ffmpeg} grabs the X screen while a scenario plays
 * its scenes in the real application - the pointer glides, clicks are real, text appears key by key.
 * <p>
 * The narration exists before the recording ({@code scripts/make-video.py} speaks every scene and
 * writes {@code timing.json}), so a scene lasts at least as long as its text; {@link #spoken} waits
 * for a part of it. Everything the cut needs later goes into {@code events.json} with the time in
 * the recording: scene starts and ends, clicks, pressed keys and the regions to zoom to.
 * <p>
 * System properties (set by the {@code docVideos} task): {@code fxt.video.dir} is the folder with
 * {@code timing.json}; {@code screen.mkv} and {@code events.json} are written there.
 */
final class DocVideoRecorder {

    static final int WIDTH = 1920;
    static final int HEIGHT = 1080;
    private static final int FPS = 30;
    /** Silence between two scenes, in seconds. */
    private static final double SCENE_GAP = 0.6;

    private final DocShell doc;
    private final Path dir;
    private final Map<String, Double> durations = new LinkedHashMap<>();
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final java.awt.Robot robot;
    private Process ffmpeg;
    private volatile long startNanos;
    private long sceneStartNanos;
    private double sceneDuration;
    private double pointerX = WIDTH / 2.0;
    private double pointerY = HEIGHT / 2.0;

    DocVideoRecorder(DocShell doc) throws Exception {
        this.doc = doc;
        this.dir = Path.of(System.getProperty("fxt.video.dir", "build/videos/scratch"));
        this.robot = new java.awt.Robot();
        Path timing = dir.resolve("timing.json");
        if (Files.exists(timing)) {
            JsonObject scenes = JsonParser.parseString(Files.readString(timing)).getAsJsonObject()
                    .getAsJsonObject("scenes");
            for (String id : scenes.keySet()) {
                durations.put(id, scenes.getAsJsonObject(id).get("duration").getAsDouble());
            }
        } else {
            System.out.println("[video] no " + timing + " - scenes get 3 s each (run scripts/make-video.py)");
        }
    }

    /** Starts the screen recording; returns once frames are being written. */
    void start() throws Exception {
        Files.createDirectories(dir);
        String display = System.getenv("DISPLAY");
        if (display == null) {
            throw new IllegalStateException("No DISPLAY - run under xvfb-run -s \"-screen 0 1920x1080x24\"");
        }
        robot.mouseMove((int) pointerX, (int) pointerY);
        ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error",
                "-f", "x11grab", "-framerate", String.valueOf(FPS), "-video_size", WIDTH + "x" + HEIGHT,
                "-draw_mouse", "1", "-i", display,
                "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency", "-crf", "12",
                "-pix_fmt", "yuv420p", "-progress", "pipe:1", "-stats_period", "0.1",
                dir.resolve("screen.mkv").toString());
        builder.redirectError(dir.resolve("ffmpeg.log").toFile());
        ffmpeg = builder.start();
        Thread reader = new Thread(this::readProgress, "video-ffmpeg-progress");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (startNanos == 0) {
            if (!ffmpeg.isAlive() || System.nanoTime() > deadline) {
                throw new IOException("ffmpeg did not start recording, see " + dir.resolve("ffmpeg.log"));
            }
            Thread.sleep(20);
        }
    }

    /** The first progress report with frames tells when frame 0 was taken. */
    private void readProgress() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(ffmpeg.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (startNanos == 0 && line.startsWith("frame=")) {
                    int frames = Integer.parseInt(line.substring(6).trim());
                    if (frames > 0) {
                        startNanos = System.nanoTime() - (long) (frames * 1e9 / FPS);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("[video] progress reader: " + e);
        }
    }

    /** Stops the recording and writes {@code events.json}. */
    void stop() throws Exception {
        if (ffmpeg != null) {
            ffmpeg.getOutputStream().write('q');
            ffmpeg.getOutputStream().flush();
            if (!ffmpeg.waitFor(15, TimeUnit.SECONDS)) {
                ffmpeg.destroy();
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fps", FPS);
        out.put("width", WIDTH);
        out.put("height", HEIGHT);
        out.put("events", events);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(dir.resolve("events.json"), gson.toJson(out));
        System.out.println("[video] wrote " + new File(dir.toFile(), "screen.mkv").getAbsolutePath());
    }

    /**
     * Plays one scene: its narration starts now, {@code body} acts, and the scene ends when both the
     * actions and the narration are done. A failing scene fails the recording.
     */
    void scene(String id, DocShell.SceneBody body) throws Exception {
        Double duration = durations.get(id);
        if (duration == null && !durations.isEmpty()) {
            throw new IllegalStateException("Scene '" + id + "' is not in the storyboard");
        }
        sceneDuration = duration == null ? 3 : duration;
        sceneStartNanos = System.nanoTime();
        event("scene-start").put("id", id);
        body.run();
        spoken(1);
        pause((long) (SCENE_GAP * 1000));
        event("scene-end").put("id", id);
        System.out.println("[video] scene done: " + id);
    }

    /** Waits until {@code fraction} (0..1) of the scene's narration has been spoken. */
    void spoken(double fraction) {
        long until = sceneStartNanos + (long) (sceneDuration * fraction * 1e9);
        long wait = (until - System.nanoTime()) / 1_000_000;
        if (wait > 0) {
            pause(wait);
        }
    }

    void pause(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
    }

    // ----- pointer -----

    /** Glides the pointer to a screen position (eased, 60 steps per second). */
    void moveTo(double x, double y) {
        double distance = Math.hypot(x - pointerX, y - pointerY);
        int steps = (int) Math.max(12, Math.min(60, distance / 14));
        double fromX = pointerX;
        double fromY = pointerY;
        for (int i = 1; i <= steps; i++) {
            double u = i / (double) steps;
            double eased = u * u * (3 - 2 * u);
            robot.mouseMove((int) Math.round(fromX + (x - fromX) * eased),
                    (int) Math.round(fromY + (y - fromY) * eased));
            pause(16);
        }
        pointerX = x;
        pointerY = y;
    }

    /** Glides the pointer to the middle of {@code node}. */
    void moveTo(Node node) {
        Bounds b = screenBounds(node);
        moveTo(b.getCenterX(), b.getCenterY());
    }

    /** Glides to a position and clicks there (a real mouse click). */
    void click(double x, double y) {
        moveTo(x, y);
        pause(250);
        event("click").putAll(Map.of("x", x, "y", y));
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        pause(60);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Glides to a position and double-clicks there (both clicks within the system's double-click time). */
    void doubleClick(double x, double y) {
        moveTo(x, y);
        pause(250);
        event("click").putAll(Map.of("x", x, "y", y));
        for (int i = 0; i < 2; i++) {
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(30);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
        }
        WaitForAsyncUtils.waitForFxEvents();
        pause(300);
    }

    void click(Node node) {
        Bounds b = screenBounds(node);
        click(b.getCenterX(), b.getCenterY());
    }

    /** Glides to a position and right-clicks there (opens a context menu). */
    void rightClick(double x, double y) {
        moveTo(x, y);
        pause(250);
        event("click").putAll(Map.of("x", x, "y", y));
        robot.mousePress(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
        pause(60);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * The scene keys go to: the newest open dialog, else the main window. Without a window manager
     * no window reports the focus, so it is not asked.
     */
    Scene activeScene() {
        return doc.fx(() -> {
            Scene scene = doc.stage().getScene();
            for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                if (window instanceof javafx.stage.Stage stage && stage != doc.stage() && stage.isShowing()
                        && stage.getScene() != null) {
                    scene = stage.getScene();
                }
            }
            return scene;
        });
    }

    // ----- keyboard -----

    /**
     * Presses a key as the focused control receives it, and shows {@code label} as a key cap in the
     * video. The event is fired in the scene (an X key press would need a window manager's focus).
     */
    void key(KeyCode code, String label) {
        event("key").put("label", label);
        Scene scene = activeScene();
        doc.onFx(() -> {
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            String text = code == KeyCode.ENTER ? "\r" : "";
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", text, code, false, false, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
        });
    }

    /**
     * Replaces {@code length} characters at {@code offset} of the active document with {@code text},
     * one key stroke at a time: first the old characters go, then the new ones appear.
     */
    void retype(int offset, int length, String text) {
        for (int i = length; i > 0; i--) {
            int end = offset + i;
            doc.onFx(() -> doc.host().replaceActiveTextRegion(end - 1, end, ""));
            pause(140);
        }
        for (int i = 0; i < text.length(); i++) {
            int at = offset + i;
            String ch = text.substring(i, i + 1);
            doc.onFx(() -> doc.host().replaceActiveTextRegion(at, at, ch));
            pause(150);
        }
    }

    /** Replaces the text of the focused input field with {@code text}, one key stroke at a time. */
    void typeIntoFocused(String text) {
        // without a window manager no window reports the focus: take the scene's focus owner
        Scene scene = activeScene();
        javafx.scene.control.TextInputControl field = doc.fx(() -> {
            Node owner = scene.getFocusOwner();
            if (owner instanceof javafx.scene.control.TextInputControl input) {
                return input;
            }
            if (owner instanceof javafx.scene.control.Spinner<?> spinner) {
                return spinner.getEditor(); // the grid's editor of a number
            }
            throw new IllegalStateException("No input field has the focus: " + owner);
        });
        doc.onFx(field::clear);
        pause(200);
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            doc.onFx(() -> field.appendText(ch));
            pause(150);
        }
        doc.onFx(() -> {
            for (Node n = field; n != null; n = n.getParent()) {
                if (n instanceof javafx.scene.control.Spinner<?> spinner) {
                    spinner.commitValue(); // as the spinner does on Enter in its editor
                    break;
                }
            }
        });
    }

    // ----- zoom -----

    /** Zooms the picture to a node (with some air around it) until {@link #zoomOut}. */
    void zoom(Node node) {
        zoom(screenBounds(node));
    }

    /** Zooms the picture to a region of the screen. */
    void zoom(Bounds region) {
        event("zoom").putAll(Map.of("x", region.getMinX(), "y", region.getMinY(),
                "w", region.getWidth(), "h", region.getHeight()));
    }

    void zoomOut() {
        event("zoom-out");
    }

    /**
     * The item of an open context menu (or its submenu) with the text {@code text}, as a node on
     * screen; waits up to 5 s for it to show.
     */
    Node menuItem(String text) throws Exception {
        Node[] found = new Node[1];
        doc.waitUntilFx(5, () -> {
            for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                if (window instanceof javafx.stage.PopupWindow && window.isShowing() && window.getScene() != null) {
                    for (Node item : window.getScene().getRoot().lookupAll(".menu-item")) {
                        if (item.lookupAll(".label").stream()
                                .anyMatch(l -> l instanceof javafx.scene.control.Label label && text.equals(label.getText()))) {
                            found[0] = item;
                            return true;
                        }
                    }
                }
            }
            return false;
        });
        return found[0];
    }

    /**
     * The node matching {@code selector} in an open popup (a menu item keeps its id there:
     * {@code #menu-project-settings}); waits up to 5 s for it to show.
     */
    Node inPopup(String selector) throws Exception {
        Node[] found = new Node[1];
        doc.waitUntilFx(5, () -> {
            for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                if (window instanceof javafx.stage.PopupWindow && window.isShowing() && window.getScene() != null) {
                    Node node = window.getScene().getRoot().lookup(selector);
                    if (node != null && node.localToScreen(node.getBoundsInLocal()) != null) {
                        found[0] = node;
                        return true;
                    }
                }
            }
            return false;
        });
        return found[0];
    }

    /** The first node matching {@code selector} in the newest open dialog (see {@link #activeScene}). */
    Node inDialog(String selector) {
        Scene scene = activeScene();
        Node node = doc.fx(() -> scene.getRoot().lookup(selector));
        if (node == null) {
            throw new IllegalStateException("Not in the dialog: " + selector);
        }
        return node;
    }

    Bounds screenBounds(Node node) {
        Bounds bounds = doc.fx(() -> node.localToScreen(node.getBoundsInLocal()));
        if (bounds == null) {
            throw new IllegalStateException("Not on screen: " + node);
        }
        return bounds;
    }

    private Map<String, Object> event(String type) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("t", startNanos == 0 ? 0 : (System.nanoTime() - startNanos) / 1e9);
        event.put("type", type);
        events.add(event);
        return event;
    }
}
