package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sun.glass.ui.Application;

class MacOpenFilesHandlerTest {

    /** Records which callbacks reached the wrapped handler. */
    private static final class RecordingHandler extends Application.EventHandler {
        final List<String> calls = new ArrayList<>();

        @Override
        public void handleQuitAction(Application app, long time) {
            calls.add("quit");
        }

        @Override
        public void handlePreferencesChanged(Map<String, Object> preferences) {
            calls.add("preferences");
        }

        @Override
        public void handleOpenFilesAction(Application app, long time, String[] files) {
            calls.add("open:" + files.length);
        }
    }

    @Test
    void overridesEveryEventHandlerCallback() throws NoSuchMethodException {
        // A callback added by a future JavaFX version must not silently bypass the delegate.
        for (Method method : Application.EventHandler.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Method override = MacOpenFilesHandler.DelegatingHandler.class
                    .getMethod(method.getName(), method.getParameterTypes());
            assertEquals(MacOpenFilesHandler.DelegatingHandler.class, override.getDeclaringClass(),
                    "not delegated: " + method.getName());
        }
    }

    @Test
    void openFilesAreForwardedToTheDelegateAndTheEditor() {
        RecordingHandler delegate = new RecordingHandler();
        List<Path> opened = new ArrayList<>();
        var handler = new MacOpenFilesHandler.DelegatingHandler(delegate, opened::addAll);

        handler.handleOpenFilesAction(null, 0, new String[]{"/tmp/a.xml", "/tmp/b.xsd"});

        assertEquals(List.of("open:2"), delegate.calls);
        assertEquals(List.of(Path.of("/tmp/a.xml"), Path.of("/tmp/b.xsd")), opened);
    }

    @Test
    void otherCallbacksReachTheDelegate() {
        RecordingHandler delegate = new RecordingHandler();
        var handler = new MacOpenFilesHandler.DelegatingHandler(delegate, files -> { });

        handler.handleQuitAction(null, 0);
        handler.handlePreferencesChanged(Map.of());

        assertEquals(List.of("quit", "preferences"), delegate.calls);
    }

    @Test
    void emptyOrMissingFilesAreIgnored() {
        List<Path> opened = new ArrayList<>();
        var handler = new MacOpenFilesHandler.DelegatingHandler(null, opened::addAll);

        handler.handleOpenFilesAction(null, 0, null);
        handler.handleOpenFilesAction(null, 0, new String[]{"", " "});

        assertTrue(opened.isEmpty());
    }
}
