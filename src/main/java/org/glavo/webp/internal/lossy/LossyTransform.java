// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.jetbrains.annotations.NotNullByDefault;

/// Inverse transform helpers used by the VP8 decoder.
@NotNullByDefault
final class LossyTransform {

    /// First fixed-point inverse-DCT multiplier.
    private static final long CONST1 = 20091;

    /// Second fixed-point inverse-DCT multiplier.
    private static final long CONST2 = 35468;

    /// Prevents instantiation.
    private LossyTransform() {
    }

    /// Applies the inverse discrete cosine transform to the first 4-by-4 block.
    ///
    /// @param block the mutable coefficient block
    static void idct4x4(int[] block) {
        idct4x4(block, 0);
    }

    /// Applies the inverse discrete cosine transform to one 4-by-4 block.
    ///
    /// @param block the array containing the mutable coefficient block
    /// @param offset the index of the first of 16 consecutive coefficients
    static void idct4x4(int[] block, int offset) {
        assert offset >= 0 && offset <= block.length - 16;

        for (int column = offset, end = offset + 4; column < end; column++) {
            long x0 = block[column];
            long x1 = block[column + 4];
            long x2 = block[column + 8];
            long x3 = block[column + 12];

            long a1 = x0 + x2;
            long b1 = x0 - x2;

            long t1 = (x1 * CONST2) >> 16;
            long t2 = x3 + ((x3 * CONST1) >> 16);
            long c1 = t1 - t2;

            t1 = x1 + ((x1 * CONST1) >> 16);
            t2 = (x3 * CONST2) >> 16;
            long d1 = t1 + t2;

            block[column] = (int) (a1 + d1);
            block[column + 4] = (int) (b1 + c1);
            block[column + 12] = (int) (a1 - d1);
            block[column + 8] = (int) (b1 - c1);
        }

        for (int row = offset, end = offset + 16; row < end; row += 4) {
            long a1 = block[row] + (long) block[row + 2];
            long b1 = block[row] - (long) block[row + 2];

            long t1 = (block[row + 1] * CONST2) >> 16;
            long t2 = block[row + 3] + ((block[row + 3] * CONST1) >> 16);
            long c1 = t1 - t2;

            t1 = block[row + 1] + ((block[row + 1] * CONST1) >> 16);
            t2 = (block[row + 3] * CONST2) >> 16;
            long d1 = t1 + t2;

            block[row] = (int) ((a1 + d1 + 4) >> 3);
            block[row + 3] = (int) ((a1 - d1 + 4) >> 3);
            block[row + 1] = (int) ((b1 + c1 + 4) >> 3);
            block[row + 2] = (int) ((b1 - c1 + 4) >> 3);
        }
    }

    /// Applies the inverse Walsh-Hadamard transform to the first 4-by-4 block.
    ///
    /// @param block the mutable coefficient block
    static void iwht4x4(int[] block) {
        iwht4x4(block, 0);
    }

    /// Applies the inverse Walsh-Hadamard transform to one 4-by-4 block.
    ///
    /// @param block the array containing the mutable coefficient block
    /// @param offset the index of the first of 16 consecutive coefficients
    static void iwht4x4(int[] block, int offset) {
        assert offset >= 0 && offset <= block.length - 16;

        for (int column = offset, end = offset + 4; column < end; column++) {
            int x0 = block[column];
            int x1 = block[column + 4];
            int x2 = block[column + 8];
            int x3 = block[column + 12];

            int a1 = x0 + x3;
            int b1 = x1 + x2;
            int c1 = x1 - x2;
            int d1 = x0 - x3;

            block[column] = a1 + b1;
            block[column + 4] = c1 + d1;
            block[column + 8] = a1 - b1;
            block[column + 12] = d1 - c1;
        }

        for (int row = offset, end = offset + 16; row < end; row += 4) {
            int a1 = block[row] + block[row + 3];
            int b1 = block[row + 1] + block[row + 2];
            int c1 = block[row + 1] - block[row + 2];
            int d1 = block[row] - block[row + 3];

            int a2 = a1 + b1;
            int b2 = c1 + d1;
            int c2 = a1 - b1;
            int d2 = d1 - c1;

            block[row] = (a2 + 3) >> 3;
            block[row + 1] = (b2 + 3) >> 3;
            block[row + 2] = (c2 + 3) >> 3;
            block[row + 3] = (d2 + 3) >> 3;
        }
    }
}
