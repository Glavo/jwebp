// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;

import java.util.Objects;

/// Bit reader for VP8L streams.
///
/// Bits are consumed least-significant-bit first as required by the WebP lossless format.
@NotNullByDefault
public final class LosslessBitReader {

    /// Array containing the selected VP8L byte range.
    private final byte[] data;

    /// Exclusive array index of the selected VP8L byte range.
    private final int endPosition;

    /// Index of the next encoded byte to buffer.
    private int bytePosition;

    /// Buffered bits in least-significant-bit-first order.
    private long buffer;

    /// Number of unread low bits in [#buffer].
    private int bitCount;

    /// Creates a new bit reader for a chunk payload.
    ///
    /// @param data the encoded VP8L bytes
    public LosslessBitReader(byte[] data) {
        this(data, 0, data.length);
    }

    /// Creates a new bit reader for a range within a chunk payload.
    ///
    /// @param data the array containing the encoded VP8L bytes
    /// @param offset the first encoded byte
    /// @param length the encoded byte count
    /// @throws IndexOutOfBoundsException if the range lies outside the array
    public LosslessBitReader(byte[] data, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, data.length);
        this.data = data;
        this.bytePosition = offset;
        this.endPosition = offset + length;
    }

    /// Refills the local buffer until at least 56 bits are available or the input is exhausted.
    public void fill() {
        int position = bytePosition;
        int count = bitCount;
        long bits = buffer;
        // Use the largest load that cannot overflow the high end of the bit window.
        if (count <= 32 && endPosition - position >= Integer.BYTES) {
            long next = ((long) data[position] & 0xFFL)
                    | (((long) data[position + 1] & 0xFFL) << 8)
                    | (((long) data[position + 2] & 0xFFL) << 16)
                    | (((long) data[position + 3] & 0xFFL) << 24);
            bits |= next << count;
            count += Integer.SIZE;
            position += Integer.BYTES;
        }
        while (count < 56 && position < endPosition) {
            bits |= ((long) data[position++] & 0xFFL) << count;
            count += Byte.SIZE;
        }
        buffer = bits;
        bitCount = count;
        bytePosition = position;
    }

    /// Returns the low `bits` bits of the current buffer without consuming them.
    ///
    /// @param bits the number of bits to inspect
    /// @return the peeked value
    public long peek(int bits) {
        if (bits == 64) {
            return buffer;
        }
        return buffer & ((1L << bits) - 1L);
    }

    /// Returns the full raw bit buffer.
    ///
    /// @return the current bit buffer
    public long peekFull() {
        return buffer;
    }

    /// Consumes the requested number of bits.
    ///
    /// @param bits the number of bits to consume
    /// @throws WebPException if not enough bits are available
    public void consume(int bits) throws WebPException {
        if (bitCount < bits) {
            throw new WebPException("Corrupt VP8L bitstream");
        }
        buffer >>>= bits;
        bitCount -= bits;
    }

    /// Reads an unsigned integer composed of the requested number of bits.
    ///
    /// @param bits the number of bits to read
    /// @return the decoded value
    /// @throws WebPException if the bitstream is truncated
    public int readBits(int bits) throws WebPException {
        if (bitCount < bits) {
            fill();
        }
        int value = (int) peek(bits);
        consume(bits);
        return value;
    }

    /// Returns the number of buffered bits.
    ///
    /// @return the bit count in the local buffer
    public int bitCount() {
        return bitCount;
    }
}
