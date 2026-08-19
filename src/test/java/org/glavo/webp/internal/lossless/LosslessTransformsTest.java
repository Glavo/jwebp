// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Tests VP8L inverse transforms independently of entropy decoding.
@NotNullByDefault
final class LosslessTransformsTest {

    /// Verifies packed R/B updates against independent channel arithmetic on heap and direct data.
    @Test
    void updatesColorChannelsIndependently() {
        int[] source = new int[4096];
        Random random = new Random(0x52_45_44_42_4C_55_45L);
        for (int index = 0; index < source.length; index++) {
            source[index] = random.nextInt();
        }

        int transform = 0x00D3_B17F;
        int[] expectedColor = source.clone();
        for (int index = 0; index < expectedColor.length; index++) {
            int value = expectedColor[index];
            int green = Argb.green(value);
            int red = Argb.red(value) + ((byte) Argb.blue(transform) * (byte) green >> 5);
            int blue = Argb.blue(value)
                    + ((byte) Argb.green(transform) * (byte) green >> 5)
                    + ((byte) Argb.red(transform) * (byte) red >> 5);
            expectedColor[index] = Argb.pack(Argb.alpha(value), red, green, blue);
        }

        int[] heapColor = source.clone();
        LosslessTransforms.applyColorTransform(
                heapColor,
                source.length,
                12,
                compactColorTransform(transform)
        );
        assertArrayEquals(expectedColor, heapColor);
        assertArrayEquals(
                expectedColor,
                applyDirectColorTransform(source, transform)
        );

        int[] expectedSubtractGreen = source.clone();
        for (int index = 0; index < expectedSubtractGreen.length; index++) {
            int value = expectedSubtractGreen[index];
            int green = Argb.green(value);
            expectedSubtractGreen[index] = Argb.pack(
                    Argb.alpha(value),
                    Argb.red(value) + green,
                    green,
                    Argb.blue(value) + green
            );
        }

        int[] heapSubtractGreen = source.clone();
        LosslessTransforms.applySubtractGreenTransform(heapSubtractGreen);
        assertArrayEquals(expectedSubtractGreen, heapSubtractGreen);

        IntBuffer directSubtractGreen = directBuffer(source);
        LosslessIntBufferTransforms.applySubtractGreenTransform(directSubtractGreen);
        int[] directSubtractGreenResult = new int[source.length];
        directSubtractGreen.get(0, directSubtractGreenResult);
        assertArrayEquals(expectedSubtractGreen, directSubtractGreenResult);
    }

    /// Verifies in-place expansion for every packing width, partial final blocks, and a full table.
    @Test
    void expandsColorTablesInPlace() {
        int width = 19;
        int height = 3;

        for (int tableSize : new int[]{1, 2, 3, 4, 5, 16, 17, 256}) {
            int bits = tableSize > 16 ? 0 : (tableSize <= 2 ? 3 : (tableSize <= 4 ? 2 : 1));
            int pixelsPerPackedByte = 1 << bits;
            int bitsPerEntry = 8 / pixelsPerPackedByte;
            int mask = (1 << bitsPerEntry) - 1;
            int packedWidth = (width + pixelsPerPackedByte - 1) / pixelsPerPackedByte;

            int[] tableData = new int[tableSize];
            for (int index = 0; index < tableSize; index++) {
                tableData[index] = Argb.opaque(index * 3, index * 5, index * 7);
            }

            int[] imageData = new int[width * height];
            Arrays.fill(imageData, 0xDEAD_BEEF);
            int[] expected = new int[imageData.length];
            for (int y = 0; y < height; y++) {
                for (int block = 0; block < packedWidth; block++) {
                    int packed = 0;
                    for (int pixel = 0; pixel < pixelsPerPackedByte; pixel++) {
                        int x = block * pixelsPerPackedByte + pixel;
                        if (x < width) {
                            int tableIndex = (x + y) & mask;
                            packed |= tableIndex << (pixel * bitsPerEntry);
                            expected[y * width + x] = tableIndex < tableSize ? tableData[tableIndex] : 0;
                        }
                    }
                    imageData[y * packedWidth + block] = packed << 8;
                }
            }

            LosslessTransforms.applyColorIndexingTransform(imageData, width, height, tableSize, tableData);

            assertArrayEquals(expected, imageData, "tableSize=" + tableSize);

            IntBuffer direct = ByteBuffer.allocateDirect(imageData.length * Integer.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            direct.put(createPackedColorIndexes(
                    width,
                    height,
                    tableSize,
                    pixelsPerPackedByte,
                    bitsPerEntry,
                    mask
            ));
            direct.clear();
            LosslessIntBufferTransforms.applyColorIndexingTransform(
                    direct,
                    width,
                    height,
                    tableSize,
                    tableData
            );
            int[] directActual = new int[expected.length];
            direct.get(directActual);
            assertArrayEquals(expected, directActual, "direct tableSize=" + tableSize);
        }
    }

    /// Creates packed color indexes in a full-size transform destination.
    ///
    /// @param width the expanded image width
    /// @param height the image height
    /// @param tableSize the number of table entries
    /// @param pixelsPerPackedByte the number of indexes in one packed byte
    /// @param bitsPerEntry the number of bits per packed index
    /// @param mask the packed-index mask
    /// @return the packed transform pixels
    private static int[] createPackedColorIndexes(
            int width,
            int height,
            int tableSize,
            int pixelsPerPackedByte,
            int bitsPerEntry,
            int mask
    ) {
        int packedWidth = (width + pixelsPerPackedByte - 1) / pixelsPerPackedByte;
        int[] imageData = new int[width * height];
        Arrays.fill(imageData, 0xDEAD_BEEF);
        for (int y = 0; y < height; y++) {
            for (int block = 0; block < packedWidth; block++) {
                int packed = 0;
                for (int pixel = 0; pixel < pixelsPerPackedByte; pixel++) {
                    int x = block * pixelsPerPackedByte + pixel;
                    if (x < width) {
                        int tableIndex = (x + y) & mask;
                        packed |= tableIndex << (pixel * bitsPerEntry);
                    }
                }
                imageData[y * packedWidth + block] = packed << 8;
            }
        }
        return imageData;
    }

    /// Applies the direct-buffer color transform and returns its pixels.
    ///
    /// @param source the source pixels
    /// @param transform the packed transform multipliers
    /// @return the transformed pixels
    private static int[] applyDirectColorTransform(int[] source, int transform) {
        IntBuffer direct = directBuffer(source);
        LosslessIntBufferTransforms.applyColorTransform(
                direct,
                source.length,
                12,
                compactColorTransform(transform)
        );
        int[] result = new int[source.length];
        direct.get(0, result);
        return result;
    }

    /// Compacts one packed transform pixel into its three signed multipliers.
    ///
    /// @param transform the packed transform pixel
    /// @return the red-to-blue, green-to-blue and green-to-red multipliers
    private static byte[] compactColorTransform(int transform) {
        return new byte[]{
                (byte) Argb.red(transform),
                (byte) Argb.green(transform),
                (byte) Argb.blue(transform)
        };
    }

    /// Creates a native-order direct integer buffer containing the supplied pixels.
    ///
    /// @param source the source pixels
    /// @return the position-zero direct buffer
    private static IntBuffer directBuffer(int[] source) {
        IntBuffer direct = ByteBuffer.allocateDirect(source.length * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        direct.put(source);
        direct.clear();
        return direct;
    }
}
