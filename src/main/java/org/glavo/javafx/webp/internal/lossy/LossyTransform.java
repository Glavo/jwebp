package org.glavo.javafx.webp.internal.lossy;

/// Inverse transform helpers used by the VP8 decoder.
final class LossyTransform {

    private static final long CONST1 = 20091;
    private static final long CONST2 = 35468;

    private LossyTransform() {
    }

    static void idct4x4(int[] block) {
        idct4x4(block, 0);
    }

    static void idct4x4(int[] block, int offset) {
        assert block.length >= 16;

        for (int i = 0; i < 4; i++) {
            long a1 = block[offset + i] + (long) block[offset + 8 + i];
            long b1 = block[offset + i] - (long) block[offset + 8 + i];

            long t1 = (block[offset + 4 + i] * CONST2) >> 16;
            long t2 = block[offset + 12 + i] + ((block[offset + 12 + i] * CONST1) >> 16);
            long c1 = t1 - t2;

            t1 = block[offset + 4 + i] + ((block[offset + 4 + i] * CONST1) >> 16);
            t2 = (block[offset + 12 + i] * CONST2) >> 16;
            long d1 = t1 + t2;

            block[offset + i] = (int) (a1 + d1);
            block[offset + 4 + i] = (int) (b1 + c1);
            block[offset + 12 + i] = (int) (a1 - d1);
            block[offset + 8 + i] = (int) (b1 - c1);
        }

        for (int i = 0; i < 4; i++) {
            int row = offset + 4 * i;
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

    static void iwht4x4(int[] block) {
        iwht4x4(block, 0);
    }

    static void iwht4x4(int[] block, int offset) {
        assert block.length >= 16;

        for (int i = 0; i < 4; i++) {
            int a1 = block[offset + i] + block[offset + 12 + i];
            int b1 = block[offset + 4 + i] + block[offset + 8 + i];
            int c1 = block[offset + 4 + i] - block[offset + 8 + i];
            int d1 = block[offset + i] - block[offset + 12 + i];

            block[offset + i] = a1 + b1;
            block[offset + 4 + i] = c1 + d1;
            block[offset + 8 + i] = a1 - b1;
            block[offset + 12 + i] = d1 - c1;
        }

        for (int row = offset; row < offset + 16; row += 4) {
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
