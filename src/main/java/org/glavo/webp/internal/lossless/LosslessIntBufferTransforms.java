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

import java.nio.IntBuffer;

/// Reverse VP8L transforms specialized for direct integer buffers.
///
/// The heap decoder retains the array-specialized implementation in [LosslessTransforms]. Keeping
/// this direct-buffer path separate avoids adding an abstraction or per-pixel storage branch to the
/// established heap hot path.
@NotNullByDefault
final class LosslessIntBufferTransforms {

    /// Prevents instantiation.
    private LosslessIntBufferTransforms() {
    }

    /// Applies the predictor transform in place.
    ///
    /// @param imageData the position-zero image buffer
    /// @param width the current image width
    /// @param height the image height
    /// @param sizeBits the predictor-block size exponent
    /// @param predictorData the predictor metadata plane
    static void applyPredictorTransform(
            IntBuffer imageData,
            int width,
            int height,
            int sizeBits,
            int[] predictorData
    ) {
        int blockXSize = LosslessTransforms.subsampleSize(width, sizeBits);
        imageData.put(0, Argb.add(imageData.get(0), 0xFF00_0000));
        applyPredictorTransform1(imageData, 1, width, width);

        for (int y = 1; y < height; y++) {
            int rowStart = y * width;
            imageData.put(rowStart, Argb.add(imageData.get(rowStart), imageData.get(rowStart - width)));
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

    /// Applies the color transform in place.
    ///
    /// @param imageData the position-zero image buffer
    /// @param width the current image width
    /// @param sizeBits the transform-block size exponent
    /// @param transformData the color-transform metadata plane
    static void applyColorTransform(
            IntBuffer imageData,
            int width,
            int sizeBits,
            int[] transformData
    ) {
        int blockXSize = LosslessTransforms.subsampleSize(width, sizeBits);
        for (int y = 0; y < imageData.limit() / width; y++) {
            int rowTransformStart = (y >> sizeBits) * blockXSize;
            for (int block = 0; block < blockXSize; block++) {
                int transform = transformData[rowTransformStart + block];
                int redToBlue = Argb.red(transform);
                int greenToBlue = Argb.green(transform);
                int greenToRed = Argb.blue(transform);

                int pixelStart = y * width + (block << sizeBits);
                int pixelEnd = Math.min(y * width + ((block + 1) << sizeBits), (y + 1) * width);
                for (int pixel = pixelStart; pixel < pixelEnd; pixel++) {
                    int value = imageData.get(pixel);
                    int green = Argb.green(value);
                    int red = Argb.red(value) + colorTransformDelta((byte) greenToRed, (byte) green);
                    int blue = Argb.blue(value)
                            + colorTransformDelta((byte) greenToBlue, (byte) green)
                            + colorTransformDelta((byte) redToBlue, (byte) red);
                    imageData.put(pixel, Argb.pack(Argb.alpha(value), red, green, blue));
                }
            }
        }
    }

    /// Applies the subtract-green transform in place.
    ///
    /// @param imageData the position-zero image buffer
    static void applySubtractGreenTransform(IntBuffer imageData) {
        for (int index = 0; index < imageData.limit(); index++) {
            int value = imageData.get(index);
            int green = Argb.green(value);
            imageData.put(index, Argb.pack(
                    Argb.alpha(value),
                    Argb.red(value) + green,
                    green,
                    Argb.blue(value) + green
            ));
        }
    }

    /// Applies the color-indexing transform in place.
    ///
    /// @param imageData the position-zero image buffer
    /// @param width the expanded image width
    /// @param height the image height
    /// @param tableSize the number of color-table entries
    /// @param tableData the color table
    static void applyColorIndexingTransform(
            IntBuffer imageData,
            int width,
            int height,
            int tableSize,
            int[] tableData
    ) {
        if (tableSize > 16) {
            int[] table = new int[256];
            System.arraycopy(tableData, 0, table, 0, tableSize);
            for (int index = 0; index < imageData.limit(); index++) {
                imageData.put(index, table[Argb.green(imageData.get(index))]);
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
                int packed = Argb.green(imageData.get(packedOffset + block));
                int blockPixelCount = Math.min(pixelsPerPackedByte, width - block * pixelsPerPackedByte);
                for (int pixel = 0; pixel < blockPixelCount; pixel++) {
                    int x = block * pixelsPerPackedByte + pixel;
                    int tableIndex = (packed >> (pixel * bitsPerEntry)) & mask;
                    imageData.put(outOffset + x, tableIndex < tableSize ? tableData[tableIndex] : 0);
                }
            }
        }
    }

    /// Applies constant-black prediction to a pixel range.
    private static void applyPredictorTransform0(IntBuffer imageData, int start, int end) {
        for (int index = start; index < end; index++) {
            imageData.put(index, Argb.add(imageData.get(index), 0xFF00_0000));
        }
    }

    /// Applies left-pixel prediction to a pixel range.
    private static void applyPredictorTransform1(IntBuffer imageData, int start, int end, int width) {
        for (int index = start; index < end; index++) {
            imageData.put(index, Argb.add(imageData.get(index), imageData.get(index - 1)));
        }
    }

    /// Applies top-pixel prediction to a pixel range.
    private static void applyPredictorTransform2(IntBuffer imageData, int start, int end, int width) {
        for (int index = start; index < end; index++) {
            imageData.put(index, Argb.add(imageData.get(index), imageData.get(index - width)));
        }
    }

    /// Applies top-right-pixel prediction to a pixel range.
    private static void applyPredictorTransform3(IntBuffer imageData, int start, int end, int width) {
        for (int index = start; index < end; index++) {
            imageData.put(index, Argb.add(imageData.get(index), imageData.get(index - width + 1)));
        }
    }

    /// Applies top-left-pixel prediction to a pixel range.
    private static void applyPredictorTransform4(IntBuffer imageData, int start, int end, int width) {
        for (int index = start; index < end; index++) {
            imageData.put(index, Argb.add(imageData.get(index), imageData.get(index - width - 1)));
        }
    }

    /// Applies averaged left/top-left/top/top-right prediction to a pixel range.
    private static void applyPredictorTransform5(IntBuffer imageData, int start, int end, int width) {
        int previous = imageData.get(start - 1);
        for (int index = start; index < end; index++) {
            int topRight = imageData.get(index - width + 1);
            int top = imageData.get(index - width);
            previous = Argb.add(imageData.get(index), Argb.average2(Argb.average2(previous, topRight), top));
            imageData.put(index, previous);
        }
    }

    /// Applies averaged left/top-left prediction to a pixel range.
    private static void applyPredictorTransform6(IntBuffer imageData, int start, int end, int width) {
        for (int index = start; index < end; index++) {
            imageData.put(
                    index,
                    Argb.add(
                            imageData.get(index),
                            Argb.average2(imageData.get(index - 1), imageData.get(index - width - 1))
                    )
            );
        }
    }

    /// Applies averaged left/top prediction to a pixel range.
    private static void applyPredictorTransform7(IntBuffer imageData, int start, int end, int width) {
        int previous = imageData.get(start - 1);
        for (int index = start; index < end; index++) {
            int top = imageData.get(index - width);
            previous = Argb.add(imageData.get(index), Argb.average2(previous, top));
            imageData.put(index, previous);
        }
    }

    /// Applies averaged top-left/top prediction to a pixel range.
    private static void applyPredictorTransform8(IntBuffer imageData, int start, int end, int width) {
        for (int index = start; index < end; index++) {
            imageData.put(
                    index,
                    Argb.add(
                            imageData.get(index),
                            Argb.average2(imageData.get(index - width - 1), imageData.get(index - width))
                    )
            );
        }
    }

    /// Applies averaged top/top-right prediction to a pixel range.
    private static void applyPredictorTransform9(IntBuffer imageData, int start, int end, int width) {
        for (int index = start; index < end; index++) {
            imageData.put(
                    index,
                    Argb.add(
                            imageData.get(index),
                            Argb.average2(imageData.get(index - width), imageData.get(index - width + 1))
                    )
            );
        }
    }

    /// Applies the four-neighbor average predictor to a pixel range.
    private static void applyPredictorTransform10(IntBuffer imageData, int start, int end, int width) {
        int previous = imageData.get(start - 1);
        for (int index = start; index < end; index++) {
            int topLeft = imageData.get(index - width - 1);
            int top = imageData.get(index - width);
            int topRight = imageData.get(index - width + 1);
            previous = Argb.add(
                    imageData.get(index),
                    Argb.average2(Argb.average2(previous, topLeft), Argb.average2(top, topRight))
            );
            imageData.put(index, previous);
        }
    }

    /// Applies the select-left-or-top predictor to a pixel range.
    private static void applyPredictorTransform11(IntBuffer imageData, int start, int end, int width) {
        int left = imageData.get(start - 1);
        int topLeft = imageData.get(start - width - 1);
        for (int index = start; index < end; index++) {
            int top = imageData.get(index - width);
            int predictLeft = colorDistance(top, topLeft);
            int predictTop = colorDistance(left, topLeft);
            int predictor = predictLeft < predictTop ? left : top;
            int value = Argb.add(imageData.get(index), predictor);
            imageData.put(index, value);
            topLeft = top;
            left = value;
        }
    }

    /// Applies clamped add-subtract prediction to a pixel range.
    private static void applyPredictorTransform12(IntBuffer imageData, int start, int end, int width) {
        int previous = imageData.get(start - 1);
        for (int index = start; index < end; index++) {
            int topLeft = imageData.get(index - width - 1);
            int top = imageData.get(index - width);
            previous = Argb.add(imageData.get(index), clampAddSubtractFullPixel(previous, top, topLeft));
            imageData.put(index, previous);
        }
    }

    /// Applies half-gradient prediction to a pixel range.
    private static void applyPredictorTransform13(IntBuffer imageData, int start, int end, int width) {
        int previous = imageData.get(start - 1);
        for (int index = start; index < end; index++) {
            int topLeft = imageData.get(index - width - 1);
            int top = imageData.get(index - width);
            previous = Argb.add(
                    imageData.get(index),
                    clampAddSubtractHalfPixel(Argb.average2(previous, top), topLeft)
            );
            imageData.put(index, previous);
        }
    }

    /// Returns the summed channel distance between two packed pixels.
    private static int colorDistance(int first, int second) {
        return Math.abs(Argb.alpha(first) - Argb.alpha(second))
                + Math.abs(Argb.red(first) - Argb.red(second))
                + Math.abs(Argb.green(first) - Argb.green(second))
                + Math.abs(Argb.blue(first) - Argb.blue(second));
    }

    /// Applies a full clamped add-subtract operation to packed channels.
    private static int clampAddSubtractFullPixel(int left, int top, int topLeft) {
        return Argb.pack(
                clampAddSubtractFull(Argb.alpha(left), Argb.alpha(top), Argb.alpha(topLeft)),
                clampAddSubtractFull(Argb.red(left), Argb.red(top), Argb.red(topLeft)),
                clampAddSubtractFull(Argb.green(left), Argb.green(top), Argb.green(topLeft)),
                clampAddSubtractFull(Argb.blue(left), Argb.blue(top), Argb.blue(topLeft))
        );
    }

    /// Applies a half-gradient operation to packed channels.
    private static int clampAddSubtractHalfPixel(int averaged, int topLeft) {
        return Argb.pack(
                clampAddSubtractHalf(Argb.alpha(averaged), Argb.alpha(topLeft)),
                clampAddSubtractHalf(Argb.red(averaged), Argb.red(topLeft)),
                clampAddSubtractHalf(Argb.green(averaged), Argb.green(topLeft)),
                clampAddSubtractHalf(Argb.blue(averaged), Argb.blue(topLeft))
        );
    }

    /// Returns one fully clamped add-subtract channel.
    private static int clampAddSubtractFull(int first, int second, int third) {
        return Math.max(0, Math.min(255, first + second - third));
    }

    /// Returns one clamped half-gradient channel.
    private static int clampAddSubtractHalf(int first, int second) {
        return Math.max(0, Math.min(255, first + (first - second) / 2));
    }

    /// Returns one signed color-transform contribution.
    private static int colorTransformDelta(byte transform, byte color) {
        return ((int) transform * (int) color) >> 5;
    }
}
