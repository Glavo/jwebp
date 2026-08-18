// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
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
            int predictorRowStart = (y >> sizeBits) * blockXSize;
            for (int blockX = 0; blockX < blockXSize; blockX++) {
                int predictor = Argb.green(predictorData[predictorRowStart + blockX]);
                int startIndex = rowStart + Math.max(blockX << sizeBits, 1);
                int endIndex = rowStart + Math.min((blockX + 1) << sizeBits, width);

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
        int height = imageData.length / width;
        int blockSize = 1 << sizeBits;
        int blockXSize = subsampleSize(width, sizeBits);
        int blockYSize = subsampleSize(height, sizeBits);
        for (int blockY = 0; blockY < blockYSize; blockY++) {
            int yStart = blockY << sizeBits;
            int yEnd = Math.min(yStart + blockSize, height);
            int transformRowStart = blockY * blockXSize;
            for (int blockX = 0; blockX < blockXSize; blockX++) {
                int transform = transformData[transformRowStart + blockX];
                int redToBlue = Argb.red(transform);
                int greenToBlue = Argb.green(transform);
                int greenToRed = Argb.blue(transform);

                int xStart = blockX << sizeBits;
                int xEnd = Math.min(xStart + blockSize, width);
                for (int y = yStart; y < yEnd; y++) {
                    int pixelEnd = y * width + xEnd;
                    for (int pixel = y * width + xStart; pixel < pixelEnd; pixel++) {
                        int value = imageData[pixel];
                        int green = Argb.green(value);
                        int red = Argb.red(value) + colorTransformDelta((byte) greenToRed, (byte) green);
                        int blue = Argb.blue(value)
                                + colorTransformDelta((byte) greenToBlue, (byte) green)
                                + colorTransformDelta((byte) redToBlue, (byte) red);
                        imageData[pixel] = (value & 0xFF00_FF00)
                                | ((red & 0xFF) << 16)
                                | (blue & 0xFF);
                    }
                }
            }
        }
    }

    /// Applies the subtract-green transform.
    public static void applySubtractGreenTransform(int[] imageData) {
        for (int index = 0; index < imageData.length; index++) {
            int value = imageData[index];
            int green = Argb.green(value);
            int redBlue = (value & 0x00FF_00FF) + green * 0x0001_0001;
            imageData[index] = (value & 0xFF00_FF00) | (redBlue & 0x00FF_00FF);
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

        for (int y = height - 1; y >= 0; y--) {
            int packedOffset = y * packedImageWidth;
            int outOffset = y * width;
            // Expanding right-to-left keeps every unread packed entry to the left of each write.
            for (int block = packedImageWidth - 1; block >= 0; block--) {
                int packed = Argb.green(imageData[packedOffset + block]);
                int blockPixelCount = Math.min(pixelsPerPackedByte, width - block * pixelsPerPackedByte);
                for (int pixel = 0; pixel < blockPixelCount; pixel++) {
                    int x = block * pixelsPerPackedByte + pixel;
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
            int predictLeft = colorDistance(top, topLeft);
            int predictTop = colorDistance(left, topLeft);

            int predictor = predictLeft < predictTop ? left : top;
            int value = Argb.add(imageData[i], predictor);
            imageData[i] = value;
            topLeft = top;
            left = value;
        }
    }

    /// Returns the sum of absolute channel differences between two packed pixels.
    ///
    /// @param first the first packed pixel
    /// @param second the second packed pixel
    /// @return the summed alpha, red, green and blue distances
    private static int colorDistance(int first, int second) {
        return Math.abs(Argb.alpha(first) - Argb.alpha(second))
                + Math.abs(Argb.red(first) - Argb.red(second))
                + Math.abs(Argb.green(first) - Argb.green(second))
                + Math.abs(Argb.blue(first) - Argb.blue(second));
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
