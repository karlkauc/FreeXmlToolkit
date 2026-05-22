/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.icons;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Loads and resolves <a href="https://iconify.design/">Iconify</a> icon sets bundled
 * as JSON resources under {@code /icons/iconify/}.
 * <p>
 * Icon literals use the Ikonli-compatible {@code prefix-name} form (e.g. {@code "bi-save"}),
 * where the part before the first {@code '-'} is the icon-set prefix and the remainder is the
 * icon name within that set.
 * <p>
 * This service is pure Java (no JavaFX dependency) so it can be exercised by unit tests. It
 * parses each icon's SVG {@code body} into one or more {@link IconPath} entries (one per
 * {@code <path>} element), preserving each path's fill rule. All icon sets are assumed to use a
 * 16x16 viewBox (the Iconify default; Bootstrap Icons comply).
 *
 * @see IconifyIcon
 */
public final class IconifyIconService {

    private static final Logger LOG = LogManager.getLogger(IconifyIconService.class);

    /** The viewBox size assumed for all bundled icon sets. */
    public static final double VIEWBOX_SIZE = 16.0;

    private static final String RESOURCE_DIR = "/icons/iconify/";

    private static final IconifyIconService INSTANCE = new IconifyIconService();

    // Extracts the d="..." attribute of an SVG path/element.
    private static final Pattern D_ATTR = Pattern.compile("\\bd\\s*=\\s*\"([^\"]*)\"");
    // Detects an explicit fill-rule="evenodd" on an element.
    private static final Pattern EVENODD = Pattern.compile("fill-rule\\s*=\\s*\"evenodd\"");
    // Splits a body into individual <path .../> elements.
    private static final Pattern PATH_ELEM = Pattern.compile("<path\\b[^>]*?/?>", Pattern.DOTALL);
    // Basic shapes that some icons use instead of <path> (e.g. bi-circle-fill).
    private static final Pattern CIRCLE_ELEM = Pattern.compile("<circle\\b[^>]*?/?>", Pattern.DOTALL);
    private static final Pattern RECT_ELEM = Pattern.compile("<rect\\b[^>]*?/?>", Pattern.DOTALL);
    private static final Pattern ELLIPSE_ELEM = Pattern.compile("<ellipse\\b[^>]*?/?>", Pattern.DOTALL);

    /** Loaded icon sets keyed by prefix (e.g. "bi"). Lazily populated. */
    private final Map<String, IconSet> sets = new ConcurrentHashMap<>();
    /** Cache of resolved literals (including negative results as empty lists). */
    private final Map<String, List<IconPath>> resolveCache = new ConcurrentHashMap<>();

    private IconifyIconService() {
    }

    public static IconifyIconService getInstance() {
        return INSTANCE;
    }

    /**
     * A single SVG path of an icon.
     *
     * @param data    the SVG path data ({@code d} attribute)
     * @param evenOdd whether the path uses the even-odd fill rule
     */
    public record IconPath(String data, boolean evenOdd) {
    }

    /**
     * Resolves an icon literal into its constituent SVG paths.
     *
     * @param literal the icon literal, e.g. {@code "bi-save"}
     * @return an immutable list of paths (never {@code null}); empty if the literal is unknown
     */
    public List<IconPath> resolve(String literal) {
        if (literal == null || literal.isBlank()) {
            return List.of();
        }
        return resolveCache.computeIfAbsent(literal, this::doResolve);
    }

    /**
     * @param literal the icon literal, e.g. {@code "bi-save"}
     * @return {@code true} if the literal resolves to at least one renderable path
     */
    public boolean exists(String literal) {
        return !resolve(literal).isEmpty();
    }

    private List<IconPath> doResolve(String literal) {
        int dash = literal.indexOf('-');
        if (dash <= 0 || dash == literal.length() - 1) {
            LOG.warn("Icon literal '{}' is not in 'prefix-name' form", literal);
            return List.of();
        }
        String prefix = literal.substring(0, dash);
        String name = literal.substring(dash + 1);
        // Tolerate Ikonli-style "name:size[:color]" suffixes (e.g. "bi-gear:16"); the size/color
        // part is applied by IconifyIcon, here we only need the bare icon name.
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(0, colon);
        }

        IconSet set = sets.computeIfAbsent(prefix, IconifyIconService::loadSet);
        if (set == null) {
            return List.of();
        }

