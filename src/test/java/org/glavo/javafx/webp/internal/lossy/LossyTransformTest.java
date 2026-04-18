package org.glavo.javafx.webp.internal.lossy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Ports of [lossy/transform.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossy/transform.rs) tests.
final class LossyTransformTest {

    @Test
    void dctInverseRoundTripsOriginalBlock() {
        int[] block = {
                38, 6, 210, 107,
                42, 125, 185, 151,
                241, 224, 125, 233,
                227, 8, 57, 96
        };

        int[] dctBlock = block.clone();
        dct4x4(dctBlock);

        int[] inverseDctBlock = dctBlock.clone();
        LossyTransform.idct4x4(inverseDctBlock);

        assertArrayEquals(block, inverseDctBlock);
    }

    private static void dct4x4(int[] block) {
        for (int i = 0; i < 4; i++) {
            long a = (block[i * 4] + (long) block[i * 4 + 3]) * 8;
            long b = (block[i * 4 + 1] + (long) block[i * 4 + 2]) * 8;
            long c = (block[i * 4 + 1] - (long) block[i * 4 + 2]) * 8;
            long d = (block[i * 4] - (long) block[i * 4 + 3]) * 8;

            block[i * 4] = (int) (a + b);
            block[i * 4 + 2] = (int) (a - b);
            block[i * 4 + 1] = (int) ((c * 2217 + d * 5352 + 14500) >> 12);
            block[i * 4 + 3] = (int) ((d * 2217 - c * 5352 + 7500) >> 12);
        }

        for (int i = 0; i < 4; i++) {
            long a = block[i] + (long) block[i + 12];
            long b = block[i + 4] + (long) block[i + 8];
            long c = block[i + 4] - (long) block[i + 8];
            long d = block[i] - (long) block[i + 12];

            block[i] = (int) ((a + b + 7) >> 4);
            block[i + 8] = (int) ((a - b + 7) >> 4);
            block[i + 4] = (int) (((c * 2217 + d * 5352 + 12000) >> 16) + (d != 0 ? 1 : 0));
            block[i + 12] = (int) ((d * 2217 - c * 5352 + 51000) >> 16);
        }
    }
}
