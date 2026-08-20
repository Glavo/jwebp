// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.javafx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests adaptive JavaFX pixel storage selection and allocation.
@NotNullByDefault
final class WebPFXImageStorageTest {

    /// Verifies that ordinary images use heap-backed storage.
    @Test
    void allocatesOrdinaryImagesOnHeap() {
        IntBuffer pixels = WebPFXImageStorage.allocate(3, 2);

        assertFalse(pixels.isDirect());
        assertEquals(6, pixels.capacity());
    }

    /// Verifies direct allocation mechanics independently of the size threshold.
    @Test
    void allocatesNativeOrderDirectStorageWhenPreferred() {
        IntBuffer pixels = WebPFXImageStorage.allocatePixels(6, true);

        assertTrue(pixels.isDirect());
        assertEquals(ByteOrder.nativeOrder(), pixels.order());
        assertEquals(6, pixels.capacity());
    }

    /// Verifies threshold selection for one image and for a combined animation allocation.
    @Test
    void selectsDirectStorageFromCombinedRetainedSize() {
        int threshold = WebPFXImageStorage.DIRECT_ALLOCATION_THRESHOLD_PIXELS;

        assertFalse(WebPFXImageStorage.prefersDirect(threshold - 1, 1));
        assertTrue(WebPFXImageStorage.prefersDirect(threshold, 1));
        assertFalse(WebPFXImageStorage.prefersDirect(1, threshold - 1));
        assertTrue(WebPFXImageStorage.prefersDirect(1, threshold));
    }

    /// Verifies that one region beyond the direct byte-buffer limit selects heap storage even when
    /// its retained size is above the direct threshold, while a multi-region animation may exceed
    /// that limit in aggregate.
    @Test
    void fallsBackToHeapSelectionBeyondDirectBufferCapacity() {
        assertTrue(WebPFXImageStorage.prefersDirect(
                WebPFXImageStorage.MAX_DIRECT_PIXEL_COUNT,
                2
        ));
        assertFalse(WebPFXImageStorage.prefersDirect(
                WebPFXImageStorage.MAX_DIRECT_PIXEL_COUNT + 1,
                1
        ));
    }

    /// Verifies that ordinary animation regions are packed into distinct heap-backed slices.
    @Test
    void packsOrdinaryAnimationRegionsOnHeap() {
        IntBuffer[] regions = WebPFXImageStorage.allocateRegions(3, 2);

        assertEquals(3, regions.length);
        for (IntBuffer region : regions) {
            assertFalse(region.isDirect());
            assertEquals(2, region.capacity());
        }

        regions[0].put(0, 1);
        regions[1].put(0, 2);
        regions[2].put(0, 3);
        assertEquals(1, regions[0].get(0));
        assertEquals(2, regions[1].get(0));
        assertEquals(3, regions[2].get(0));
    }

    /// Verifies that partial animation preparation can preserve a preselected storage location.
    @Test
    void packsAnimationRegionsAtRequestedLocation() {
        IntBuffer[] heapRegions = WebPFXImageStorage.allocateRegions(2, 3, false);
        IntBuffer[] directRegions = WebPFXImageStorage.allocateRegions(2, 3, true);

        for (IntBuffer region : heapRegions) {
            assertFalse(region.isDirect());
            assertEquals(3, region.capacity());
        }
        for (IntBuffer region : directRegions) {
            assertTrue(region.isDirect());
            assertEquals(ByteOrder.nativeOrder(), region.order());
            assertEquals(3, region.capacity());
        }
    }
}
