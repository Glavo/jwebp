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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.internal.Argb;

/// Reverse transforms for VP8L decoded pixels.
@NotNullByDefault
public final class LosslessTransforms {

    /// Predictor transform tag.
    public static final int PREDICTOR = 0;

    /// Color transform tag.
    public static final int COLOR = 1;

    /// Subtract-green transform tag.
    public static final int SUBTRACT_GREEN = 2;

    /// Color indexing transform tag.
    public static final int COLOR_INDEXING = 3;

    private LosslessTransforms() {
    }

    /// Encoded reverse-transform description.
    @NotNullByDefault
    public static final class Transform {
        final int kind;
        final int sizeBits;
        final int @Nullable [] data;
        final int tableSize;

        private Transform(int kind, int sizeBits, int @Nullable [] data, int tableSize) {
            this.kind = kind;
            this.sizeBits = sizeBits;
            this.data = data;
            this.tableSize = tableSize;
        }

        public static Transform predictor(int sizeBits, int[] data) {
            return new Transform(PREDICTOR, sizeBits, data, 0);
        }

        public static Transform color(int sizeBits, int[] data) {
            return new Transform(COLOR, sizeBits, data, 0);
        }

        public static Transform subtractGreen() {
            return new Transform(SUBTRACT_GREEN, 0, null, 0);
        }

        public static Transform colorIndexing(int tableSize, int[] data) {
            return new Transform(COLOR_INDEXING, 0, data, tableSize);
        }
    }

    /// Returns the lossless block subsample size used by transform metadata planes.
    ///
    /// @param size the full image dimension
    /// @param bits the log2 block size
    /// @return the metadata plane size
    public static int subsampleSize(int size, int bits) {
        return (size + (1 << bits) - 1) >> bits;
    }

    /// Applies the predictor transform.
    public static void applyPredictorTransform(int[] imageData, int width, int height, int sizeBits, int[] predictorData) {
        int blockXSize = subsampleSize(width, sizeBits);
        imageData[0] = Argb.add(imageData[0], 0xFF00_0000);
        applyPredictorTransform1(imageData, 1, width, width);

        for (int y = 1; y < height; y++) {
            int rowStart = y * width;
            imageData[rowStart] = Argb.add(imageData[rowStart], imageData[rowStart - width]);
        }

        for (int y = 1; y < height; y++) {
            for (int blockX = 0; blockX < blockXSize; blockX++) {
                int blockIndex = (y >> sizeBits) * blockXSize + blockX;
                int predictor = Argb.green(predictorData[blockIndex]);
                int startIndex = y * width + Math.max(blockX << sizeBits, 1);
                int endIndex = y * width + Math.min((blockX + 1) << sizeBits, width);

                switch (predictor) {
                    case 0 -> applyPredictorTransform0(imageData, startIndex, endIndex);
                    case 1 -> applyPredictorTransform1(imageData, startIndex, endIndex, width);
                    case 2 -> applyPredictorTransform2(imageData, startIndex, endIndex, width);
                    case 3 -> applyPredictorTransform3(imageData, startIndex, endIndex, width);
                    case 4 -> applyPredictorTransform4(imageData, startIndex, endIndex, width);
                    case 5 -> applyPredictorTransform5(imageData, startIndex, endIndex, width);
                    case 6 -> applyPredictorTransform6(imageData, startIndex, endIndex, width);
                    case 7 -> applyPredictorTransform7(imageData, startIndex, endIndex, width);
                    case 8 -> applyPredictorTransform8(imageData, startIndex, endIndex, width);
                    case 9 -> applyPredictorTransform9(imageData, startIndex, endIndex, width);
                    case 10 -> applyPredictorTransform10(imageData, startIndex, endIndex, width);
                    case 11 -> applyPredictorTransform11(imageData, startIndex, endIndex, width);
                    case 12 -> applyPredictorTransform12(imageData, startIndex, endIndex, width);
                    case 13 -> applyPredictorTransform13(imageData, startIndex, endIndex, width);
                    default -> {
                    }
                }
            }
        }
    }

