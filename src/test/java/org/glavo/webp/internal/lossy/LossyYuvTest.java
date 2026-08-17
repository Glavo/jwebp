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

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Ports of [lossy/yuv.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossy/yuv.rs) tests.
@NotNullByDefault
final class LossyYuvTest {

    /// Verifies fancy chroma interpolation against explicitly upsampled planes.
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

    /// Verifies simple ARGB conversion with padded strides, shared chroma rows, and an odd width.
    @Test
    void simpleArgbGridHandlesPaddedOddDimensions() {
        int width = 5;
        int height = 3;
        int bufferWidth = 16;
        int chromaWidth = (width + 1) / 2;
        int chromaStride = bufferWidth / 2;

        byte[] yBuffer = new byte[bufferWidth * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                yBuffer[y * bufferWidth + x] = (byte) (y * 53 + x * 29 + 17);
            }
        }

        int chromaHeight = (height + 1) / 2;
        byte[] uBuffer = new byte[chromaStride * chromaHeight];
        byte[] vBuffer = new byte[chromaStride * chromaHeight];
        for (int y = 0; y < chromaHeight; y++) {
            for (int x = 0; x < chromaWidth; x++) {
                uBuffer[y * chromaStride + x] = (byte) (y * 71 + x * 37 + 43);
                vBuffer[y * chromaStride + x] = (byte) (y * 61 + x * 41 + 97);
            }
        }

        int[] actual = new int[width * height];
        LossyYuv.fillArgbBufferSimple(
                actual,
                yBuffer,
                uBuffer,
                vBuffer,
                width,
                chromaWidth,
                bufferWidth
        );

        int[] expected = new int[actual.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int luma = yBuffer[y * bufferWidth + x] & 0xFF;
                int chromaIndex = (y >>> 1) * chromaStride + (x >>> 1);
                int u = uBuffer[chromaIndex] & 0xFF;
                int v = vBuffer[chromaIndex] & 0xFF;
                expected[y * width + x] = Argb.opaque(
                        LossyYuv.yuvToR(luma, v),
                        LossyYuv.yuvToG(luma, u, v),
                        LossyYuv.yuvToB(luma, u)
                );
            }
        }

        assertArrayEquals(expected, actual);
    }

    /// Verifies that direct and array destinations produce identical fancy-upsampled pixels.
    @Test
    void fancyArgbDirectBufferMatchesArrayDestination() {
        int width = 4;
        int height = 4;
        byte[] yBuffer = u(
                77, 162, 202, 185,
                28, 13, 199, 182,
                135, 147, 164, 135,
                66, 27, 171, 130
        );
        byte[] uBuffer = u(34, 101, 123, 163);
        byte[] vBuffer = u(97, 167, 149, 23);

        int[] expected = new int[width * height];
        LossyYuv.fillArgbBufferFancy(expected, yBuffer, uBuffer, vBuffer, width, height, width);

        IntBuffer direct = ByteBuffer.allocateDirect(expected.length * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        LossyYuv.fillArgbBufferFancy(direct, yBuffer, uBuffer, vBuffer, width, height, width);

        int[] actual = new int[expected.length];
        direct.get(actual);
        assertArrayEquals(expected, actual);
    }

    /// Verifies individual conversion helpers against the upstream fixed-point constants.
    @Test
    void yuvConversionsMatchUpstreamConstants() {
        int y = 203;
        int u = 40;
        int v = 42;

        assertEquals(80, LossyYuv.yuvToR(y, v));
        assertEquals(255, LossyYuv.yuvToG(y, u, v));
        assertEquals(40, LossyYuv.yuvToB(y, u));
    }

    /// Converts unsigned integer literals to their byte representation.
    ///
    /// @param values the unsigned values to convert
    /// @return the byte representation
    private static byte[] u(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
