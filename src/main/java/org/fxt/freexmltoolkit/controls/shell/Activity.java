package org.fxt.freexmltoolkit.controls.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginManager;

/**
 * The activities of the Unified shell, shown as icons in the left Activity Bar (VS-Code style): the
 * built-in ones (constants, in Activity Bar order, after the Figma mockups) and those contributed by
 * plugins ({@link PluginActivity}), placed before {@link #SETTINGS}.
 * <p>
 * Each activity carries a stable {@link #id()} (used for persistence and {@link #fromId(String)}
 * lookup), a human-readable {@link #label()}, and a Bootstrap {@link #icon()} literal resolvable by
 * {@code IconifyIcon}. There is one instance per id, so activities compare with {@code ==}.
 * <p>
 * Help is not an activity: it is the Help menu of the menu bar, also opened by the header's Help
 * button and F1 (UX plan 4.4).
 */
public final class Activity {

    public static final Activity EXPLORER = new Activity("explorer", "Explorer", "bi-folder2-open", Workflow.WORKSPACE, null);
    public static final Activity SEARCH = new Activity("search", "Search", "bi-search", Workflow.SEARCH, null);
    public static final Activity FAVORITES = new Activity("favorites", "Favorites", "bi-star", Workflow.FAVORITES, null);
    public static final Activity VALIDATION = new Activity("validation", "Validation", "bi-check2-circle", Workflow.VALIDATION, null);
    public static final Activity TRANSFORM = new Activity("transform", "Transform", "bi-arrow-repeat", Workflow.TRANSFORM, null);
    public static final Activity SCHEMA = new Activity("schema", "Schema", "bi-diagram-3", Workflow.SCHEMA, null);
    public static final Activity SCHEMA_LIBRARY = new Activity("schema-library", "Schema Library", "bi-collection", Workflow.SCHEMA_LIBRARY, null);
    public static final Activity PDF_FOP = new Activity("pdf", "PDF / FOP", "bi-file-earmark-pdf", Workflow.PDF, null);
    public static final Activity SIGNATURE = new Activity("signature", "Signature", "bi-shield-lock", Workflow.SIGNATURE, null);
    public static final Activity SETTINGS = new Activity("settings", "Settings", "bi-gear", Workflow.NEUTRAL, null);

    private static final List<Activity> BUILT_IN = List.of(EXPLORER, SEARCH, FAVORITES, VALIDATION, TRANSFORM,
            SCHEMA, SCHEMA_LIBRARY, PDF_FOP, SIGNATURE, SETTINGS);
    /** Plugin activities by id: one instance each. */
    private static final Map<String, Activity> PLUGIN_ACTIVITIES = new ConcurrentHashMap<>();

    private final String id;
    private final String label;
    private final String icon;
    private final Workflow workflow;
    private final PluginActivity plugin;

    private Activity(String id, String label, String icon, Workflow workflow, PluginActivity plugin) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.workflow = workflow;
        this.plugin = plugin;
    }

    /**
     * @return all activities in Activity Bar order: the built-in ones, then the plugins' (in load
     *         order; one whose id is taken is left out), {@link #SETTINGS} last
     */
    public static Activity[] values() {
        List<Activity> all = new ArrayList<>(BUILT_IN.subList(0, BUILT_IN.size() - 1));
        for (PluginActivity contributed : PluginManager.getInstance().activities()) {
            if (BUILT_IN.stream().anyMatch(a -> a.id.equals(contributed.id()))) {
                continue;
            }
            Activity activity = PLUGIN_ACTIVITIES.computeIfAbsent(contributed.id(), id -> new Activity(id,
                    contributed.label(), contributed.icon(), contributed.workflow(), contributed));
            if (!all.contains(activity)) {
                all.add(activity);
            }
        }
        all.add(SETTINGS);
        return all.toArray(Activity[]::new);
    }

    /** @return the workflow colour family this activity belongs to (spec 2026-09-26 §1.1). */
    public Workflow workflow() {
        return workflow;
    }

    /** @return the stable identifier (used for persistence and lookup). */
    public String id() {
        return id;
    }

    /** @return the human-readable label shown in tooltips/headers. */
    public String label() {
        return label;
    }

    /** @return the Bootstrap icon literal (e.g. {@code bi-gear}). */
    public String icon() {
        return icon;
    }

    /** @return the plugin's contribution for a plugin activity */
    public Optional<PluginActivity> plugin() {
        return Optional.ofNullable(plugin);
    }

    /** @return whether the activity is shown now (plugin activities can hide themselves) */
    public boolean isVisible() {
        if (plugin == null) {
            return true;
        }
        try {
            return plugin.visible().getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** @return the activity shown when the shell first opens. */
    public static Activity defaultActivity() {
        return EXPLORER;
    }

    /**
     * Resolves an activity by its {@link #id()}.
     *
     * @param id the id, may be {@code null}
     * @return the matching activity, or {@link Optional#empty()} if none / {@code null}
     */
    public static Optional<Activity> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Activity a : values()) {
            if (a.id.equals(id)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return id;
    }
}
