// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests endian-aware primitive loads from byte arrays.
@NotNullByDefault
final class ArrayUtilsTest {

    /// Verifies little-endian loads at an unaligned byte offset.
    @Test
    void readsLittleEndianValuesAtUnalignedOffset() {
        byte[] bytes = sampleBytes();

        assertEquals((short) 0x2301, ArrayUtils.getShortLE(bytes, 1));
        assertEquals(0x6745_2301, ArrayUtils.getIntLE(bytes, 1));
        assertEquals(0xEFCD_AB89_6745_2301L, ArrayUtils.getLongLE(bytes, 1));
    }

    /// Verifies big-endian loads at an unaligned byte offset.
    @Test
    void readsBigEndianValuesAtUnalignedOffset() {
        byte[] bytes = sampleBytes();

        assertEquals((short) 0x0123, ArrayUtils.getShortBE(bytes, 1));
        assertEquals(0x0123_4567, ArrayUtils.getIntBE(bytes, 1));
        assertEquals(0x0123_4567_89AB_CDEFL, ArrayUtils.getLongBE(bytes, 1));
    }

    /// Verifies that loads reject ranges extending beyond the source array.
    @Test
    void rejectsOutOfBoundsRanges() {
        byte[] bytes = new byte[Long.BYTES];

        assertThrows(IndexOutOfBoundsException.class, () -> ArrayUtils.getShortLE(bytes, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> ArrayUtils.getShortBE(bytes, bytes.length - 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ArrayUtils.getIntLE(bytes, bytes.length - 3));
        assertThrows(IndexOutOfBoundsException.class, () -> ArrayUtils.getIntBE(bytes, bytes.length - 3));
        assertThrows(IndexOutOfBoundsException.class, () -> ArrayUtils.getLongLE(bytes, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ArrayUtils.getLongBE(bytes, 1));
    }

    /// Returns bytes containing a recognizable primitive value beginning at offset one.
    ///
    /// @return the sample bytes
    private static byte[] sampleBytes() {
        return new byte[]{
                0,
                0x01,
                0x23,
                0x45,
                0x67,
                (byte) 0x89,
                (byte) 0xAB,
                (byte) 0xCD,
                (byte) 0xEF,
                0
        };
    }
}
