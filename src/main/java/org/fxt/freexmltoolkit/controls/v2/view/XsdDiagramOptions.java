package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Display options shared by every open XSD diagram, the schema tabs as well as the type editor's.
 * Changes are stored in the application properties and reach open diagrams at once.
 */
public final class XsdDiagramOptions {

    static final String SHOW_CONSTRAINTS_KEY = "xsd.diagram.showConstraints";
    static final String SHOW_CONSTRAINT_TARGETS_KEY = "xsd.diagram.showConstraintTargets";

    private static final BooleanProperty SHOW_CONSTRAINTS = create(SHOW_CONSTRAINTS_KEY, true);
    private static final BooleanProperty SHOW_CONSTRAINT_TARGETS = create(SHOW_CONSTRAINT_TARGETS_KEY, false);

    private XsdDiagramOptions() {
    }

    /** @return whether cards list their identity constraints and assertions */
    public static BooleanProperty showConstraintsProperty() {
        return SHOW_CONSTRAINTS;
    }

    /**
     * @return whether the card of a constraint's field target (e.g. {@code BenchmarkID}) also lists
     *         the identity constraint an ancestor declares for it; off by default. Only shown while
     *         {@link #showConstraintsProperty()} is on.
     */
    public static BooleanProperty showConstraintTargetsProperty() {
        return SHOW_CONSTRAINT_TARGETS;
    }

    private static BooleanProperty create(String key, boolean fallback) {
        BooleanProperty property = new SimpleBooleanProperty(read(key, fallback));
        property.addListener((obs, old, value) -> {
            try {
                ServiceRegistry.get(PropertiesService.class).set(key, Boolean.toString(value));
            } catch (Throwable ignored) {
                // properties service unavailable: the choice holds for this session
            }
        });
        return property;
    }

    private static boolean read(String key, boolean fallback) {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(key);
            return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.strip());
        } catch (Throwable e) {
            return fallback;
        }
    }
}
