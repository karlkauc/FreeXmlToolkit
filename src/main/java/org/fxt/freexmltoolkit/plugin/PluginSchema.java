package org.fxt.freexmltoolkit.plugin;

import java.util.Objects;

/**
 * An XML Schema a plugin adds to the Schema Library (source "Plugin"): documents in its namespace
 * (or, without one, with its root element) get it bound automatically; a remote location is
 * downloaded once and cached.
 *
 * @param namespace   the target namespace ({@code ""} for none)
 * @param location    a URL or file path
 * @param description shown in the Schema Library
 * @param rootElement for schemas without a namespace: the root element it applies to, else {@code null}
 */
public record PluginSchema(String namespace, String location, String description, String rootElement) {

    public PluginSchema {
        Objects.requireNonNull(location, "location");
        namespace = namespace == null ? "" : namespace;
    }
}
