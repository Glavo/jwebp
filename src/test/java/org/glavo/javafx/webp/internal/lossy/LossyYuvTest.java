package org.glavo.javafx.webp.internal.lossy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Ports of [lossy/yuv.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossy/yuv.rs) tests.
final class LossyYuvTest {

    @Test
    void fancyGridMatchesExplicitUpsampling() {
        byte[] yBuffer = u(
                77, 162, 202, 185,
                28, 13, 199, 182,
                135, 147, 164, 135,
                66, 27, 171, 130
        );

        byte[] uBuffer = u(
                34, 101,
                123, 163
        );

        byte[] vBuffer = u(
                97, 167,
                149, 23
        );

        byte[] rgbBuffer = new byte[16 * 3];
        LossyYuv.fillRgbBufferFancy(rgbBuffer, yBuffer, uBuffer, vBuffer, 4, 4, 4, 3);

        byte[] upsampledUBuffer = u(
                34, 51, 84, 101,
                56, 71, 101, 117,
                101, 112, 136, 148,
                123, 133, 153, 163
        );

        byte[] upsampledVBuffer = u(
                97, 115, 150, 167,
                110, 115, 126, 131,
                136, 117, 78, 59,
                149, 118, 55, 23
        );

        byte[] expected = new byte[16 * 3];
        for (int i = 0; i < 16; i++) {
            int y = yBuffer[i] & 0xFF;
            int u = upsampledUBuffer[i] & 0xFF;
            int v = upsampledVBuffer[i] & 0xFF;
            expected[i * 3] = (byte) LossyYuv.yuvToR(y, v);
            expected[i * 3 + 1] = (byte) LossyYuv.yuvToG(y, u, v);
            expected[i * 3 + 2] = (byte) LossyYuv.yuvToB(y, u);
        }

        assertArrayEquals(expected, rgbBuffer);
    }

    @Test
    void yuvConversionsMatchUpstreamConstants() {
        int y = 203;
        int u = 40;
        int v = 42;

        assertEquals(80, LossyYuv.yuvToR(y, v));
        assertEquals(255, LossyYuv.yuvToG(y, u, v));
        assertEquals(40, LossyYuv.yuvToB(y, u));
    }

    private static byte[] u(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
