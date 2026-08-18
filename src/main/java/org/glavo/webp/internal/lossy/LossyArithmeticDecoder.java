// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.glavo.webp.internal.ArrayUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import org.glavo.webp.WebPException;

/// Decodes VP8 boolean-coded header and coefficient partitions.
@NotNullByDefault
final class LossyArithmeticDecoder {

    /// Shared zero-capacity input used before this decoder is initialized.
    private static final byte @Unmodifiable [] EMPTY_INPUT = ArrayUtils.EMPTY_BYTE_ARRAY;

    /// Remaining bytes in the current VP8 boolean-coded partition.
    private byte[] input = EMPTY_INPUT;

    /// Index of the next encoded byte.
    private int inputPosition;

    /// Exclusive end index of the encoded partition.
    private int inputLimit;

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

    /// Initializes this decoder from one VP8 partition range.
    ///
    /// @param input the array containing the encoded partition
    /// @param offset the first encoded byte
    /// @param length the partition length in bytes
    /// @throws WebPException if the partition is empty
    void init(byte[] input, int offset, int length) throws WebPException {
        if (length <= 0) {
            throw new WebPException("Not enough VP8 partition data");
        }
        if (offset < 0 || offset > input.length - length) {
            throw new IndexOutOfBoundsException(
                    "VP8 partition range is outside the input: " + offset + " + " + length
            );
        }

        this.input = input;
        this.inputPosition = offset;
        this.inputLimit = offset + length;
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
        return readHalfBit();
    }

    /// Applies one equiprobable sign bit to a non-negative magnitude.
    ///
    /// @param magnitude the non-negative magnitude
    /// @return the signed value
    int readSigned(int magnitude) {
        int mask = readHalfBitMask();
        return (magnitude ^ mask) - mask;
    }

    /// Reads an unsigned literal in most-significant-bit-first order.
    ///
    /// @param bits the number of bits to read
    /// @return the decoded literal
    int readLiteral(int bits) {
        int value = 0;
        for (int i = 0; i < bits; i++) {
            value = (value << 1) | (readHalfBit() ? 1 : 0);
        }
        return value;
    }

    /// Reads a conditionally present signed magnitude.
    ///
    /// @param bits the number of magnitude bits
    /// @return the decoded value, or `0` when the value is absent
    int readOptionalSignedValue(int bits) {
        if (!readHalfBit()) {
            return 0;
        }
        int magnitude = readLiteral(bits);
        boolean negative = readHalfBit();
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
        return readWithTree(tree, probabilities, 0, firstNode);
    }

    /// Reads a symbol from a VP8 tree using a probability range within a flat array.
    ///
    /// @param tree the VP8 branch table, with two entries per internal node
    /// @param probabilities the array containing zero-bit probabilities
    /// @param probabilityOffset the array index corresponding to tree node zero
    /// @param firstNode the first internal-node index to evaluate
    /// @return the decoded symbol
    int readWithTree(int[] tree, int[] probabilities, int probabilityOffset, int firstNode) {
        int node = firstNode;
        while (true) {
            int branch = tree[node * 2 + (readBit(probabilities[probabilityOffset + node]) ? 1 : 0)];
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
            if (inputLimit - inputPosition >= Integer.BYTES) {
                byte[] input = this.input;
                int position = inputPosition;
                int nextValue = ArrayUtils.getIntBE(input, position);
                inputPosition = position + Integer.BYTES;
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

        // The normalized range is at most 255, so the product fits in an int.
        int split = 1 + ((range - 1) * probability >> 8);
        int activeValue = (int) (value >>> bitCount);

        boolean result;
        if (activeValue >= split) {
            range -= split;
            value -= (long) split << bitCount;
            result = true;
        } else {
            range = split;
            result = false;
        }

        // Either branch leaves range in 1 through 255, making the shift 0 through 7.
        int shift = Integer.numberOfLeadingZeros(range) - 24;
        range <<= shift;
        bitCount -= shift;
        return result;
    }

    /// Reads one equiprobable bit using its single-step normalization rule.
    ///
    /// @return the decoded bit
    private boolean readHalfBit() {
        return readHalfBitMask() != 0;
    }

    /// Reads one equiprobable bit and returns its sign-extension mask.
    ///
    /// @return `-1` for a one bit, or `0` for a zero bit
    private int readHalfBitMask() {
        if (bitCount < 0) {
            if (inputLimit - inputPosition >= Integer.BYTES) {
                byte[] input = this.input;
                int position = inputPosition;
                int nextValue = ArrayUtils.getIntBE(input, position);
                inputPosition = position + Integer.BYTES;
                value <<= 32;
                value |= Integer.toUnsignedLong(nextValue);
                bitCount += 32;
            } else {
                loadFromTailBytes();
                if (pastEof) {
                    return 0;
                }
            }
        }

        int split = (range + 1) >>> 1;
        int activeValue = (int) (value >>> bitCount);
        int mask = (split - 1 - activeValue) >> 31;
        int nextRange = (split & ~mask) | ((range - split) & mask);
        int shift = 1 - (nextRange >>> 7);
        range = nextRange << shift;
        value -= (long) (split & mask) << bitCount;
        bitCount -= shift;
        return mask;
    }

    /// Loads one tail byte or the single synthesized VP8 padding byte.
    private void loadFromTailBytes() {
        if (inputPosition < inputLimit) {
            value <<= 8;
            value |= Byte.toUnsignedInt(input[inputPosition++]);
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
