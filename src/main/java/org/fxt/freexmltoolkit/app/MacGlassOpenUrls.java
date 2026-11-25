package org.fxt.freexmltoolkit.app;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Delivers files macOS asks the application to open (Finder double-click, "Open With", a drop on
 * the Dock icon, {@code open file.xml}) on JavaFX versions whose Glass toolkit answers
 * {@code -[NSApplicationDelegate application:openURLs:]}.
 *
 * <p>AppKit prefers {@code application:openURLs:} over {@code application:openFiles:}. Glass
 * (JavaFX 25) implements it only to forward the URLs to an embedded AWT as an
 * {@code AWTEmbeddedEvent} notification; it never calls {@code notifyOpenFiles}, so
 * {@link MacOpenFilesHandler} gets nothing. This class replaces Glass's method through the
 * Objective-C runtime: file URLs go to {@code onFiles}, any other URL still goes to Glass's
 * implementation. On a JavaFX without that method nothing is changed (Glass's
 * {@code application:openFiles:} then reaches {@link MacOpenFilesHandler}).
 *
 * <p>Must run before the JavaFX toolkit starts: on a cold start AppKit dispatches the open event
 * as soon as Glass enters {@code [NSApp run]}, before {@code Application.init()}. That is why the
 * Glass library is loaded here by path ({@code dlopen}; it has no load-time initialisers, the JVM
 * later loads the same image). Never throws: without it, opened files are just not loaded.
 */
public final class MacGlassOpenUrls {

    private static final Logger logger = LogManager.getLogger(MacGlassOpenUrls.class);

    private static final String GLASS_DELEGATE_CLASS = "GlassApplication";
    private static final String OPEN_URLS_SELECTOR = "application:openURLs:";
    /** Objective-C type encoding of {@code - (void)application:(NSApplication *)app openURLs:(NSArray *)urls}. */
    private static final String OPEN_URLS_TYPES = "v@:@@";

    /** The replacement IMP; referenced statically so JNA's native trampoline is never collected. */
    private static volatile OpenUrlsImp installed;

    private MacGlassOpenUrls() {
    }

    /** Native signature of the replaced method: {@code (self, _cmd, application, urls)}. */
    public interface OpenUrlsImp extends Callback {
        void invoke(Pointer self, Pointer cmd, Pointer application, Pointer urls);
    }

    /** One URL macOS handed over: {@code path} is set for a file URL, {@code null} for any other URL. */
    record OpenedUrl(String path) {
        boolean isFile() {
            return path != null && !path.isBlank();
        }
    }

    /** Files to open and whether other (non-file) URLs remain for Glass. */
    record Split(List<Path> files, boolean hasOtherUrls) {
    }

    /**
     * Separates file URLs from other URLs, skipping unparseable paths. Pure.
     */
    static Split split(List<OpenedUrl> urls) {
        List<Path> files = new ArrayList<>();
        boolean others = false;
        for (OpenedUrl url : urls) {
            if (!url.isFile()) {
                others = true;
                continue;
            }
            try {
                files.add(Path.of(url.path()));
            } catch (InvalidPathException e) {
                logger.debug("Ignoring unparseable open-URL path: {}", url.path());
            }
        }
        return new Split(files, others);
    }

    /** @return whether this JVM runs on macOS. */
    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    /**
     * Installs the replacement on macOS (once). Call from {@code main} before the JavaFX toolkit starts.
     *
     * @param onFiles receives the files macOS asked to open (on the AppKit main thread)
     * @return {@code true} if Glass's method was replaced (now or by an earlier call)
     */
    public static synchronized boolean install(Consumer<List<Path>> onFiles) {
        if (!isMac()) {
            return false;
        }
        if (installed != null) {
            return true;
        }
        try {
            ObjC objc = new ObjC();
            loadGlass();
            Pointer cls = objc.cls(GLASS_DELEGATE_CLASS);
            if (cls == null) {
                logger.warn("macOS open-URLs bridge not installed: Glass class {} not found", GLASS_DELEGATE_CLASS);
                return false;
            }
            Pointer sel = objc.sel(OPEN_URLS_SELECTOR);
            if (objc.instanceMethod(cls, sel) == null) {
                logger.info("Glass has no {}; open-file events take the application:openFiles: path", OPEN_URLS_SELECTOR);
                return false;
            }
            OpenUrlsImp[] self = new OpenUrlsImp[1];
            Pointer[] original = new Pointer[1];
            self[0] = (receiver, cmd, application, urls) -> handle(objc, onFiles, original[0], receiver, cmd, application, urls);
            installed = self[0];
            original[0] = objc.replaceMethod(cls, sel, self[0], OPEN_URLS_TYPES);
            logger.info("macOS open-URLs bridge installed");
            return true;
        } catch (Throwable t) {
            installed = null;
            logger.warn("Could not install the macOS open-URLs bridge: {}", t.toString());
            return false;
        }
    }

