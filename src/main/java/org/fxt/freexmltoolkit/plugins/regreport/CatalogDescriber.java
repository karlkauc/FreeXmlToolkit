/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.plugin.ElementDescriber;

/**
 * Describes the elements of a report with its field catalogues: the element's path from the root is
 * looked up in each catalogue (Inspector DOCUMENTATION, grid tooltip). Catalogues load lazily.
 */
public final class CatalogDescriber implements ElementDescriber {

    private final BooleanSupplier enabled;
    private final List<Supplier<FieldCatalog>> catalogs;

    /**
     * @param enabled  whether the extension is on
     * @param catalogs the catalogues to search (each loaded on first use)
     */
    public CatalogDescriber(BooleanSupplier enabled, List<Supplier<FieldCatalog>> catalogs) {
        this.enabled = enabled;
        this.catalogs = catalogs;
    }

    @Override
    public String describe(ElementInfo element) {
        if (!enabled.getAsBoolean() || element.path().isEmpty()) {
            return null;
        }
        String path = String.join("/", element.path());
        for (Supplier<FieldCatalog> catalog : catalogs) {
            FieldCatalog.Field field = catalog.get().byPath(path);
            if (field != null) {
                return FieldCatalog.describe(field);
            }
        }
        return null;
    }

    /** @return a supplier that loads a catalogue resource once */
    public static Supplier<FieldCatalog> lazy(String resource) {
        return new Supplier<>() {
            private volatile FieldCatalog catalog;

            @Override
            public FieldCatalog get() {
                FieldCatalog loaded = catalog;
                if (loaded == null) {
                    synchronized (this) {
                        if (catalog == null) {
                            catalog = FieldCatalog.load(resource);
                        }
                        loaded = catalog;
                    }
                }
                return loaded;
            }
        };
    }
}
