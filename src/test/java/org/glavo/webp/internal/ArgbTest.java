// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
