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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Heuristically decides which JavaFX Prism rendering pipeline order to use on the
 * current machine, based on the installed graphics adapter.
 *
 * <p>On machines with only an <em>integrated</em> GPU, hardware-accelerated rendering
 * (Direct3D / OpenGL ES2) can crash on large scene graphs, which is why such machines
 * have historically been started with {@code -Dprism.order=sw}. Machines with a
 * <em>dedicated</em> GPU run hardware rendering reliably and noticeably faster.
 *
 * <p>This detector queries the OS for the GPU name(s) (PowerShell/WMIC on Windows,
 * {@code lspci} on Linux, {@code system_profiler} on macOS — informational only there)
 * and classifies the result into:
 * <ul>
 *   <li>{@link #ORDER_HARDWARE} ({@code "d3d,es2,sw"}) — dedicated GPU, hardware preferred
 *       with software as a safety net, or</li>
 *   <li>{@link #ORDER_SOFTWARE} ({@code "sw"}) — integrated/virtual/unknown adapter.</li>
 * </ul>
 *
 * <p>The classification is intentionally <strong>conservative</strong>: anything that is
 * not confidently identified as a dedicated GPU falls back to software rendering, since a
 * slower-but-stable startup is preferable to a crash. The manual "Hardware" override in
 * the settings is the escape hatch when a dedicated GPU is not recognized.
 *
 * <p>This class has no JavaFX dependency and is safe to call from {@code main()} before
 * the JavaFX toolkit initializes. It never throws — any failure yields {@link #ORDER_SOFTWARE}.
 */
public final class RenderingPipelineDetector {

    private static final Logger logger = LogManager.getLogger(RenderingPipelineDetector.class);

    /** Prism order preferring hardware (Direct3D on Windows, OpenGL ES2 elsewhere) with software fallback. */
    public static final String ORDER_HARDWARE = "d3d,es2,sw";

    /** Prism order forcing pure software rendering. */
    public static final String ORDER_SOFTWARE = "sw";

    /**
     * Substrings (lower-case) identifying integrated, virtual or software adapters that
     * should use software rendering. Checked before the dedicated markers so that, e.g.,
     * "Intel" always wins over an accidental dedicated keyword.
     */
    private static final List<String> INTEGRATED_MARKERS = List.of(
            "intel",            // Intel HD/UHD/Iris integrated graphics (and, conservatively, Intel Arc)
            "uhd graphics",
            "hd graphics",
            "iris",
            "microsoft basic",  // Microsoft Basic Render Driver (no GPU)
            "basic render",
            "llvmpipe",         // Mesa software rasterizer
            "softpipe",
            "swrast",
            "vmware",           // virtual machine adapters
            "virtualbox",
            "svga",
            "qxl",
            "parallels"
    );

    /** Substrings (lower-case) identifying dedicated GPUs that run hardware rendering reliably. */
    private static final List<String> DEDICATED_MARKERS = List.of(
            "nvidia", "geforce", "quadro", "rtx", "gtx", "tesla",
            "amd", "radeon", "firepro"
    );

    private static final long COMMAND_TIMEOUT_SECONDS = 2;

    private RenderingPipelineDetector() {
        // utility class
    }

    /**
     * Detects the recommended {@code prism.order} value for the current machine.
     *
     * @return {@link #ORDER_HARDWARE} when a dedicated GPU is detected (or on macOS),
     * otherwise {@link #ORDER_SOFTWARE}. Never {@code null}, never throws.
     */
    public static String detectPrismOrder() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            List<String> adapters;
            if (os.contains("win")) {
                adapters = queryWindowsAdapters();
            } else if (os.contains("mac") || os.contains("darwin")) {
                // Apple Silicon (Metal) and Intel Macs with Metal run hardware rendering
                // reliably; always prefer hardware. The query is informational only.
                List<String> macAdapters = queryMacAdapters();
                logger.info("Rendering auto-detect (macOS): adapters={}, choosing hardware", macAdapters);
                return ORDER_HARDWARE;
            } else {
                adapters = queryLinuxAdapters();
            }

            String order = classifyAdapters(adapters);
            logger.info("Rendering auto-detect: os='{}', adapters={}, prism.order={}",
                    System.getProperty("os.name"), adapters, order);
            return order;
        } catch (Throwable t) {
            logger.warn("Rendering auto-detect failed, falling back to software rendering: {}", t.toString());
            return ORDER_SOFTWARE;
        }
    }

    /**
     * Classifies a list of GPU adapter names into a Prism order. If <em>any</em> adapter
     * is identified as a dedicated GPU, hardware rendering is chosen (covers hybrid laptops
     * with an integrated + dedicated GPU). An empty list yields software rendering.
     *
     * @param adapterNames the detected adapter names (may be empty)
     * @return {@link #ORDER_HARDWARE} or {@link #ORDER_SOFTWARE}
     */
    static String classifyAdapters(List<String> adapterNames) {
        if (adapterNames == null || adapterNames.isEmpty()) {
            return ORDER_SOFTWARE;
        }
        for (String name : adapterNames) {
            if (ORDER_HARDWARE.equals(classify(name))) {
                return ORDER_HARDWARE;
            }
        }
        return ORDER_SOFTWARE;
    }

    /**
     * Classifies a single GPU adapter name as hardware- or software-rendering capable.
     *
     * @param gpuName the adapter name (e.g. {@code "NVIDIA GeForce RTX 3070"}); may be {@code null}
     * @return {@link #ORDER_HARDWARE} for a recognized dedicated GPU, otherwise {@link #ORDER_SOFTWARE}
     */
    static String classify(String gpuName) {
        if (gpuName == null) {
            return ORDER_SOFTWARE;
        }
        String n = gpuName.toLowerCase(Locale.ROOT).trim();
        if (n.isEmpty()) {
            return ORDER_SOFTWARE;
        }

        // 1. Integrated / virtual / software adapters -> software rendering.
        for (String marker : INTEGRATED_MARKERS) {
            if (n.contains(marker)) {
                return ORDER_SOFTWARE;
            }
        }

        // 2. AMD integrated APUs ("Radeon Graphics", "Radeon(TM) Graphics", "... Vega ... Graphics")
        //    are integrated; discrete Radeons ("RX", "Pro", numbered models) are not.
        if (n.contains("radeon") && (n.contains("vega")
                || n.matches(".*radeon\\s*(\\(tm\\))?\\s*graphics.*"))) {
            return ORDER_SOFTWARE;
        }

        // 3. Recognized dedicated GPUs -> hardware rendering.
        for (String marker : DEDICATED_MARKERS) {
            if (n.contains(marker)) {
                return ORDER_HARDWARE;
            }
        }

        // 4. Unknown adapter -> conservative software rendering.
        return ORDER_SOFTWARE;
    }

    private static List<String> queryWindowsAdapters() {
        // PowerShell is the modern, reliable source; WMIC is the legacy fallback.
        String out = runCommand(List.of("powershell", "-NoProfile", "-Command",
                "Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name"));
        if (out == null || out.isBlank()) {
            out = runCommand(List.of("wmic", "path", "win32_VideoController", "get", "name"));
        }
        List<String> names = new ArrayList<>();
        if (out != null) {
            for (String line : out.split("\\R")) {
                String t = line.trim();
                // WMIC emits a "Name" header row; skip it and blanks.
                if (!t.isEmpty() && !t.equalsIgnoreCase("Name")) {
                    names.add(t);
                }
            }
        }
        return names;
    }

    private static List<String> queryLinuxAdapters() {
        String out = runCommand(List.of("sh", "-c", "lspci 2>/dev/null"));
        List<String> names = new ArrayList<>();
        if (out != null) {
            for (String line : out.split("\\R")) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("vga compatible controller")
                        || lower.contains("3d controller")
                        || lower.contains("display controller")) {
                    // Keep the description after the last ": " (the adapter name).
                    int idx = line.indexOf(": ", line.indexOf(':') + 1);
                    names.add(idx >= 0 ? line.substring(idx + 2).trim() : line.trim());
                }
            }
        }
        return names;
    }

    private static List<String> queryMacAdapters() {
        String out = runCommand(List.of("sh", "-c",
                "system_profiler SPDisplaysDataType 2>/dev/null | grep -i 'Chipset Model'"));
        List<String> names = new ArrayList<>();
        if (out != null) {
            for (String line : out.split("\\R")) {
                int idx = line.indexOf(':');
                if (idx >= 0) {
                    names.add(line.substring(idx + 1).trim());
                }
            }
        }
        return names;
    }

    /**
     * Runs an external command with a short timeout and returns its stdout, or {@code null}
     * on any failure/timeout. Never throws.
     */
    private static String runCommand(List<String> command) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }

            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.debug("GPU query command timed out: {}", command);
                return null;
            }
            return sb.toString();
        } catch (Throwable t) {
            logger.debug("GPU query command failed ({}): {}", command, t.toString());
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
