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
package org.glavo.webp.internal.lossless;

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Tests VP8L inverse transforms independently of entropy decoding.
@NotNullByDefault
final class LosslessTransformsTest {

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
        }
    }
}
