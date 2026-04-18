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
package org.glavo.javafx.webp.internal.lossy;

import org.glavo.javafx.webp.WebPException;

/// VP8 boolean arithmetic decoder.
///
/// The implementation mirrors the split fast-path / cold-path structure of the reference crate
/// so that the eventual Java VP8 decoder can stay structurally close to the source material.
final class LossyArithmeticDecoder {

    private static final int FINAL_BYTES_REMAINING_EOF = -0xE;

    private int[][] chunks = new int[0][];
    private State state = new State();
    private final int[] finalBytes = new int[3];
    private int finalBytesRemaining = FINAL_BYTES_REMAINING_EOF;

    LossyArithmeticDecoder() {
        state.range = 255;
        state.bitCount = -8;
    }

    void init(byte[] buffer) throws WebPException {
        if (buffer.length == 0) {
            throw new WebPException("Not enough VP8 partition data");
        }

        int fullChunks = buffer.length / 4;
        int remainder = buffer.length % 4;
        int chunkCount = fullChunks + (remainder == 0 ? 0 : 1);
        int[][] newChunks = new int[Math.max(fullChunks, 0)][4];

        int offset = 0;
        for (int i = 0; i < fullChunks; i++) {
            newChunks[i][0] = buffer[offset++] & 0xFF;
            newChunks[i][1] = buffer[offset++] & 0xFF;
            newChunks[i][2] = buffer[offset++] & 0xFF;
            newChunks[i][3] = buffer[offset++] & 0xFF;
        }

        this.chunks = newChunks;
        this.state = new State();
        this.state.range = 255;
        this.state.bitCount = -8;
        this.finalBytesRemaining = remainder == 0 ? 0 : remainder;
        if (remainder != 0) {
            for (int i = 0; i < remainder; i++) {
                finalBytes[i] = buffer[offset + i] & 0xFF;
            }
        }
    }

    BitResultAccumulator startAccumulatedResult() {
        return new BitResultAccumulator();
    }

    <T> T check(BitResultAccumulator accumulator, T valueIfNotPastEof) throws WebPException {
        if (isPastEof()) {
            throw new WebPException("Corrupt VP8 boolean bitstream");
        }
        return valueIfNotPastEof;
    }

    BitResult<Boolean> readBool(int probability) {
        return BitResult.ok(coldReadBit(probability));
    }

    BitResult<Boolean> readFlag() {
        return BitResult.ok(coldReadBit(128));
    }

    BitResult<Boolean> readSign() {
        return BitResult.ok(coldReadBit(128));
    }

    BitResult<Integer> readLiteral(int bits) {
        int value = 0;
        for (int i = 0; i < bits; i++) {
            value = (value << 1) | (coldReadBit(128) ? 1 : 0);
        }
        return BitResult.ok(value);
    }

    BitResult<Integer> readOptionalSignedValue(int bits) {
        if (!coldReadBit(128)) {
            return BitResult.ok(0);
        }
        int magnitude = readLiteral(bits).valueIfNotPastEof;
        boolean negative = coldReadBit(128);
        return BitResult.ok(negative ? -magnitude : magnitude);
    }

    BitResult<Integer> readWithTree(TreeNode[] tree) {
        return readWithTreeWithFirstNode(tree, tree[0]);
    }

    BitResult<Integer> readWithTreeWithFirstNode(TreeNode[] tree, TreeNode firstNode) {
        int index = firstNode.index & 0xFF;
        while (true) {
            TreeNode node = tree[index];
            boolean bit = coldReadBit(node.prob & 0xFF);
            int branch = bit ? (node.right & 0xFF) : (node.left & 0xFF);
            if (branch < tree.length) {
                index = branch;
            } else {
                return BitResult.ok(TreeNode.valueFromBranch(branch));
            }
        }
    }

    private boolean coldReadBit(int probability) {
        if (state.bitCount < 0) {
            if (state.chunkIndex < chunks.length) {
                int[] chunk = chunks[state.chunkIndex++];
                int value = (chunk[0] << 24) | (chunk[1] << 16) | (chunk[2] << 8) | chunk[3];
                state.value <<= 32;
                state.value |= Integer.toUnsignedLong(value);
                state.bitCount += 32;
            } else {
                loadFromFinalBytes();
                if (isPastEof()) {
                    return false;
                }
            }
        }

        long split = 1L + (((long) state.range - 1L) * probability >> 8);
        long bigSplit = split << state.bitCount;

        boolean result;
        if (Long.compareUnsigned(state.value, bigSplit) >= 0) {
            state.range -= (int) split;
            state.value -= bigSplit;
            result = true;
        } else {
            state.range = (int) split;
            result = false;
        }

        int shift = Math.max(0, Integer.numberOfLeadingZeros(state.range) - 24);
        state.range <<= shift;
        state.bitCount -= shift;
        return result;
    }

    private void loadFromFinalBytes() {
        if (finalBytesRemaining > 0) {
            finalBytesRemaining--;
            int value = finalBytes[0];
            finalBytes[0] = finalBytes[1];
            finalBytes[1] = finalBytes[2];
            state.value <<= 8;
            state.value |= value;
            state.bitCount += 8;
        } else if (finalBytesRemaining == 0) {
            finalBytesRemaining--;
            state.value <<= 8;
            state.bitCount += 8;
        } else {
            finalBytesRemaining = FINAL_BYTES_REMAINING_EOF;
        }
    }

    private boolean isPastEof() {
        return finalBytesRemaining == FINAL_BYTES_REMAINING_EOF;
    }

    static final class BitResult<T> {
        final T valueIfNotPastEof;

        private BitResult(T valueIfNotPastEof) {
            this.valueIfNotPastEof = valueIfNotPastEof;
        }

        static <T> BitResult<T> ok(T value) {
            return new BitResult<>(value);
        }

        T orAccumulate(BitResultAccumulator accumulator) {
            return valueIfNotPastEof;
        }
    }

    static final class BitResultAccumulator {
    }

    private static final class State {
        int chunkIndex;
        long value;
        int range;
        int bitCount;
    }
}
