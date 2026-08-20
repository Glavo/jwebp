// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests packed `ARGB` channel arithmetic.
@NotNullByDefault
final class ArgbTest {

    /// Verifies premultiplication and its necessarily lossy inverse at alpha boundaries.
    @Test
    void convertsBetweenPremultipliedAndNonPremultipliedPixels() {
        assertEquals(0x0000_0000, Argb.premultiply(0x0011_2233));
        assertEquals(0xFF11_2233, Argb.premultiply(0xFF11_2233));
        assertEquals(0x8080_4020, Argb.premultiply(0x80FF_8040));

        assertEquals(0x0000_0000, Argb.unpremultiply(0x0000_0000));
        assertEquals(0xFF11_2233, Argb.unpremultiply(0xFF11_2233));
        assertEquals(0x80FF_8040, Argb.unpremultiply(0x8080_4020));
    }

    /// Verifies exact premultiplication rounding for every alpha and channel value.
    @Test
    void premultipliesChannelsWithExactRounding() {
        for (int alpha = 0; alpha <= 0xFF; alpha++) {
            for (int channel = 0; channel <= 0xFF; channel++) {
                int red = channel;
                int green = (channel + 0x55) & 0xFF;
                int blue = (channel + 0xAA) & 0xFF;
                int expected = Argb.pack(
                        alpha,
                        (red * alpha + 0x7F) / 0xFF,
                        (green * alpha + 0x7F) / 0xFF,
                        (blue * alpha + 0x7F) / 0xFF
                );
                assertEquals(expected, Argb.premultiply(Argb.pack(alpha, red, green, blue)));
            }
        }
    }

    /// Verifies in-place array premultiplication and opacity reporting.
    @Test
    void premultipliesPixelArrays() {
        int[] empty = new int[0];
        assertTrue(Argb.premultiply(empty));

        int[] opaque = {0xFF11_2233, 0xFF44_5566};
        assertTrue(Argb.premultiply(opaque));
        assertArrayEquals(new int[]{0xFF11_2233, 0xFF44_5566}, opaque);

        int[] mixed = {0xFF11_2233, 0x80FF_8040, 0x0011_2233, 0xFF44_5566};
        assertFalse(Argb.premultiply(mixed));
        assertArrayEquals(new int[]{0xFF11_2233, 0x8080_4020, 0, 0xFF44_5566}, mixed);
    }

    /// Verifies opaque-prefix detection across empty, opaque, and translucent arrays.
    @Test
    void countsOpaqueArrayPrefixes() {
        assertEquals(0, Argb.countOpaquePrefix(new int[0]));
        assertEquals(3, Argb.countOpaquePrefix(new int[]{
                0xFF11_2233,
                0xFF44_5566,
                0xFF77_8899
        }));
        assertEquals(2, Argb.countOpaquePrefix(new int[]{
                0xFF11_2233,
                0xFF44_5566,
                0x8055_6677,
                0xFF88_99AA
        }));
        assertEquals(0, Argb.countOpaquePrefix(new int[]{0x0011_2233, 0xFF44_5566}));
    }

    /// Verifies opaque-prefix detection around aggregate scan boundaries.
    @Test
    void countsOpaquePrefixesAcrossScanBoundaries() {
        for (int prefixLength : new int[]{0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17, 31, 32, 33}) {
            int[] pixels = new int[prefixLength + 1];
            Arrays.fill(pixels, 0xFF12_3456);
            pixels[prefixLength] = 0xFE12_3456;
            assertEquals(prefixLength, Argb.countOpaquePrefix(pixels));

            IntBuffer directPixels = ByteBuffer.allocateDirect((prefixLength + 3) * Integer.BYTES)
                    .asIntBuffer();
            directPixels.put(0, 0x0012_3456);
            for (int index = 0; index < prefixLength; index++) {
                directPixels.put(index + 1, 0xFF12_3456);
            }
            directPixels.put(prefixLength + 1, 0xFE12_3456);
            directPixels.put(prefixLength + 2, 0xFF12_3456);
            directPixels.position(1);
            directPixels.limit(prefixLength + 2);

            assertEquals(prefixLength, Argb.countOpaquePrefix(directPixels));
            assertEquals(1, directPixels.position());
            assertEquals(prefixLength + 2, directPixels.limit());
        }
    }

    /// Verifies that buffer-prefix detection is relative to the remaining region and preserves state.
    @Test
    void countsOpaqueBufferPrefixesWithoutChangingState() {
        IntBuffer pixels = IntBuffer.wrap(new int[]{
                0x0011_2233,
                0xFF44_5566,
                0xFF77_8899,
                0x80AA_BBCC,
                0xFFD0_E0F0
        });
        pixels.position(1);
        pixels.limit(4);

        assertEquals(2, Argb.countOpaquePrefix(pixels));
        assertEquals(1, pixels.position());
        assertEquals(4, pixels.limit());
    }

    /// Verifies that packed addition matches independent modulo-256 channel arithmetic.
    @Test
    void addsChannelsIndependently() {
        Random random = new Random(0x4A_57_45_42_50L);
        for (int iteration = 0; iteration < 100_000; iteration++) {
            int left = random.nextInt();
            int right = random.nextInt();
            int expected = Argb.pack(
                    Argb.alpha(left) + Argb.alpha(right),
                    Argb.red(left) + Argb.red(right),
                    Argb.green(left) + Argb.green(right),
                    Argb.blue(left) + Argb.blue(right)
            );
            assertEquals(expected, Argb.add(left, right));
        }
    }

    /// Verifies that packed averaging matches independent channel arithmetic.
    @Test
    void averagesChannelsIndependently() {
        Random random = new Random(0x41_56_45_52_41_47_45L);
        for (int iteration = 0; iteration < 100_000; iteration++) {
            int left = random.nextInt();
            int right = random.nextInt();
            int expected = Argb.pack(
                    (Argb.alpha(left) + Argb.alpha(right)) >>> 1,
                    (Argb.red(left) + Argb.red(right)) >>> 1,
                    (Argb.green(left) + Argb.green(right)) >>> 1,
                    (Argb.blue(left) + Argb.blue(right)) >>> 1
            );
            assertEquals(expected, Argb.average2(left, right));
        }
    }
}
