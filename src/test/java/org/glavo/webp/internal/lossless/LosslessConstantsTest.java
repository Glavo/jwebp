// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests compact VP8L constant representations.
@NotNullByDefault
final class LosslessConstantsTest {

    /// Verifies that every vertical distance offset fits in the low three bits.
    @Test
    void distanceMapUsesThreeBitVerticalOffsets() {
        for (byte encoded : LosslessConstants.DISTANCE_MAP) {
            assertEquals(0, encoded & 0x8);
        }
    }
}
