// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Ports of [lossless/decoder/mod.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossless/decoder/mod.rs) bit-reader tests.
@NotNullByDefault
final class LosslessBitReaderTest {

    @Test
    void bitReadTest() throws Exception {
        LosslessBitReader bitReader = new LosslessBitReader(new byte[]{(byte) 0x9C, 0x41, (byte) 0xE1});

        assertEquals(4, bitReader.readBits(3));
        assertEquals(3, bitReader.readBits(2));
        assertEquals(12, bitReader.readBits(6));
        assertEquals(40, bitReader.readBits(10));
        assertEquals(7, bitReader.readBits(3));
    }

    @Test
    void bitReadErrorTest() throws Exception {
        LosslessBitReader bitReader = new LosslessBitReader(new byte[]{0x6A});

        assertEquals(2, bitReader.readBits(3));
        assertEquals(13, bitReader.readBits(5));
        assertThrows(WebPException.class, () -> bitReader.readBits(4));
    }

    /// Verifies that resetting changes the input range and discards buffered bits.
    @Test
    void resetsInputRangeAndBufferedState() throws Exception {
        LosslessBitReader bitReader = new LosslessBitReader(new byte[]{0x6A, 0x55});
        assertEquals(2, bitReader.readBits(3));

        byte[] replacement = {(byte) 0xFF, 0x34, 0x12, (byte) 0x80};
        bitReader.reset(replacement, 1, 2);

        assertEquals(0x1234, bitReader.readBits(16));
        assertThrows(WebPException.class, () -> bitReader.readBits(1));
    }

    /// Verifies mixed-width reads across unaligned array ranges and refill boundaries.
    @Test
    void readsRandomUnalignedRangesAcrossRefills() throws Exception {
        Random random = new Random(0x4C4F_4E47_5245_4144L);
        for (int offset = 0; offset < Long.BYTES; offset++) {
            for (int length = 1; length <= 64; length++) {
                byte[] data = new byte[offset + length + Long.BYTES];
                random.nextBytes(data);
                LosslessBitReader bitReader = new LosslessBitReader(data, offset, length);

                int bitOffset = 0;
                int totalBits = length * Byte.SIZE;
                while (bitOffset < totalBits) {
                    int width = Math.min(1 + random.nextInt(Integer.SIZE), totalBits - bitOffset);
                    int expected = readBits(data, offset * Byte.SIZE + bitOffset, width);
                    assertEquals(expected, bitReader.readBits(width));
                    bitOffset += width;
                }
                assertThrows(WebPException.class, () -> bitReader.readBits(1));
            }
        }
    }

    /// Returns an unsigned bit sequence in least-significant-bit-first order.
    ///
    /// @param data the source bytes
    /// @param bitOffset the source bit offset
    /// @param width the number of bits to read
    /// @return the decoded value
    private static int readBits(byte[] data, int bitOffset, int width) {
        int value = 0;
        for (int index = 0; index < width; index++) {
            int sourceBit = bitOffset + index;
            int bit = (data[sourceBit / Byte.SIZE] >>> (sourceBit & (Byte.SIZE - 1))) & 1;
            value |= bit << index;
        }
        return value;
    }
}
