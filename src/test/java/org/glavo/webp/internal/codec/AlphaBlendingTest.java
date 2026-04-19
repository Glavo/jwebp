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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Port of the ignored exhaustive test in [alpha_blending.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/alpha_blending.rs).
@NotNullByDefault
final class AlphaBlendingTest {

    private static void testAlphaBlending(int a1, int r1, int a2, int r2) {
        int optimized = AlphaBlending.blend(
                Argb.pack(a1, r1, 0, 0),
                Argb.pack(a2, r2, 0, 0)
        );
        int reference = referenceBlend(
                Argb.pack(a1, r1, 0, 0),
                Argb.pack(a2, r2, 0, 0)
        );

        for (int i = 0; i < 4; i++) {
            int delta = Math.abs(channel(optimized, i) - channel(reference, i));
            assertTrue(delta <= 3,
                    "Mismatch in results. optimized="
                            + describe(optimized)
                            + ", reference="
                            + describe(reference)
                            + ", blended values=["
                            + r1 + ", 0, 0, " + a1 + "], ["
                            + r2 + ", 0, 0, " + a2 + ']');
        }
    }

    @Test
    @Disabled("Takes too long to run on CI. Run this locally when changing the function.")
    void alphaBlendingOptimization() {
        for (int r1 = 0; r1 < 255; r1++) {
            for (int a1 = 11; a1 < 255; a1++) {
                for (int r2 = 0; r2 < 255; r2++) {
                    for (int a2 = 11; a2 < 255; a2++) {
                        testAlphaBlending(a1, r1, a2, r2);
                    }
                }
            }
        }
    }

    /// Same as [alphaBlendingOptimization], but with reduced data volume to run in CI.
    @Test
    void alphaBlendingOptimizationFast() {
        var random = new Random(0);
        for (int i = 0; i < 1_000_000; i++) {
            int r1 = random.nextInt(255);
            int a1 = random.nextInt(11, 255);
            int r2 = random.nextInt(255);
            int a2 = random.nextInt(11, 255);
            testAlphaBlending(a1, r1, a2, r2);
        }
    }

    @Test
    void alphaBlendingHandlesFullyTransparentPixels() {
        int transparentSource = Argb.pack(0, 17, 34, 51);
        int transparentDestination = Argb.pack(0, 68, 85, 102);
        int visibleSource = Argb.pack(128, 120, 90, 60);
        int visibleDestination = Argb.pack(192, 25, 50, 75);

        assertEquals(
                visibleDestination,
                AlphaBlending.blend(transparentSource, visibleDestination),
                "a fully transparent source must leave the destination unchanged"
        );
        assertEquals(
                visibleSource,
                AlphaBlending.blend(visibleSource, transparentDestination),
                "a fully transparent destination must be replaced by the source"
        );
        assertEquals(
                transparentDestination,
                AlphaBlending.blend(transparentSource, transparentDestination),
                "when both pixels are transparent the destination short-circuit should win"
        );
    }

    private static int referenceBlend(int buffer, int canvas) {
        double canvasAlpha = Argb.alpha(canvas);
        double bufferAlpha = Argb.alpha(buffer);
        double blendAlphaValue = bufferAlpha + canvasAlpha * (1.0 - bufferAlpha / 255.0);
        int blendAlpha = (int) blendAlphaValue;

        int r = 0;
        int g = 0;
        int b = 0;
        if (blendAlpha != 0) {
            r = (int) ((Argb.red(buffer) * bufferAlpha
                    + Argb.red(canvas) * canvasAlpha * (1.0 - bufferAlpha / 255.0))
                    / blendAlphaValue);
            g = (int) ((Argb.green(buffer) * bufferAlpha
                    + Argb.green(canvas) * canvasAlpha * (1.0 - bufferAlpha / 255.0))
                    / blendAlphaValue);
            b = (int) ((Argb.blue(buffer) * bufferAlpha
                    + Argb.blue(canvas) * canvasAlpha * (1.0 - bufferAlpha / 255.0))
                    / blendAlphaValue);
        }

        return Argb.pack(blendAlpha, r, g, b);
    }

    private static int channel(int argb, int channel) {
        return switch (channel) {
            case 0 -> Argb.red(argb);
            case 1 -> Argb.green(argb);
            case 2 -> Argb.blue(argb);
            case 3 -> Argb.alpha(argb);
            default -> throw new IllegalArgumentException("Invalid channel index: " + channel);
        };
    }

    private static String describe(int argb) {
        return "[" + Argb.red(argb) + ", " + Argb.green(argb) + ", " + Argb.blue(argb) + ", " + Argb.alpha(argb) + "]";
    }
}
