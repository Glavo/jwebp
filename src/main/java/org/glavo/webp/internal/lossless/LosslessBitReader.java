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

    /// Ensures that up to 56 bits are available in the local buffer.
    ///
    /// @throws WebPException if the stream is malformed
    public void fill() throws WebPException {
        while (bitCount < 56 && bytePosition < endPosition) {
            buffer |= ((long) data[bytePosition] & 0xFFL) << bitCount;
            bitCount += 8;
            bytePosition++;
        }
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
