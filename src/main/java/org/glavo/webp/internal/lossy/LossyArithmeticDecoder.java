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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.WebPException;

import java.nio.ByteBuffer;

/// VP8 boolean arithmetic decoder.
///
/// The implementation mirrors the split fast-path / cold-path structure of the reference crate
/// so that the eventual Java VP8 decoder can stay structurally close to the source material.
@NotNullByDefault
final class LossyArithmeticDecoder {

    private ByteBuffer input = ByteBuffer.allocate(0);
    private State state = new State();
    private boolean zeroBytePending;
    private boolean pastEof;

    LossyArithmeticDecoder() {
        state.range = 255;
        state.bitCount = -8;
    }

    void init(ByteBuffer buffer) throws WebPException {
        ByteBuffer input = buffer.slice();
        if (!input.hasRemaining()) {
            throw new WebPException("Not enough VP8 partition data");
        }

        this.input = input;
        this.state = new State();
        this.state.range = 255;
        this.state.bitCount = -8;
        this.zeroBytePending = true;
        this.pastEof = false;
    }

    BitResultAccumulator startAccumulatedResult() {
        return new BitResultAccumulator();
    }

    <T> @Nullable T check(BitResultAccumulator accumulator, @Nullable T valueIfNotPastEof) throws WebPException {
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
            if (input.remaining() >= Integer.BYTES) {
                int value = (Byte.toUnsignedInt(input.get()) << 24)
                        | (Byte.toUnsignedInt(input.get()) << 16)
                        | (Byte.toUnsignedInt(input.get()) << 8)
                        | Byte.toUnsignedInt(input.get());
                state.value <<= 32;
                state.value |= Integer.toUnsignedLong(value);
                state.bitCount += 32;
            } else {
                loadFromTailBytes();
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

    private void loadFromTailBytes() {
        if (input.hasRemaining()) {
            state.value <<= 8;
            state.value |= Byte.toUnsignedInt(input.get());
            state.bitCount += 8;
        } else if (zeroBytePending) {
            zeroBytePending = false;
            state.value <<= 8;
            state.bitCount += 8;
        } else {
            pastEof = true;
        }
    }

    private boolean isPastEof() {
        return pastEof;
    }

    @NotNullByDefault
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

    @NotNullByDefault
    static final class BitResultAccumulator {
    }

    @NotNullByDefault
    private static final class State {
        long value;
        int range;
        int bitCount;
    }
}
