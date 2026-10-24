package org.fxt.freexmltoolkit.debugger.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.fxt.freexmltoolkit.debugger.BreakpointTarget;
import org.fxt.freexmltoolkit.debugger.SourceLocations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The breakpoint gutter toggles on the mouse <em>press</em>: a click handler never fired,
 * because the press moved the caret, the editor re-styled the line and RichTextFX rebuilt
 * the gutter cell before the release.
 */
@ExtendWith(ApplicationExtension.class)
class BreakpointGutterFactoryTest {

    private static final String FILE = "/tmp/gutter/sheet.xsl";

    /** In-memory breakpoint target recording toggles. */
    private static final class RecordingTarget implements BreakpointTarget {
        final Set<String> set = new LinkedHashSet<>();

        @Override
        public boolean hasBreakpoint(String filePath, int line) {
            return set.contains(SourceLocations.normalize(filePath) + ":" + line);
        }

        @Override
        public boolean toggleBreakpoint(String filePath, int line) {
            String key = SourceLocations.normalize(filePath) + ":" + line;
            if (set.remove(key)) {
                return false;
            }
            set.add(key);
            return true;
        }
    }

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        stage.setScene(new Scene(editor, 600, 400));
        stage.show();
    }

    @Test
    void primaryPressTogglesAndIsConsumed() throws Exception {
        RecordingTarget target = new RecordingTarget();
        AtomicInteger refreshed = new AtomicInteger();
        BreakpointGutterFactory factory = new BreakpointGutterFactory(target, v -> refreshed.incrementAndGet());
        factory.setFilePath(FILE);

        Node slot = WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.apply(4));
        assertTrue(slot.getStyleClass().contains(BreakpointGutterFactory.SLOT_STYLE_CLASS));

        // Stand-in for the CodeArea: a parent that would move the caret if the press bubbled up.
        AtomicInteger bubbled = new AtomicInteger();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            javafx.scene.layout.StackPane parent = new javafx.scene.layout.StackPane(slot);
            parent.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> bubbled.incrementAndGet());
            return parent;
        });

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Event.fireEvent(slot, mouse(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            return null;
        });
        assertTrue(target.hasBreakpoint(FILE, 5), "paragraph index 4 is line 5");
        assertEquals(0, bubbled.get(), "the press must not reach the CodeArea (caret would move)");
        assertEquals(1, refreshed.get());

        // A bare click (no press on this node) does nothing — no double toggle.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Event.fireEvent(slot, mouse(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY));
            return null;
        });
        assertTrue(target.hasBreakpoint(FILE, 5));

        // The secondary button opens the gutter's breakpoint menu (conditions) instead of the
        // editor's context menu: it toggles nothing and does not reach the editor.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Event.fireEvent(slot, mouse(MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY));
            return null;
        });
        assertTrue(target.hasBreakpoint(FILE, 5));
        assertEquals(1, refreshed.get());
        assertEquals(0, bubbled.get(), "a secondary press is consumed by the gutter (breakpoint menu)");

        // Pressing again removes it.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Event.fireEvent(slot, mouse(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            return null;
        });
        assertFalse(target.hasBreakpoint(FILE, 5));
        assertEquals(2, refreshed.get());
    }

    @Test
    void realClickInTheEditorGutterSetsABreakpointWithoutMovingTheCaret(FxRobot robot) throws Exception {
        RecordingTarget target = new RecordingTarget();
        BreakpointGutterFactory factory = new BreakpointGutterFactory(target, v -> editor.refreshGutter());
        factory.setFilePath(FILE);
        CodeArea ca = editor.getCodeArea();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.setText("<a>\n  <b/>\n  <c/>\n  <d/>\n</a>\n");
            editor.setExtraGutterFactory(factory);
            ca.moveTo(0);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Node slot = settledSlot(ca, 2);
        assertNotNull(slot.getScene(), "the slot of a visible line is part of the scene");
        robot.clickOn(slot);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(target.hasBreakpoint(FILE, 3), "one click on the gutter of line 3 must set the breakpoint");
        assertEquals(0, ca.getCurrentParagraph(), "the gutter press must not move the caret");
        // After the refresh the slot of line 3 renders the red breakpoint icon.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> slotOf(ca, 2) instanceof javafx.scene.Parent p
                && p.getChildrenUnmodifiable().stream()
                        .anyMatch(n -> n instanceof org.fxt.freexmltoolkit.controls.icons.IconifyIcon));
    }

    /**
     * Waits until the paragraph's gutter stops being rebuilt (the asynchronous syntax highlighting
     * replaces the paragraphs — and their cells — shortly after {@code setText}) and returns the
     * slot that is then live in the scene.
     */
    private static Node settledSlot(CodeArea ca, int paragraph) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        Node previous = null;
        while (System.currentTimeMillis() < deadline) {
            Node current = slotOf(ca, paragraph);
            if (current != null && current == previous) {
                return current;
            }
            previous = current;
            Thread.sleep(300);
        }
        throw new AssertionError("gutter of paragraph " + paragraph + " never settled");
    }

    /** The live (in-scene) breakpoint slot of a paragraph, queried on the FX thread; {@code null} if not rendered. */
    private static Node slotOf(CodeArea ca, int paragraph) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Node graphic = ca.getParagraphGraphic(paragraph);
            Node slot = graphic == null ? null : graphic.lookup("." + BreakpointGutterFactory.SLOT_STYLE_CLASS);
            return slot != null && slot.getScene() != null ? slot : null;
        });
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, MouseButton button) {
        return new MouseEvent(type, 5, 5, 5, 5, button, 1,
                false, false, false, false,
                button == MouseButton.PRIMARY, false, button == MouseButton.SECONDARY,
                false, false, false, null);
    }

    @Test
    void conditionAndHitRuleCanBeSetFromTheGutterAndAreMarked() throws Exception {
        org.fxt.freexmltoolkit.debugger.DebugSession session = new org.fxt.freexmltoolkit.debugger.DebugSession();
        AtomicInteger refreshed = new AtomicInteger();
        BreakpointGutterFactory factory = new BreakpointGutterFactory(session, v -> refreshed.incrementAndGet());
        factory.setFilePath(FILE);
        java.util.concurrent.atomic.AtomicReference<String> asked = new java.util.concurrent.atomic.AtomicReference<>();
        org.fxt.freexmltoolkit.debugger.HitCondition third =
                new org.fxt.freexmltoolkit.debugger.HitCondition(org.fxt.freexmltoolkit.debugger.HitCondition.Mode.EQUAL, 3);
        factory.setPropertiesPrompt((owner, location, current, hits) -> {
            asked.set(location + "|" + current.condition() + "|" + current.hit().format() + "|" + hits);
            return java.util.Optional.of(new BreakpointPropertiesDialog.Settings("@n = 2", third));
        });

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.editBreakpoint(5, null)));
        assertEquals("sheet.xsl, line 5|||0", asked.get(), "asked for line 5 without rules and without hits");
        assertEquals("@n = 2", session.conditionAt(FILE, 5), "the line gets a conditional breakpoint");
        assertEquals(third, session.breakpointAt(FILE, 5).hit(), "with the hit rule");
        assertEquals(1, refreshed.get(), "the gutter is re-rendered");
        Node slot = WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.apply(4));
        assertTrue(slot.getStyleClass().contains(BreakpointGutterFactory.CONDITIONAL_STYLE_CLASS),
                "a breakpoint with rules has its own marker");

        java.util.List<String> labels = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> factory.menuFor(5).getItems().stream()
                        .map(javafx.scene.control.MenuItem::getText).filter(java.util.Objects::nonNull).toList());
        assertEquals(java.util.List.of("Breakpoint Properties…", "Remove Condition", "Remove Hit Count Rule",
                "Disable Breakpoint", "Remove Breakpoint"), labels);
        java.util.List<String> empty = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> factory.menuFor(9).getItems().stream().map(javafx.scene.control.MenuItem::getText).toList());
        assertEquals(java.util.List.of("Add Conditional Breakpoint…"), empty);

        factory.setPropertiesPrompt((owner, location, current, hits) -> java.util.Optional.empty());
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.editBreakpoint(5, null)), "cancel changes nothing");
        assertEquals("@n = 2", session.conditionAt(FILE, 5));
    }

    @Test
    void disabledAndMutedBreakpointsShowAnInactiveMarker() throws Exception {
        org.fxt.freexmltoolkit.debugger.DebugSession session = new org.fxt.freexmltoolkit.debugger.DebugSession();
        AtomicInteger refreshed = new AtomicInteger();
        BreakpointGutterFactory factory = new BreakpointGutterFactory(session, v -> refreshed.incrementAndGet());
        factory.setFilePath(FILE);
        session.toggleBreakpoint(FILE, 2);
        session.toggleBreakpoint(FILE, 3);
        session.setEnabled(FILE, 3, false);

        Node active = WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.apply(1));
        Node disabled = WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.apply(2));
        assertFalse(active.getStyleClass().contains(BreakpointGutterFactory.INACTIVE_STYLE_CLASS));
        assertTrue(disabled.getStyleClass().contains(BreakpointGutterFactory.INACTIVE_STYLE_CLASS),
                "a disabled breakpoint stays visible, hollow");

        java.util.List<String> labels = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> factory.menuFor(3).getItems().stream().map(javafx.scene.control.MenuItem::getText)
                        .filter(java.util.Objects::nonNull).toList());
        assertTrue(labels.contains("Enable Breakpoint"), labels.toString());
        javafx.scene.control.MenuItem enable = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> factory.menuFor(3).getItems().stream().filter(i -> "Enable Breakpoint".equals(i.getText()))
                        .findFirst().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> enable.fire());
        assertTrue(session.hasBreakpoint(FILE, 3), "re-enabled from the gutter menu");

        session.setMuted(true);
        Node muted = WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.apply(1));
        assertTrue(muted.getStyleClass().contains(BreakpointGutterFactory.INACTIVE_STYLE_CLASS),
                "while muted every breakpoint is shown inactive");
    }

    @Test
    void runToHereIsOfferedOnlyWhileASessionRuns() {
        org.fxt.freexmltoolkit.debugger.DebugSession session = new org.fxt.freexmltoolkit.debugger.DebugSession();
        BreakpointGutterFactory factory = new BreakpointGutterFactory(session, v -> { });
        factory.setFilePath(FILE);
        java.util.function.Supplier<java.util.List<String>> labels = () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> factory.menuFor(7).getItems().stream().map(javafx.scene.control.MenuItem::getText)
                        .filter(java.util.Objects::nonNull).toList());
        assertFalse(labels.get().contains("Run to Here"), "no session running");
        session.startSession();
        assertEquals("Run to Here", labels.get().get(0));
        javafx.scene.control.MenuItem runTo = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> factory.menuFor(7).getItems().get(0));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> runTo.fire());
        assertEquals(7, session.getRunToLine());
    }

    @Test
    void breakpointsTheLastRunNeverReachedAreMarked() {
        org.fxt.freexmltoolkit.debugger.DebugSession session = new org.fxt.freexmltoolkit.debugger.DebugSession();
        BreakpointGutterFactory factory = new BreakpointGutterFactory(session, v -> { });
        factory.setFilePath(FILE);
        session.toggleBreakpoint(FILE, 4);
        session.startSession();
        Node during = WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.apply(3));
        assertFalse(during.getStyleClass().contains(BreakpointGutterFactory.UNREACHED_STYLE_CLASS), "not while running");
        session.markRunEnded(true);
        Node after = WaitForAsyncUtils.waitForAsyncFx(2000, () -> factory.apply(3));
        assertTrue(after.getStyleClass().contains(BreakpointGutterFactory.UNREACHED_STYLE_CLASS));
    }
}
