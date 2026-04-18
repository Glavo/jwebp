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
package org.glavo.javafx.webp.internal.codec;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Ports of [extended.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/extended.rs) regression tests.
final class ExtendedWebPTest {

    @Test
    void disposeClearFullsizeRgbFrame() {
        int width = 4;
        int height = 4;
        byte[] canvas = new byte[width * height * 4];
        Arrays.fill(canvas, (byte) 0xAA);
        byte[] frame = new byte[width * height * 3];
        Arrays.fill(frame, (byte) 0xFF);

        ExtendedWebP.compositeFrame(
                canvas,
                width,
                height,
                new byte[]{0, 0, 0, 0},
                frame,
                0,
                0,
                width,
                height,
                false,
                true,
                width,
                height,
                0,
                0
        );

        for (int i = 0; i < width * height; i++) {
            assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, pixel(canvas, width, i % width, i / width));
        }
    }

    @Test
    void disposeClearSubframeRgbFrame() {
        int canvasWidth = 8;
        int canvasHeight = 8;
        byte[] canvas = new byte[canvasWidth * canvasHeight * 4];
        Arrays.fill(canvas, (byte) 0xAA);

        int previousX = 2;
        int previousY = 2;
        int previousWidth = 4;
        int previousHeight = 4;

        int frameWidth = 4;
        int frameHeight = 4;
        byte[] frame = new byte[frameWidth * frameHeight * 3];
        Arrays.fill(frame, (byte) 0xFF);

        ExtendedWebP.compositeFrame(
                canvas,
                canvasWidth,
                canvasHeight,
                new byte[]{0, 0, 0, 0},
                frame,
                0,
                0,
                frameWidth,
                frameHeight,
                false,
                true,
                previousWidth,
                previousHeight,
                previousX,
                previousY
        );

        for (int y = previousY; y < previousY + previousHeight; y++) {
            for (int x = previousX; x < previousX + previousWidth; x++) {
                if (x >= frameWidth || y >= frameHeight) {
                    assertArrayEquals(new byte[]{0, 0, 0, 0}, pixel(canvas, canvasWidth, x, y));
                }
            }
        }

        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, pixel(canvas, canvasWidth, x, y));
            }
        }
    }

    private static byte[] pixel(byte[] rgba, int width, int x, int y) {
        int index = (y * width + x) * 4;
        return new byte[]{rgba[index], rgba[index + 1], rgba[index + 2], rgba[index + 3]};
    }
}
