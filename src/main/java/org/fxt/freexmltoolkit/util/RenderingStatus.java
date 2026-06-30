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
 */

package org.fxt.freexmltoolkit.util;

import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reports the JavaFX Prism rendering pipeline that is <em>actually active</em> in the
 * running application — as opposed to {@link RenderingPipelineDetector}, which only
 * recommends a {@code prism.order} before the toolkit starts.
 *
 * <p>The active pipeline can differ from the requested {@code prism.order} (e.g. when
 * hardware rendering was requested but the GPU pipeline failed to initialize and Prism
 * silently fell back to software). This class reads the truth from
 * {@code com.sun.prism.GraphicsPipeline.getPipeline()} via reflection, so it has no
 * compile-time dependency on JavaFX internals and never throws.
 *
 * <p>Reflection requires the package to be exported at runtime
 * ({@code --add-exports javafx.graphics/com.sun.prism=ALL-UNNAMED}); when it is not, the
 * methods degrade gracefully to {@code null}/{@code false}. Must be called after the
 * JavaFX toolkit has started (i.e. a Stage has been shown).
 */
public final class RenderingStatus {

    private static final Logger logger = LogManager.getLogger(RenderingStatus.class);

    private RenderingStatus() {
        // utility class
    }

    /**
     * Returns a human-readable description of the active Prism pipeline, e.g.
     * {@code "Hardware — Direct3D"}, {@code "Hardware — OpenGL ES2"}, or {@code "Software"}.
     *
     * @return the description, or {@code null} if the pipeline cannot be determined
     *         (toolkit not started, or {@code com.sun.prism} not accessible)
     */
    public static String activePipelineDescription() {
        String className = activePipelineClassName();
        if (className == null) {
            return null;
        }
        String lower = className.toLowerCase(Locale.ROOT);
        if (lower.contains(".d3d.") || lower.contains("d3dpipeline")) {
            return "Hardware — Direct3D";
        }
        if (lower.contains(".es2.") || lower.contains("es2pipeline")) {
            return "Hardware — OpenGL ES2";
        }
        if (lower.contains(".mtl.") || lower.contains("mtlpipeline") || lower.contains("metal")) {
            return "Hardware — Metal";
        }
        if (lower.contains(".sw.") || lower.contains("swpipeline")) {
            return "Software";
        }
        if (lower.contains(".j2d.") || lower.contains("j2dpipeline")) {
            return "Software — Java2D";
        }
        return className;
    }

    /**
     * @return {@code true} if the active pipeline is GPU-accelerated (Direct3D/ES2/Metal),
     *         {@code false} if it is software or cannot be determined
     */
    public static boolean isHardwareActive() {
        String desc = activePipelineDescription();
        return desc != null && desc.startsWith("Hardware");
    }

    /**
     * @return the simple class name of the active Prism pipeline (e.g. {@code "ES2Pipeline"}),
     *         or {@code null} if it cannot be read
     */
    private static String activePipelineClassName() {
        try {
            Class<?> graphicsPipeline = Class.forName("com.sun.prism.GraphicsPipeline");
            Object pipeline = graphicsPipeline.getMethod("getPipeline").invoke(null);
            if (pipeline == null) {
                return null;
            }
            return pipeline.getClass().getName();
        } catch (Throwable t) {
            // com.sun.prism not exported, or toolkit not yet initialized.
            logger.debug("Could not read active Prism pipeline: {}", t.toString());
            return null;
        }
    }
}