        String body = set.bodyFor(name);
        if (body == null) {
            LOG.warn("Icon '{}' not found in Iconify set '{}'", name, prefix);
            return List.of();
        }
        List<IconPath> paths = parseBody(body);
        if (paths.isEmpty()) {
            LOG.warn("Icon '{}' in set '{}' contains no <path> data (unsupported shape)", name, prefix);
        }
        return paths;
    }

    /**
     * Parses an SVG {@code body} string into individual paths, preserving fill rules.
     * A {@code fill-rule="evenodd"} on a wrapping element applies to all contained paths.
     */
    static List<IconPath> parseBody(String body) {
        boolean groupEvenOdd = false;
        // A fill-rule outside any <path ...> element (e.g. on a wrapping <g> or <svg>) applies to all.
        String outsidePaths = PATH_ELEM.matcher(body).replaceAll(" ");
        if (EVENODD.matcher(outsidePaths).find()) {
            groupEvenOdd = true;
        }

        List<IconPath> result = new ArrayList<>();
        Matcher elem = PATH_ELEM.matcher(body);
        while (elem.find()) {
            String pathElem = elem.group();
            Matcher d = D_ATTR.matcher(pathElem);
            if (d.find()) {
                String data = d.group(1).trim();
                if (!data.isEmpty()) {
                    boolean evenOdd = groupEvenOdd || EVENODD.matcher(pathElem).find();
                    result.add(new IconPath(data, evenOdd));
                }
            }
        }
        // Convert basic shapes (<circle>, <rect>, <ellipse>) to equivalent path data.
        Matcher circle = CIRCLE_ELEM.matcher(body);
        while (circle.find()) {
            String d = circleToPath(circle.group());
            if (d != null) result.add(new IconPath(d, false));
        }
        Matcher ellipse = ELLIPSE_ELEM.matcher(body);
        while (ellipse.find()) {
            String d = ellipseToPath(ellipse.group());
            if (d != null) result.add(new IconPath(d, false));
        }
        Matcher rect = RECT_ELEM.matcher(body);
        while (rect.find()) {
            String d = rectToPath(rect.group());
            if (d != null) result.add(new IconPath(d, false));
        }
        return List.copyOf(result);
    }

    private static final Pattern NUM_ATTR =
            Pattern.compile("\\b%s\\s*=\\s*\"([-+]?[0-9]*\\.?[0-9]+)\"");

    private static Double attr(String elem, String name) {
        Matcher m = Pattern.compile(String.format(NUM_ATTR.pattern(), name)).matcher(elem);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    /** Converts an SVG {@code <circle>} element to a closed path (two semicircle arcs). */
    private static String circleToPath(String elem) {
        Double cx = attr(elem, "cx"), cy = attr(elem, "cy"), r = attr(elem, "r");
        if (cx == null || cy == null || r == null) return null;
        return ellipsePath(cx, cy, r, r);
    }

    /** Converts an SVG {@code <ellipse>} element to a closed path. */
    private static String ellipseToPath(String elem) {
        Double cx = attr(elem, "cx"), cy = attr(elem, "cy"),
                rx = attr(elem, "rx"), ry = attr(elem, "ry");
        if (cx == null || cy == null || rx == null || ry == null) return null;
        return ellipsePath(cx, cy, rx, ry);
    }

    private static String ellipsePath(double cx, double cy, double rx, double ry) {
        return String.format("M %s %s a %s %s 0 1 0 %s 0 a %s %s 0 1 0 %s 0 Z",
                cx - rx, cy, rx, ry, 2 * rx, rx, ry, -2 * rx);
    }

    /** Converts an SVG {@code <rect>} element to a closed path (rounded corners ignored). */
    private static String rectToPath(String elem) {
        Double x = attr(elem, "x"), y = attr(elem, "y"),
                w = attr(elem, "width"), h = attr(elem, "height");
        double xx = x == null ? 0 : x, yy = y == null ? 0 : y;
        if (w == null || h == null) return null;
        return String.format("M %s %s h %s v %s h %s Z", xx, yy, w, h, -w);
    }

    private static IconSet loadSet(String prefix) {
        String resource = RESOURCE_DIR + prefix + ".json";
        try (InputStream in = IconifyIconService.class.getResourceAsStream(resource)) {
            if (in == null) {
                LOG.warn("Iconify set resource not found: {}", resource);
                return null;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject icons = root.has("icons") ? root.getAsJsonObject("icons") : new JsonObject();
            JsonObject aliases = root.has("aliases") ? root.getAsJsonObject("aliases") : new JsonObject();
            LOG.info("Loaded Iconify set '{}' ({} icons, {} aliases)", prefix, icons.size(), aliases.size());
            return new IconSet(icons, aliases);
        } catch (Exception e) {
            LOG.error("Failed to load Iconify set '{}'", prefix, e);
            return null;
        }
    }

    /** A parsed Iconify icon set: icon definitions plus simple aliases. */
    private record IconSet(JsonObject icons, JsonObject aliases) {

        /**
         * Returns the SVG {@code body} for an icon name, following one level of alias indirection.
         * Alias transforms (rotate/flip) are not applied; the parent body is used as-is.
         */
        String bodyFor(String name) {
            if (icons.has(name)) {
                JsonObject icon = icons.getAsJsonObject(name);
                return icon.has("body") ? icon.get("body").getAsString() : null;
            }
            if (aliases.has(name)) {
                JsonObject alias = aliases.getAsJsonObject(name);
                if (alias.has("parent")) {
                    String parent = alias.get("parent").getAsString();
                    if (icons.has(parent)) {
                        JsonObject icon = icons.getAsJsonObject(parent);
                        return icon.has("body") ? icon.get("body").getAsString() : null;
                    }
                }
            }
            return null;
        }
    }
}
