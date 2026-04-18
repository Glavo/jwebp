package org.glavo.javafx.webp.internal.lossless;

/// Reverse transforms for VP8L decoded pixels.
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
    public static final class Transform {
        final int kind;
        final int sizeBits;
        final byte[] data;
        final int tableSize;

        private Transform(int kind, int sizeBits, byte[] data, int tableSize) {
            this.kind = kind;
            this.sizeBits = sizeBits;
            this.data = data;
            this.tableSize = tableSize;
        }

        public static Transform predictor(int sizeBits, byte[] data) {
            return new Transform(PREDICTOR, sizeBits, data, 0);
        }

        public static Transform color(int sizeBits, byte[] data) {
            return new Transform(COLOR, sizeBits, data, 0);
        }

        public static Transform subtractGreen() {
            return new Transform(SUBTRACT_GREEN, 0, null, 0);
        }

        public static Transform colorIndexing(int tableSize, byte[] data) {
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
    public static void applyPredictorTransform(byte[] imageData, int width, int height, int sizeBits, byte[] predictorData) {
        int blockXSize = subsampleSize(width, sizeBits);
        imageData[3] = (byte) ((imageData[3] + 255) & 0xFF);
        applyPredictorTransform1(imageData, 4, width * 4, width);

        for (int y = 1; y < height; y++) {
            for (int channel = 0; channel < 4; channel++) {
                int index = y * width * 4 + channel;
                imageData[index] = (byte) ((imageData[index] + imageData[(y - 1) * width * 4 + channel]) & 0xFF);
            }
        }

        for (int y = 1; y < height; y++) {
            for (int blockX = 0; blockX < blockXSize; blockX++) {
                int blockIndex = (y >> sizeBits) * blockXSize + blockX;
                int predictor = predictorData[blockIndex * 4 + 1] & 0xFF;
                int startIndex = (y * width + Math.max(blockX << sizeBits, 1)) * 4;
                int endIndex = (y * width + Math.min((blockX + 1) << sizeBits, width)) * 4;

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
    public static void applyColorTransform(byte[] imageData, int width, int sizeBits, byte[] transformData) {
        int blockXSize = subsampleSize(width, sizeBits);
        for (int y = 0; y < imageData.length / (width * 4); y++) {
            int rowTransformStart = (y >> sizeBits) * blockXSize * 4;
            for (int block = 0; block < blockXSize; block++) {
                int transformIndex = rowTransformStart + block * 4;
                int redToBlue = transformData[transformIndex] & 0xFF;
                int greenToBlue = transformData[transformIndex + 1] & 0xFF;
                int greenToRed = transformData[transformIndex + 2] & 0xFF;

                int pixelStart = (y * width + (block << sizeBits)) * 4;
                int pixelEnd = Math.min((y * width + ((block + 1) << sizeBits)) * 4, (y + 1) * width * 4);
                for (int pixel = pixelStart; pixel < pixelEnd; pixel += 4) {
                    int green = imageData[pixel + 1] & 0xFF;
                    int red = (imageData[pixel] & 0xFF) + colorTransformDelta((byte) greenToRed, (byte) green);
                    int blue = (imageData[pixel + 2] & 0xFF)
                            + colorTransformDelta((byte) greenToBlue, (byte) green)
                            + colorTransformDelta((byte) redToBlue, (byte) red);
                    imageData[pixel] = (byte) red;
                    imageData[pixel + 2] = (byte) blue;
                }
            }
        }
    }

    /// Applies the subtract-green transform.
    public static void applySubtractGreenTransform(byte[] imageData) {
        for (int index = 0; index < imageData.length; index += 4) {
            imageData[index] = (byte) ((imageData[index] + imageData[index + 1]) & 0xFF);
            imageData[index + 2] = (byte) ((imageData[index + 2] + imageData[index + 1]) & 0xFF);
        }
    }

    /// Applies the color indexing transform.
    public static void applyColorIndexingTransform(byte[] imageData, int width, int height, int tableSize, byte[] tableData) {
        if (tableSize > 16) {
            byte[][] table = new byte[256][4];
            for (int i = 0; i < tableSize; i++) {
                System.arraycopy(tableData, i * 4, table[i], 0, 4);
            }
            for (int index = 0; index < imageData.length; index += 4) {
                int tableIndex = imageData[index + 1] & 0xFF;
                System.arraycopy(table[tableIndex], 0, imageData, index, 4);
            }
            return;
        }

        int bits = tableSize <= 2 ? 3 : (tableSize <= 4 ? 2 : 1);
        int pixelsPerPackedByte = 1 << bits;
        int bitsPerEntry = 8 / pixelsPerPackedByte;
        int mask = (1 << bitsPerEntry) - 1;
        int packedImageWidth = (width + pixelsPerPackedByte - 1) / pixelsPerPackedByte;
        byte[] packedIndices = new byte[packedImageWidth];

        for (int y = height - 1; y >= 0; y--) {
            int packedOffset = y * packedImageWidth * 4;
            for (int block = 0; block < packedImageWidth; block++) {
                packedIndices[block] = imageData[packedOffset + block * 4 + 1];
            }

            int outOffset = y * width * 4;
            for (int block = 0; block < packedImageWidth; block++) {
                int packed = packedIndices[block] & 0xFF;
                for (int pixel = 0; pixel < pixelsPerPackedByte; pixel++) {
                    int x = block * pixelsPerPackedByte + pixel;
                    if (x >= width) {
                        break;
                    }
                    int tableIndex = (packed >> (pixel * bitsPerEntry)) & mask;
                    int dst = outOffset + x * 4;
                    if (tableIndex < tableSize) {
                        System.arraycopy(tableData, tableIndex * 4, imageData, dst, 4);
                    } else {
                        imageData[dst] = 0;
                        imageData[dst + 1] = 0;
                        imageData[dst + 2] = 0;
                        imageData[dst + 3] = 0;
                    }
                }
            }
        }
    }

    private static void applyPredictorTransform0(byte[] imageData, int start, int end) {
        for (int i = start + 3; i < end; i += 4) {
            imageData[i] = (byte) ((imageData[i] + 0xFF) & 0xFF);
        }
    }

    private static void applyPredictorTransform1(byte[] imageData, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            imageData[i] = (byte) ((imageData[i] + imageData[i - 4]) & 0xFF);
        }
    }

    private static void applyPredictorTransform2(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            imageData[i] = (byte) ((imageData[i] + imageData[i - stride]) & 0xFF);
        }
    }

    private static void applyPredictorTransform3(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            imageData[i] = (byte) ((imageData[i] + imageData[i - stride + 4]) & 0xFF);
        }
    }

    private static void applyPredictorTransform4(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            imageData[i] = (byte) ((imageData[i] + imageData[i - stride - 4]) & 0xFF);
        }
    }

    private static void applyPredictorTransform5(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        byte[] prev = slice4(imageData, start - 4);
        for (int i = start; i < end; i += 4) {
            byte[] topRight = slice4(imageData, i - stride + 4);
            byte[] top = slice4(imageData, i - stride);
            prev = new byte[]{
                    (byte) ((imageData[i] + average2(average2(prev[0] & 0xFF, topRight[0] & 0xFF), top[0] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 1] + average2(average2(prev[1] & 0xFF, topRight[1] & 0xFF), top[1] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 2] + average2(average2(prev[2] & 0xFF, topRight[2] & 0xFF), top[2] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 3] + average2(average2(prev[3] & 0xFF, topRight[3] & 0xFF), top[3] & 0xFF)) & 0xFF)
            };
            System.arraycopy(prev, 0, imageData, i, 4);
        }
    }

    private static void applyPredictorTransform6(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            imageData[i] = (byte) ((imageData[i] + average2(imageData[i - 4] & 0xFF, imageData[i - stride - 4] & 0xFF)) & 0xFF);
        }
    }

    private static void applyPredictorTransform7(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        byte[] prev = slice4(imageData, start - 4);
        for (int i = start; i < end; i += 4) {
            byte[] top = slice4(imageData, i - stride);
            prev = new byte[]{
                    (byte) ((imageData[i] + average2(prev[0] & 0xFF, top[0] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 1] + average2(prev[1] & 0xFF, top[1] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 2] + average2(prev[2] & 0xFF, top[2] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 3] + average2(prev[3] & 0xFF, top[3] & 0xFF)) & 0xFF)
            };
            System.arraycopy(prev, 0, imageData, i, 4);
        }
    }

    private static void applyPredictorTransform8(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            imageData[i] = (byte) ((imageData[i] + average2(imageData[i - stride - 4] & 0xFF, imageData[i - stride] & 0xFF)) & 0xFF);
        }
    }

    private static void applyPredictorTransform9(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            imageData[i] = (byte) ((imageData[i] + average2(imageData[i - stride] & 0xFF, imageData[i - stride + 4] & 0xFF)) & 0xFF);
        }
    }

    private static void applyPredictorTransform10(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        byte[] prev = slice4(imageData, start - 4);
        for (int i = start; i < end; i += 4) {
            byte[] topLeft = slice4(imageData, i - stride - 4);
            byte[] top = slice4(imageData, i - stride);
            byte[] topRight = slice4(imageData, i - stride + 4);
            prev = new byte[]{
                    (byte) ((imageData[i] + average2(average2(prev[0] & 0xFF, topLeft[0] & 0xFF), average2(top[0] & 0xFF, topRight[0] & 0xFF))) & 0xFF),
                    (byte) ((imageData[i + 1] + average2(average2(prev[1] & 0xFF, topLeft[1] & 0xFF), average2(top[1] & 0xFF, topRight[1] & 0xFF))) & 0xFF),
                    (byte) ((imageData[i + 2] + average2(average2(prev[2] & 0xFF, topLeft[2] & 0xFF), average2(top[2] & 0xFF, topRight[2] & 0xFF))) & 0xFF),
                    (byte) ((imageData[i + 3] + average2(average2(prev[3] & 0xFF, topLeft[3] & 0xFF), average2(top[3] & 0xFF, topRight[3] & 0xFF))) & 0xFF)
            };
            System.arraycopy(prev, 0, imageData, i, 4);
        }
    }

    private static void applyPredictorTransform11(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        int[] left = toInt4(slice4(imageData, start - 4));
        int[] topLeft = toInt4(slice4(imageData, start - stride - 4));
        for (int i = start; i < end; i += 4) {
            int[] top = toInt4(slice4(imageData, i - stride));
            int predictLeft = 0;
            int predictTop = 0;
            for (int c = 0; c < 4; c++) {
                int predict = left[c] + top[c] - topLeft[c];
                predictLeft += Math.abs(predict - left[c]);
                predictTop += Math.abs(predict - top[c]);
            }
            if (predictLeft < predictTop) {
                for (int c = 0; c < 4; c++) {
                    imageData[i + c] = (byte) ((imageData[i + c] + left[c]) & 0xFF);
                }
            } else {
                for (int c = 0; c < 4; c++) {
                    imageData[i + c] = (byte) ((imageData[i + c] + top[c]) & 0xFF);
                }
            }
            topLeft = top;
            left = toInt4(slice4(imageData, i));
        }
    }

    private static void applyPredictorTransform12(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        byte[] prev = slice4(imageData, start - 4);
        for (int i = start; i < end; i += 4) {
            byte[] topLeft = slice4(imageData, i - stride - 4);
            byte[] top = slice4(imageData, i - stride);
            prev = new byte[]{
                    (byte) ((imageData[i] + clampAddSubtractFull(prev[0] & 0xFF, top[0] & 0xFF, topLeft[0] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 1] + clampAddSubtractFull(prev[1] & 0xFF, top[1] & 0xFF, topLeft[1] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 2] + clampAddSubtractFull(prev[2] & 0xFF, top[2] & 0xFF, topLeft[2] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 3] + clampAddSubtractFull(prev[3] & 0xFF, top[3] & 0xFF, topLeft[3] & 0xFF)) & 0xFF)
            };
            System.arraycopy(prev, 0, imageData, i, 4);
        }
    }

    private static void applyPredictorTransform13(byte[] imageData, int start, int end, int width) {
        int stride = width * 4;
        byte[] prev = slice4(imageData, start - 4);
        for (int i = start; i < end; i += 4) {
            byte[] topLeft = slice4(imageData, i - stride - 4);
            byte[] top = slice4(imageData, i - stride);
            prev = new byte[]{
                    (byte) ((imageData[i] + clampAddSubtractHalf(((prev[0] & 0xFF) + (top[0] & 0xFF)) / 2, topLeft[0] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 1] + clampAddSubtractHalf(((prev[1] & 0xFF) + (top[1] & 0xFF)) / 2, topLeft[1] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 2] + clampAddSubtractHalf(((prev[2] & 0xFF) + (top[2] & 0xFF)) / 2, topLeft[2] & 0xFF)) & 0xFF),
                    (byte) ((imageData[i + 3] + clampAddSubtractHalf(((prev[3] & 0xFF) + (top[3] & 0xFF)) / 2, topLeft[3] & 0xFF)) & 0xFF)
            };
            System.arraycopy(prev, 0, imageData, i, 4);
        }
    }

    private static byte[] slice4(byte[] data, int offset) {
        return new byte[]{data[offset], data[offset + 1], data[offset + 2], data[offset + 3]};
    }

    private static int[] toInt4(byte[] data) {
        return new int[]{data[0] & 0xFF, data[1] & 0xFF, data[2] & 0xFF, data[3] & 0xFF};
    }

    private static int average2(int a, int b) {
        return (a + b) / 2;
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