    /// Applies the color transform.
    public static void applyColorTransform(int[] imageData, int width, int sizeBits, int[] transformData) {
        int blockXSize = subsampleSize(width, sizeBits);
        for (int y = 0; y < imageData.length / width; y++) {
            int rowTransformStart = (y >> sizeBits) * blockXSize;
            for (int block = 0; block < blockXSize; block++) {
                int transform = transformData[rowTransformStart + block];
                int redToBlue = Argb.red(transform);
                int greenToBlue = Argb.green(transform);
                int greenToRed = Argb.blue(transform);

                int pixelStart = y * width + (block << sizeBits);
                int pixelEnd = Math.min(y * width + ((block + 1) << sizeBits), (y + 1) * width);
                for (int pixel = pixelStart; pixel < pixelEnd; pixel++) {
                    int value = imageData[pixel];
                    int green = Argb.green(value);
                    int red = Argb.red(value) + colorTransformDelta((byte) greenToRed, (byte) green);
                    int blue = Argb.blue(value)
                            + colorTransformDelta((byte) greenToBlue, (byte) green)
                            + colorTransformDelta((byte) redToBlue, (byte) red);
                    imageData[pixel] = Argb.pack(Argb.alpha(value), red, green, blue);
                }
            }
        }
    }

    /// Applies the subtract-green transform.
    public static void applySubtractGreenTransform(int[] imageData) {
        for (int index = 0; index < imageData.length; index++) {
            int value = imageData[index];
            int green = Argb.green(value);
            imageData[index] = Argb.pack(
                    Argb.alpha(value),
                    Argb.red(value) + green,
                    green,
                    Argb.blue(value) + green
            );
        }
    }

    /// Applies the color indexing transform.
    public static void applyColorIndexingTransform(int[] imageData, int width, int height, int tableSize, int[] tableData) {
        if (tableSize > 16) {
            int[] table = new int[256];
            System.arraycopy(tableData, 0, table, 0, tableSize);
            for (int index = 0; index < imageData.length; index++) {
                imageData[index] = table[Argb.green(imageData[index])];
            }
            return;
        }

        int bits = tableSize <= 2 ? 3 : (tableSize <= 4 ? 2 : 1);
        int pixelsPerPackedByte = 1 << bits;
        int bitsPerEntry = 8 / pixelsPerPackedByte;
        int mask = (1 << bitsPerEntry) - 1;
        int packedImageWidth = (width + pixelsPerPackedByte - 1) / pixelsPerPackedByte;
        int[] packedIndices = new int[packedImageWidth];

        for (int y = height - 1; y >= 0; y--) {
            int packedOffset = y * packedImageWidth;
            for (int block = 0; block < packedImageWidth; block++) {
                packedIndices[block] = Argb.green(imageData[packedOffset + block]);
            }

            int outOffset = y * width;
            for (int block = 0; block < packedImageWidth; block++) {
                int packed = packedIndices[block];
                for (int pixel = 0; pixel < pixelsPerPackedByte; pixel++) {
                    int x = block * pixelsPerPackedByte + pixel;
                    if (x >= width) {
                        break;
                    }
                    int tableIndex = (packed >> (pixel * bitsPerEntry)) & mask;
                    if (tableIndex < tableSize) {
                        imageData[outOffset + x] = tableData[tableIndex];
                    } else {
                        imageData[outOffset + x] = 0;
                    }
                }
            }
        }
    }

