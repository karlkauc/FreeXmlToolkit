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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RenderingPipelineDetector}'s GPU-name classification heuristic.
 * These tests exercise the pure classification logic and never spawn a subprocess.
 */
class RenderingPipelineDetectorTest {

    private static final String HW = RenderingPipelineDetector.ORDER_HARDWARE; // "d3d,es2,sw"
    private static final String SW = RenderingPipelineDetector.ORDER_SOFTWARE; // "sw"

    @Test
    void dedicatedNvidiaGpuUsesHardware() {
        assertEquals(HW, RenderingPipelineDetector.classify("NVIDIA GeForce RTX 3070"));
        assertEquals(HW, RenderingPipelineDetector.classify("NVIDIA Quadro P2000"));
        assertEquals(HW, RenderingPipelineDetector.classify("NVIDIA GeForce GTX 1650"));
    }

    @Test
    void dedicatedAmdGpuUsesHardware() {
        assertEquals(HW, RenderingPipelineDetector.classify("AMD Radeon Pro 5500M"));
        assertEquals(HW, RenderingPipelineDetector.classify("AMD Radeon RX 6800 XT"));
        assertEquals(HW, RenderingPipelineDetector.classify("Advanced Micro Devices, Inc. [AMD/ATI] Navi 22"));
    }

    @Test
    void intelIntegratedGpuUsesSoftware() {
        assertEquals(SW, RenderingPipelineDetector.classify("Intel(R) UHD Graphics 620"));
        assertEquals(SW, RenderingPipelineDetector.classify("Intel HD Graphics 4000"));
        assertEquals(SW, RenderingPipelineDetector.classify("Intel(R) Iris(R) Xe Graphics"));
    }

    @Test
    void amdIntegratedApuUsesSoftware() {
        assertEquals(SW, RenderingPipelineDetector.classify("AMD Radeon(TM) Graphics"));
        assertEquals(SW, RenderingPipelineDetector.classify("AMD Radeon Vega 8 Graphics"));
    }

    @Test
    void virtualAndSoftwareAdaptersUseSoftware() {
        assertEquals(SW, RenderingPipelineDetector.classify("Microsoft Basic Render Driver"));
        assertEquals(SW, RenderingPipelineDetector.classify("llvmpipe (LLVM 15.0.0, 256 bits)"));
        assertEquals(SW, RenderingPipelineDetector.classify("VMware SVGA 3D"));
    }

    @Test
    void unknownNullAndEmptyUseSoftware() {
        assertEquals(SW, RenderingPipelineDetector.classify(null));
        assertEquals(SW, RenderingPipelineDetector.classify(""));
        assertEquals(SW, RenderingPipelineDetector.classify("   "));
        assertEquals(SW, RenderingPipelineDetector.classify("SomeUnknownAdapter 9000"));
    }

    @Test
    void hybridLaptopWithDedicatedGpuUsesHardware() {
        // Integrated + dedicated -> hardware wins.
        assertEquals(HW, RenderingPipelineDetector.classifyAdapters(
                List.of("Intel(R) UHD Graphics 630", "NVIDIA GeForce RTX 2060")));
    }

    @Test
    void integratedOnlyUsesSoftware() {
        assertEquals(SW, RenderingPipelineDetector.classifyAdapters(
                List.of("Intel(R) UHD Graphics 620")));
    }

    @Test
    void emptyAdapterListUsesSoftware() {
        assertEquals(SW, RenderingPipelineDetector.classifyAdapters(List.of()));
        assertEquals(SW, RenderingPipelineDetector.classifyAdapters(null));
    }

    @Test
    void detectPrismOrderNeverThrowsAndReturnsValidValue() {
        String order = RenderingPipelineDetector.detectPrismOrder();
        assertTrue(HW.equals(order) || SW.equals(order),
                "detectPrismOrder must return a valid prism.order, was: " + order);
    }
}