    private static void handle(ObjC objc, Consumer<List<Path>> onFiles, Pointer original,
                               Pointer receiver, Pointer cmd, Pointer application, Pointer urls) {
        try {
            Split split = split(objc.urls(urls));
            if (!split.files().isEmpty()) {
                logger.info("macOS asked to open {} file(s)", split.files().size());
                onFiles.accept(split.files());
            }
            if (split.hasOtherUrls() && original != null) {
                Function.getFunction(original).invoke(Void.class, new Object[]{receiver, cmd, application, urls});
            }
        } catch (Throwable t) {
            // never let an exception unwind into AppKit
            logger.warn("Could not handle the files macOS asked to open: {}", t.toString());
        }
    }

    /** Loads Glass's native library so its Objective-C classes are registered before the toolkit starts. */
    private static void loadGlass() {
        Path glass = Path.of(System.getProperty("java.home"), "lib", "libglass.dylib");
        if (java.nio.file.Files.isRegularFile(glass)) {
            NativeLibrary.getInstance(glass.toString());
        }
    }

    /** The few Objective-C runtime calls this class needs. */
    private static final class ObjC {
        private final Function getClass;
        private final Function registerSelector;
        private final Function getInstanceMethod;
        private final Function replaceMethod;
        private final Function msgSend;

        ObjC() {
            NativeLibrary lib = NativeLibrary.getInstance("objc");
            getClass = lib.getFunction("objc_getClass");
            registerSelector = lib.getFunction("sel_registerName");
            getInstanceMethod = lib.getFunction("class_getInstanceMethod");
            replaceMethod = lib.getFunction("class_replaceMethod");
            msgSend = lib.getFunction("objc_msgSend");
        }

        Pointer cls(String name) {
            return (Pointer) getClass.invoke(Pointer.class, new Object[]{name});
        }

        Pointer sel(String name) {
            return (Pointer) registerSelector.invoke(Pointer.class, new Object[]{name});
        }

        Pointer instanceMethod(Pointer cls, Pointer sel) {
            return (Pointer) getInstanceMethod.invoke(Pointer.class, new Object[]{cls, sel});
        }

        /** @return the previous implementation */
        Pointer replaceMethod(Pointer cls, Pointer sel, Callback imp, String types) {
            return (Pointer) replaceMethod.invoke(Pointer.class, new Object[]{cls, sel, imp, types});
        }

        Pointer send(Pointer receiver, String selector, Object... args) {
            Object[] all = new Object[args.length + 2];
            all[0] = receiver;
            all[1] = sel(selector);
            System.arraycopy(args, 0, all, 2, args.length);
            return (Pointer) msgSend.invoke(Pointer.class, all);
        }

        long sendLong(Pointer receiver, String selector) {
            return msgSend.invokeLong(new Object[]{receiver, sel(selector)});
        }

        /** Reads an {@code NSArray<NSURL *>}: the file-system path of each file URL, {@code null} for others. */
        List<OpenedUrl> urls(Pointer array) {
            List<OpenedUrl> result = new ArrayList<>();
            if (array == null) {
                return result;
            }
            long count = sendLong(array, "count");
            for (long i = 0; i < count; i++) {
                Pointer url = send(array, "objectAtIndex:", i);
                // filePathURL turns a file reference URL (file:///.file/id=…) into a path URL; nil for non-file URLs
                Pointer pathUrl = url == null ? null : send(url, "filePathURL");
                Pointer path = pathUrl == null ? null : send(pathUrl, "path");
                result.add(new OpenedUrl(path == null ? null : string(path)));
            }
            return result;
        }

        private String string(Pointer nsString) {
            Pointer utf8 = send(nsString, "UTF8String");
            return utf8 == null ? null : utf8.getString(0, "UTF-8");
        }
    }
}
