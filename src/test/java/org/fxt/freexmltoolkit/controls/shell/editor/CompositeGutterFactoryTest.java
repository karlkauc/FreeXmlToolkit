package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;

import org.junit.jupiter.api.Test;

class CompositeGutterFactoryTest {

    @Test
    void startsEmptyAndCollapsible() {
        CompositeGutterFactory composite = new CompositeGutterFactory();
        assertTrue(composite.isEmpty());
    }

    @Test
    void singleContributorPassesItsNodeThrough() {
        CompositeGutterFactory composite = new CompositeGutterFactory();
        Rectangle marker = new Rectangle();
        composite.set("debugger", line -> line == 3 ? marker : null);

        assertFalse(composite.isEmpty());
        assertSame(marker, composite.apply(3));
        assertNull(composite.apply(1));
    }

    @Test
    void twoContributorsComposeIntoABox() {
        CompositeGutterFactory composite = new CompositeGutterFactory();
        Rectangle breakpoint = new Rectangle();
        Rectangle lightbulb = new Rectangle();
        composite.set("debugger", line -> breakpoint);
        composite.set("quickfix", line -> line == 5 ? lightbulb : null);

        Node both = composite.apply(5);
        assertTrue(both instanceof HBox hbox && hbox.getChildren().size() == 2,
                "both contributions must render side by side");
        Node onlyBreakpoint = composite.apply(1);
        assertTrue(onlyBreakpoint instanceof HBox hbox && hbox.getChildren().size() == 1);
    }

    @Test
    void removingAContributorRestoresTheOther() {
        CompositeGutterFactory composite = new CompositeGutterFactory();
        Rectangle breakpoint = new Rectangle();
        composite.set("debugger", line -> breakpoint);
        composite.set("quickfix", line -> new Rectangle());
        composite.set("quickfix", null);

        assertFalse(composite.isEmpty());
        assertSame(breakpoint, composite.apply(7), "single remaining contributor passes through");
        composite.set("debugger", null);
        assertTrue(composite.isEmpty());
    }

    @Test
    void allNullContributionsYieldNull() {
        CompositeGutterFactory composite = new CompositeGutterFactory();
        composite.set("a", line -> null);
        composite.set("b", line -> null);
        assertNull(composite.apply(1), "no contribution → no gutter node");
    }
}
