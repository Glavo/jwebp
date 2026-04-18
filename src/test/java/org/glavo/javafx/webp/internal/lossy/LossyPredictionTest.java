package org.glavo.javafx.webp.internal.lossy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/// Ports of [lossy/prediction.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossy/prediction.rs) tests.
final class LossyPredictionTest {

    @Test
    void avg2MatchesCeilingAverage() {
        for (int i = 0; i <= 255; i++) {
            for (int j = 0; j <= 255; j++) {
                int expected = (i + j + 1) >> 1;
                int actual = LossyPrediction.avg2(i, j);
                if (actual != expected) {
                    fail("avg2(" + i + ", " + j + "), expected " + expected + ", got " + actual + '.');
                }
            }
        }
    }

    @Test
    void avg2SpecificCases() {
        assertEquals(255, LossyPrediction.avg2(255, 255));
        assertEquals(1, LossyPrediction.avg2(1, 1));
        assertEquals(2, LossyPrediction.avg2(2, 1));
    }

    @Test
    void avg3MatchesUpstreamFormula() {
        for (int i = 0; i <= 255; i++) {
            for (int j = 0; j <= 255; j++) {
                for (int k = 0; k <= 255; k++) {
                    int expected = (i + 2 * j + k + 2) >> 2;
                    int actual = LossyPrediction.avg3(i, j, k);
                    if (actual != expected) {
                        fail("avg3(" + i + ", " + j + ", " + k + "), expected " + expected + ", got " + actual + '.');
                    }
                }
            }
        }
    }

    @Test
    void edgePixelsReadsCorrectNeighborhood() {
        byte[] image = u(
                5, 6, 7, 8, 9,
                4, 0, 0, 0, 0,
                3, 0, 0, 0, 0,
                2, 0, 0, 0, 0,
                1, 0, 0, 0, 0
        );

        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, LossyPrediction.edgePixels(image, 1, 1, 5));
    }

    @Test
    void topPixelsReadsCorrectNeighborhood() {
        byte[] image = u(
                1, 2, 3, 4, 5, 6, 7, 8,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0
        );

        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8}, LossyPrediction.topPixels(image, 0, 1, 8));
    }

    @Test
    void addResidueClampsIntoByteRange() {
        byte[] predictionBlock = u(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16
        );
        int[] residueBlock = {
                -1, -2, -3, -4,
                250, 249, 248, 250,
                -10, -18, -192, -17,
                -3, 15, 18, 9
        };
        byte[] expected = u(
                0, 0, 0, 0,
                255, 255, 255, 255,
                0, 0, 0, 0,
                10, 29, 33, 25
        );

        LossyPrediction.addResidue(predictionBlock, residueBlock, 0, 0, 4);
        assertArrayEquals(expected, predictionBlock);
    }

    @Test
    void predictBhepredFillsRowsFromLeftEdge() {
        byte[] expected = u(
                5, 0, 0, 0, 0,
                4, 4, 4, 4, 4,
                3, 3, 3, 3, 3,
                2, 2, 2, 2, 2,
                1, 1, 1, 1, 1
        );

        byte[] image = u(
                5, 0, 0, 0, 0,
                4, 0, 0, 0, 0,
                3, 0, 0, 0, 0,
                2, 0, 0, 0, 0,
                1, 0, 0, 0, 0
        );

        LossyPrediction.predictBhepred(image, 1, 1, 5);
        assertArrayEquals(expected, image);
    }

    @Test
    void predictBrdpredFillsDiagonalPattern() {
        byte[] expected = u(
                5, 6, 7, 8, 9,
                4, 5, 6, 7, 8,
                3, 4, 5, 6, 7,
                2, 3, 4, 5, 6,
                1, 2, 3, 4, 5
        );

        byte[] image = u(
                5, 6, 7, 8, 9,
                4, 0, 0, 0, 0,
                3, 0, 0, 0, 0,
                2, 0, 0, 0, 0,
                1, 0, 0, 0, 0
        );

        LossyPrediction.predictBrdpred(image, 1, 1, 5);
        assertArrayEquals(expected, image);
    }

    @Test
    void predictBldpredMatchesUpstreamAverages() {
        byte[] image = u(
                1, 2, 3, 4, 5, 6, 7, 8,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0
        );

        LossyPrediction.predictBldpred(image, 0, 1, 8);

        assertEquals(2, image[8] & 0xFF);
        assertEquals(3, image[9] & 0xFF);
        assertEquals(4, image[10] & 0xFF);
        assertEquals(5, image[11] & 0xFF);
        assertEquals(3, image[16] & 0xFF);
        assertEquals(4, image[17] & 0xFF);
        assertEquals(5, image[18] & 0xFF);
        assertEquals(6, image[19] & 0xFF);
        assertEquals(4, image[24] & 0xFF);
        assertEquals(5, image[25] & 0xFF);
        assertEquals(6, image[26] & 0xFF);
        assertEquals(7, image[27] & 0xFF);
        assertEquals(5, image[32] & 0xFF);
        assertEquals(6, image[33] & 0xFF);
        assertEquals(7, image[34] & 0xFF);
        assertEquals(8, image[35] & 0xFF);
    }

    @Test
    void predictBvepredMatchesUpstreamAverages() {
        byte[] image = u(
                1, 2, 3, 4, 5, 6, 7, 8, 9,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0
        );

        LossyPrediction.predictBvepred(image, 1, 1, 9);

        assertEquals(2, image[10] & 0xFF);
        assertEquals(3, image[11] & 0xFF);
        assertEquals(4, image[12] & 0xFF);
        assertEquals(5, image[13] & 0xFF);
        assertEquals(2, image[19] & 0xFF);
        assertEquals(3, image[20] & 0xFF);
        assertEquals(4, image[21] & 0xFF);
        assertEquals(5, image[22] & 0xFF);
        assertEquals(2, image[28] & 0xFF);
        assertEquals(3, image[29] & 0xFF);
        assertEquals(4, image[30] & 0xFF);
        assertEquals(5, image[31] & 0xFF);
        assertEquals(2, image[37] & 0xFF);
        assertEquals(3, image[38] & 0xFF);
        assertEquals(4, image[39] & 0xFF);
        assertEquals(5, image[40] & 0xFF);
    }

    private static byte[] u(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
