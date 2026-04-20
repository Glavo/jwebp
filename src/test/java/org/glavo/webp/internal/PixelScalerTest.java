/*
 * Copyright 2026 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Unit tests for packed-`ARGB` scaling.
@NotNullByDefault
final class PixelScalerTest {

    @Test
    void nearestNeighborScalingRepeatsExpectedSourcePixels() {
        int topLeft = Argb.pack(0xFF, 10, 20, 30);
        int topRight = Argb.pack(0xFF, 40, 50, 60);
        int bottomLeft = Argb.pack(0xFF, 70, 80, 90);
        int bottomRight = Argb.pack(0xFF, 100, 110, 120);

        int[] source = {
                topLeft, topRight,
                bottomLeft, bottomRight
        };

        int[] scaled = PixelScaler.scaleArgb(source, 2, 2, new ScalePlan(2, 2, 4, 4, false));
        int[] expected = {
                topLeft, topLeft, topRight, topRight,
                topLeft, topLeft, topRight, topRight,
                bottomLeft, bottomLeft, bottomRight, bottomRight,
                bottomLeft, bottomLeft, bottomRight, bottomRight
        };

        assertArrayEquals(expected, scaled);
    }

    @Test
    void bilinearScalingInterpolatesChannelsIndependently() {
        int topLeft = Argb.pack(255, 0, 0, 0);
        int topRight = Argb.pack(128, 200, 0, 0);
        int bottomLeft = Argb.pack(64, 0, 100, 0);
        int bottomRight = Argb.pack(0, 0, 0, 240);

        int[] source = {
                topLeft, topRight,
                bottomLeft, bottomRight
        };

        int[] scaled = PixelScaler.scaleArgb(source, 2, 2, new ScalePlan(2, 2, 3, 3, true));

        assertEquals(topLeft, scaled[0]);
        assertEquals(Argb.pack(192, 100, 0, 0), scaled[1]);
        assertEquals(topRight, scaled[2]);
        assertEquals(Argb.pack(160, 0, 50, 0), scaled[3]);
        assertEquals(Argb.pack(112, 50, 25, 60), scaled[4]);
        assertEquals(Argb.pack(64, 100, 0, 120), scaled[5]);
        assertEquals(bottomLeft, scaled[6]);
        assertEquals(Argb.pack(32, 0, 50, 120), scaled[7]);
        assertEquals(bottomRight, scaled[8]);
    }

    @Test
    void noOpScalingReturnsSourceArray() {
        int[] source = {
                Argb.pack(255, 1, 2, 3),
                Argb.pack(255, 4, 5, 6),
                Argb.pack(255, 7, 8, 9),
                Argb.pack(255, 10, 11, 12)
        };

        int[] scaled = PixelScaler.scaleArgb(source, 2, 2, new ScalePlan(2, 2, 2, 2, true));

        assertArrayEquals(source, scaled);
        assertSame(source, scaled);
    }
}