    private static void applyPredictorTransform0(int[] imageData, int start, int end) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], 0xFF00_0000);
        }
    }

    private static void applyPredictorTransform1(int[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], imageData[i - 1]);
        }
    }

    private static void applyPredictorTransform2(int[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], imageData[i - width]);
        }
    }

    private static void applyPredictorTransform3(int[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], imageData[i - width + 1]);
        }
    }

    private static void applyPredictorTransform4(int[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], imageData[i - width - 1]);
        }
    }

    private static void applyPredictorTransform5(int[] imageData, int start, int end, int width) {
        int prev = imageData[start - 1];
        for (int i = start; i < end; i++) {
            int topRight = imageData[i - width + 1];
            int top = imageData[i - width];
            prev = Argb.add(imageData[i], Argb.average2(Argb.average2(prev, topRight), top));
            imageData[i] = prev;
        }
    }

    private static void applyPredictorTransform6(int[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], Argb.average2(imageData[i - 1], imageData[i - width - 1]));
        }
    }

    private static void applyPredictorTransform7(int[] imageData, int start, int end, int width) {
        int prev = imageData[start - 1];
        for (int i = start; i < end; i++) {
            int top = imageData[i - width];
            prev = Argb.add(imageData[i], Argb.average2(prev, top));
            imageData[i] = prev;
        }
    }

    private static void applyPredictorTransform8(int[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], Argb.average2(imageData[i - width - 1], imageData[i - width]));
        }
    }

    private static void applyPredictorTransform9(int[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = Argb.add(imageData[i], Argb.average2(imageData[i - width], imageData[i - width + 1]));
        }
    }

    private static void applyPredictorTransform10(int[] imageData, int start, int end, int width) {
        int prev = imageData[start - 1];
        for (int i = start; i < end; i++) {
            int topLeft = imageData[i - width - 1];
            int top = imageData[i - width];
            int topRight = imageData[i - width + 1];
            prev = Argb.add(imageData[i], Argb.average2(Argb.average2(prev, topLeft), Argb.average2(top, topRight)));
            imageData[i] = prev;
        }
    }

    private static void applyPredictorTransform11(int[] imageData, int start, int end, int width) {
        int left = imageData[start - 1];
        int topLeft = imageData[start - width - 1];
        for (int i = start; i < end; i++) {
            int top = imageData[i - width];
            int predictLeft = 0;
            int predictTop = 0;
            predictLeft += Math.abs((Argb.red(left) + Argb.red(top) - Argb.red(topLeft)) - Argb.red(left));
            predictLeft += Math.abs((Argb.green(left) + Argb.green(top) - Argb.green(topLeft)) - Argb.green(left));
            predictLeft += Math.abs((Argb.blue(left) + Argb.blue(top) - Argb.blue(topLeft)) - Argb.blue(left));
            predictLeft += Math.abs((Argb.alpha(left) + Argb.alpha(top) - Argb.alpha(topLeft)) - Argb.alpha(left));
            predictTop += Math.abs((Argb.red(left) + Argb.red(top) - Argb.red(topLeft)) - Argb.red(top));
            predictTop += Math.abs((Argb.green(left) + Argb.green(top) - Argb.green(topLeft)) - Argb.green(top));
            predictTop += Math.abs((Argb.blue(left) + Argb.blue(top) - Argb.blue(topLeft)) - Argb.blue(top));
            predictTop += Math.abs((Argb.alpha(left) + Argb.alpha(top) - Argb.alpha(topLeft)) - Argb.alpha(top));

            int predictor = predictLeft < predictTop ? left : top;
            imageData[i] = Argb.add(imageData[i], predictor);
            topLeft = top;
            left = imageData[i];
        }
    }

    private static void applyPredictorTransform12(int[] imageData, int start, int end, int width) {
        int prev = imageData[start - 1];
        for (int i = start; i < end; i++) {
            int topLeft = imageData[i - width - 1];
            int top = imageData[i - width];
            prev = Argb.add(imageData[i], clampAddSubtractFullPixel(prev, top, topLeft));
            imageData[i] = prev;
        }
    }

    private static void applyPredictorTransform13(int[] imageData, int start, int end, int width) {
        int prev = imageData[start - 1];
        for (int i = start; i < end; i++) {
            int topLeft = imageData[i - width - 1];
            int top = imageData[i - width];
            prev = Argb.add(imageData[i], clampAddSubtractHalfPixel(Argb.average2(prev, top), topLeft));
            imageData[i] = prev;
        }
    }

    private static int clampAddSubtractFullPixel(int left, int top, int topLeft) {
        return Argb.pack(
                clampAddSubtractFull(Argb.alpha(left), Argb.alpha(top), Argb.alpha(topLeft)),
                clampAddSubtractFull(Argb.red(left), Argb.red(top), Argb.red(topLeft)),
                clampAddSubtractFull(Argb.green(left), Argb.green(top), Argb.green(topLeft)),
                clampAddSubtractFull(Argb.blue(left), Argb.blue(top), Argb.blue(topLeft))
        );
    }

    private static int clampAddSubtractHalfPixel(int averaged, int topLeft) {
        return Argb.pack(
                clampAddSubtractHalf(Argb.alpha(averaged), Argb.alpha(topLeft)),
                clampAddSubtractHalf(Argb.red(averaged), Argb.red(topLeft)),
                clampAddSubtractHalf(Argb.green(averaged), Argb.green(topLeft)),
                clampAddSubtractHalf(Argb.blue(averaged), Argb.blue(topLeft))
        );
    }

    private static int clampAddSubtractFull(int a, int b, int c) {
        return Math.max(0, Math.min(255, a + b - c));
    }

    private static int clampAddSubtractHalf(int a, int b) {
        return Math.max(0, Math.min(255, a + (a - b) / 2));
    }

    private static int colorTransformDelta(byte transform, byte color) {
        return ((int) transform * (int) color) >> 5;
    }
}
