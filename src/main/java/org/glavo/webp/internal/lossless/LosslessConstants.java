// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Shared lossless WebP constants lifted from the WebP lossless bitstream specification.
@NotNullByDefault
public final class LosslessConstants {

    /// Number of code-length alphabet symbols.
    public static final int CODE_LENGTH_CODES = 19;

    /// Canonical traversal order for code-length codes.
    public static final byte @Unmodifiable [] CODE_LENGTH_CODE_ORDER = {
            17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    /// Back-reference distance map defined by the VP8L format.
    ///
    /// Each byte packs one horizontal-offset (`dx`) and one vertical-offset (`dy`) into its high
    /// and low nibbles. Values in `-7`..`8` are mapped to the nibbles `0`..`15` by
    /// `v >= 0 ? v : 16 + v`, so the nibbles `9`..`15` stand for `-7`..`-1`.
    public static final byte @Unmodifiable [] DISTANCE_MAP = {
            (byte) 0x01, // (0, 1)
            (byte) 0x10, // (1, 0)
            (byte) 0x11, // (1, 1)
            (byte) 0xF1, // (-1, 1)
            (byte) 0x02, // (0, 2)
            (byte) 0x20, // (2, 0)
            (byte) 0x12, // (1, 2)
            (byte) 0xF2, // (-1, 2)
            (byte) 0x21, // (2, 1)
            (byte) 0xE1, // (-2, 1)
            (byte) 0x22, // (2, 2)
            (byte) 0xE2, // (-2, 2)
            (byte) 0x03, // (0, 3)
            (byte) 0x30, // (3, 0)
            (byte) 0x13, // (1, 3)
            (byte) 0xF3, // (-1, 3)
            (byte) 0x31, // (3, 1)
            (byte) 0xD1, // (-3, 1)
            (byte) 0x23, // (2, 3)
            (byte) 0xE3, // (-2, 3)
            (byte) 0x32, // (3, 2)
            (byte) 0xD2, // (-3, 2)
            (byte) 0x04, // (0, 4)
            (byte) 0x40, // (4, 0)
            (byte) 0x14, // (1, 4)
            (byte) 0xF4, // (-1, 4)
            (byte) 0x41, // (4, 1)
            (byte) 0xC1, // (-4, 1)
            (byte) 0x33, // (3, 3)
            (byte) 0xD3, // (-3, 3)
            (byte) 0x24, // (2, 4)
            (byte) 0xE4, // (-2, 4)
            (byte) 0x42, // (4, 2)
            (byte) 0xC2, // (-4, 2)
            (byte) 0x05, // (0, 5)
            (byte) 0x34, // (3, 4)
            (byte) 0xD4, // (-3, 4)
            (byte) 0x43, // (4, 3)
            (byte) 0xC3, // (-4, 3)
            (byte) 0x50, // (5, 0)
            (byte) 0x15, // (1, 5)
            (byte) 0xF5, // (-1, 5)
            (byte) 0x51, // (5, 1)
            (byte) 0xB1, // (-5, 1)
            (byte) 0x25, // (2, 5)
            (byte) 0xE5, // (-2, 5)
            (byte) 0x52, // (5, 2)
            (byte) 0xB2, // (-5, 2)
            (byte) 0x44, // (4, 4)
            (byte) 0xC4, // (-4, 4)
            (byte) 0x35, // (3, 5)
            (byte) 0xD5, // (-3, 5)
            (byte) 0x53, // (5, 3)
            (byte) 0xB3, // (-5, 3)
            (byte) 0x06, // (0, 6)
            (byte) 0x60, // (6, 0)
            (byte) 0x16, // (1, 6)
            (byte) 0xF6, // (-1, 6)
            (byte) 0x61, // (6, 1)
            (byte) 0xA1, // (-6, 1)
            (byte) 0x26, // (2, 6)
            (byte) 0xE6, // (-2, 6)
            (byte) 0x62, // (6, 2)
            (byte) 0xA2, // (-6, 2)
            (byte) 0x45, // (4, 5)
            (byte) 0xC5, // (-4, 5)
            (byte) 0x54, // (5, 4)
            (byte) 0xB4, // (-5, 4)
            (byte) 0x36, // (3, 6)
            (byte) 0xD6, // (-3, 6)
            (byte) 0x63, // (6, 3)
            (byte) 0xA3, // (-6, 3)
            (byte) 0x07, // (0, 7)
            (byte) 0x70, // (7, 0)
            (byte) 0x17, // (1, 7)
            (byte) 0xF7, // (-1, 7)
            (byte) 0x55, // (5, 5)
            (byte) 0xB5, // (-5, 5)
            (byte) 0x71, // (7, 1)
            (byte) 0x91, // (-7, 1)
            (byte) 0x46, // (4, 6)
            (byte) 0xC6, // (-4, 6)
            (byte) 0x64, // (6, 4)
            (byte) 0xA4, // (-6, 4)
            (byte) 0x27, // (2, 7)
            (byte) 0xE7, // (-2, 7)
            (byte) 0x72, // (7, 2)
            (byte) 0x92, // (-7, 2)
            (byte) 0x37, // (3, 7)
            (byte) 0xD7, // (-3, 7)
            (byte) 0x73, // (7, 3)
            (byte) 0x93, // (-7, 3)
            (byte) 0x56, // (5, 6)
            (byte) 0xB6, // (-5, 6)
            (byte) 0x65, // (6, 5)
            (byte) 0xA5, // (-6, 5)
            (byte) 0x80, // (8, 0)
            (byte) 0x47, // (4, 7)
            (byte) 0xC7, // (-4, 7)
            (byte) 0x74, // (7, 4)
            (byte) 0x94, // (-7, 4)
            (byte) 0x81, // (8, 1)
            (byte) 0x82, // (8, 2)
            (byte) 0x66, // (6, 6)
            (byte) 0xA6, // (-6, 6)
            (byte) 0x83, // (8, 3)
            (byte) 0x57, // (5, 7)
            (byte) 0xB7, // (-5, 7)
            (byte) 0x75, // (7, 5)
            (byte) 0x95, // (-7, 5)
            (byte) 0x84, // (8, 4)
            (byte) 0x67, // (6, 7)
            (byte) 0xA7, // (-6, 7)
            (byte) 0x76, // (7, 6)
            (byte) 0x96, // (-7, 6)
            (byte) 0x85, // (8, 5)
            (byte) 0x77, // (7, 7)
            (byte) 0x97, // (-7, 7)
            (byte) 0x86, // (8, 6)
            (byte) 0x87 // (8, 7)
    };

    private LosslessConstants() {
    }
}
