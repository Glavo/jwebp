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
package org.glavo.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.internal.Argb;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Ports of [extended.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/extended.rs) regression tests.
@NotNullByDefault
final class ExtendedWebPTest {

    @Test
    void disposeClearFullsizeRgbFrame() {
        int width = 4;
        int height = 4;
        int[] canvas = new int[width * height];
        Arrays.fill(canvas, Argb.pack(0xAA, 0xAA, 0xAA, 0xAA));
        int[] frame = new int[width * height];
        Arrays.fill(frame, 0xFFFF_FFFF);

        ExtendedWebP.compositeFrame(
                canvas,
                width,
                height,
                0,
                frame,
                0,
                0,
                width,
                height,
                true,
                width,
                height,
                0,
                0
        );

        for (int i = 0; i < width * height; i++) {
            assertEquals(0xFFFF_FFFF, pixel(canvas, width, i % width, i / width));
        }
    }

    @Test
    void disposeClearSubframeRgbFrame() {
        int canvasWidth = 8;
        int canvasHeight = 8;
        int[] canvas = new int[canvasWidth * canvasHeight];
        Arrays.fill(canvas, Argb.pack(0xAA, 0xAA, 0xAA, 0xAA));

        int previousX = 2;
        int previousY = 2;
        int previousWidth = 4;
        int previousHeight = 4;

        int frameWidth = 4;
        int frameHeight = 4;
        int[] frame = new int[frameWidth * frameHeight];
        Arrays.fill(frame, 0xFFFF_FFFF);

        ExtendedWebP.compositeFrame(
                canvas,
                canvasWidth,
                canvasHeight,
                0,
                frame,
                0,
                0,
                frameWidth,
                frameHeight,
                true,
                previousWidth,
                previousHeight,
                previousX,
                previousY
        );

        for (int y = previousY; y < previousY + previousHeight; y++) {
            for (int x = previousX; x < previousX + previousWidth; x++) {
                if (x >= frameWidth || y >= frameHeight) {
                    assertEquals(0, pixel(canvas, canvasWidth, x, y));
                }
            }
        }

        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                assertEquals(0xFFFF_FFFF, pixel(canvas, canvasWidth, x, y));
            }
        }
    }

    private static int pixel(int[] argb, int width, int x, int y) {
        return argb[y * width + x];
    }
}
