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
package org.glavo.webp.internal.lossy;

import org.glavo.webp.internal.ArrayUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import org.glavo.webp.WebPException;

import java.nio.ByteBuffer;

/// Decodes VP8 boolean-coded header and coefficient partitions.
@NotNullByDefault
final class LossyArithmeticDecoder {

    /// Shared zero-capacity input used before this decoder is initialized.
    private static final @UnmodifiableView ByteBuffer EMPTY_INPUT =
            ByteBuffer.wrap(ArrayUtils.EMPTY_BYTE_ARRAY).asReadOnlyBuffer();

    /// Remaining bytes in the current VP8 boolean-coded partition.
    private ByteBuffer input = EMPTY_INPUT;

    /// Buffered arithmetic-coded bits aligned to the current range.
    private long value;

    /// Current arithmetic decoder range.
    private int range = 255;

    /// Number of usable buffered bits after range normalization.
    private int bitCount = -8;

    /// Whether the single VP8 padding byte may still be synthesized at end of input.
    private boolean zeroBytePending;

    /// Whether decoding has consumed data beyond the permitted padding byte.
    private boolean pastEof;

    /// Creates an uninitialized boolean decoder.
    LossyArithmeticDecoder() {
    }

    /// Initializes this decoder from one VP8 partition.
    ///
    /// The supplied buffer is sliced, so its position and limit are not modified.
    ///
    /// @param buffer the encoded partition bytes
    /// @throws WebPException if the partition is empty
    void init(ByteBuffer buffer) throws WebPException {
        ByteBuffer input = buffer.slice();
        if (!input.hasRemaining()) {
            throw new WebPException("Not enough VP8 partition data");
        }

        this.input = input;
        this.value = 0L;
        this.range = 255;
        this.bitCount = -8;
        this.zeroBytePending = true;
        this.pastEof = false;
    }

    /// Verifies that the preceding group of reads did not pass the end of the partition.
    ///
    /// @throws WebPException if decoding consumed data beyond the permitted VP8 padding byte
    void ensureNotPastEof() throws WebPException {
        if (pastEof) {
            throw new WebPException("Corrupt VP8 boolean bitstream");
        }
    }

    /// Reads one boolean using the supplied probability for a zero bit.
    ///
    /// @param probability the VP8 probability in the range `0` through `255`
    /// @return the decoded boolean
    boolean readBool(int probability) {
        return readBit(probability);
    }

    /// Reads one equiprobable flag.
    ///
    /// @return the decoded flag
    boolean readFlag() {
        return readBit(128);
    }

    /// Reads one equiprobable sign bit.
    ///
    /// @return `true` for a negative value
    boolean readSign() {
        return readBit(128);
    }

    /// Reads an unsigned literal in most-significant-bit-first order.
    ///
    /// @param bits the number of bits to read
    /// @return the decoded literal
    int readLiteral(int bits) {
        int value = 0;
        for (int i = 0; i < bits; i++) {
            value = (value << 1) | (readBit(128) ? 1 : 0);
        }
        return value;
    }

    /// Reads a conditionally present signed magnitude.
    ///
    /// @param bits the number of magnitude bits
    /// @return the decoded value, or `0` when the value is absent
    int readOptionalSignedValue(int bits) {
        if (!readBit(128)) {
            return 0;
        }
        int magnitude = readLiteral(bits);
        boolean negative = readBit(128);
        return negative ? -magnitude : magnitude;
    }

    /// Reads a symbol from a VP8 tree using one probability per internal node.
    ///
    /// @param tree the VP8 branch table, with two entries per internal node
    /// @param probabilities the zero-bit probabilities for the internal nodes
    /// @return the decoded symbol
    int readWithTree(int[] tree, int[] probabilities) {
        return readWithTree(tree, probabilities, 0);
    }

    /// Reads a symbol from a VP8 tree starting at a selected internal node.
    ///
    /// Positive branches identify another entry in the VP8 branch table, while non-positive
    /// branches encode a leaf as its negated symbol value.
    ///
    /// @param tree the VP8 branch table, with two entries per internal node
    /// @param probabilities the zero-bit probabilities for the internal nodes
    /// @param firstNode the first internal-node index to evaluate
    /// @return the decoded symbol
    int readWithTree(int[] tree, int[] probabilities, int firstNode) {
        int node = firstNode;
        while (true) {
            int branch = tree[node * 2 + (readBit(probabilities[node]) ? 1 : 0)];
            if (branch <= 0) {
                return -branch;
            }
            node = branch / 2;
        }
    }

    /// Reads and range-normalizes one arithmetic-coded bit.
    ///
    /// @param probability the probability for a zero bit
    /// @return the decoded bit
    private boolean readBit(int probability) {
        if (bitCount < 0) {
            if (input.remaining() >= Integer.BYTES) {
                int nextValue = (Byte.toUnsignedInt(input.get()) << 24)
                        | (Byte.toUnsignedInt(input.get()) << 16)
                        | (Byte.toUnsignedInt(input.get()) << 8)
                        | Byte.toUnsignedInt(input.get());
                value <<= 32;
                value |= Integer.toUnsignedLong(nextValue);
                bitCount += 32;
            } else {
                loadFromTailBytes();
                if (pastEof) {
                    return false;
                }
            }
        }

        long split = 1L + (((long) range - 1L) * probability >> 8);
        long bigSplit = split << bitCount;

        boolean result;
        if (Long.compareUnsigned(value, bigSplit) >= 0) {
            range -= (int) split;
            value -= bigSplit;
            result = true;
        } else {
            range = (int) split;
            result = false;
        }

        int shift = Math.max(0, Integer.numberOfLeadingZeros(range) - 24);
        range <<= shift;
        bitCount -= shift;
        return result;
    }

    /// Loads one tail byte or the single synthesized VP8 padding byte.
    private void loadFromTailBytes() {
        if (input.hasRemaining()) {
            value <<= 8;
            value |= Byte.toUnsignedInt(input.get());
            bitCount += 8;
        } else if (zeroBytePending) {
            zeroBytePending = false;
            value <<= 8;
            bitCount += 8;
        } else {
            pastEof = true;
        }
    }
}
