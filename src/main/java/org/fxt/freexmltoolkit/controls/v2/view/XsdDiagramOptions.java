package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Display options shared by every open XSD diagram, the schema tabs as well as the type editor's.
 * Changes are stored in the application properties.
 */
public final class XsdDiagramOptions {

    static final String SHOW_CONSTRAINTS_KEY = "xsd.diagram.showConstraints";

    private static final BooleanProperty SHOW_CONSTRAINTS = createShowConstraints();

    private XsdDiagramOptions() {
    }

    /** @return whether cards list their identity constraints and assertions */
    public static BooleanProperty showConstraintsProperty() {
        return SHOW_CONSTRAINTS;
    }

    private static BooleanProperty createShowConstraints() {
        BooleanProperty property = new SimpleBooleanProperty(readShowConstraints());
        property.addListener((obs, old, value) -> {
            try {
                ServiceRegistry.get(PropertiesService.class).set(SHOW_CONSTRAINTS_KEY, Boolean.toString(value));
            } catch (Throwable ignored) {
                // properties service unavailable: the choice holds for this session
            }
        });
        return property;
    }

    private static boolean readShowConstraints() {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(SHOW_CONSTRAINTS_KEY);
            return value == null || value.isBlank() || Boolean.parseBoolean(value.strip());
        } catch (Throwable e) {
            return true;
        }
    }
}
