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

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.Argb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Pure-Java VP8L decoder.
///
/// The implementation follows the structure of the reference `image-webp` lossless
/// decoder. It decodes to tightly packed non-premultiplied `ARGB` pixels.
@NotNullByDefault
public final class LosslessDecoder {

    private static final int GREEN = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int ALPHA = 3;
    private static final int DIST = 4;

    private static final int HUFFMAN_CODES_PER_META_CODE = 5;
    private static final int[] ALPHABET_SIZE = {256 + 24, 256, 256, 256, 40};
    private static final int NUM_TRANSFORM_TYPES = 4;

    private final LosslessBitReader bitReader;
    private final LosslessTransforms.@Nullable Transform[] transforms = new LosslessTransforms.Transform[NUM_TRANSFORM_TYPES];
    private final int[] transformOrder = new int[NUM_TRANSFORM_TYPES];
    private int transformOrderSize;
    private int width;
    private int height;

    /// Creates a decoder for one VP8L payload.
    ///
    /// @param data the encoded VP8L bytes
    public LosslessDecoder(byte[] data) {
        this.bitReader = new LosslessBitReader(data);
    }

    /// Decodes a VP8L frame into `ARGB` pixels.
    ///
    /// @param width the expected width
    /// @param height the expected height
    /// @param implicitDimensions whether the VP8L header should be skipped because dimensions are
    ///                           defined externally, as in ALPH chunks
    /// @param buffer the `ARGB` destination buffer
    /// @throws WebPException if the bitstream is malformed or inconsistent
    public void decodeFrame(int width, int height, boolean implicitDimensions, int[] buffer) throws WebPException {
        resetState();

        if (implicitDimensions) {
            this.width = width;
            this.height = height;
        } else {
            int signature = bitReader.readBits(8);
            if (signature != 0x2F) {
                throw new WebPException("Invalid VP8L signature: " + signature);
            }

            this.width = bitReader.readBits(14) + 1;
            this.height = bitReader.readBits(14) + 1;
            if (this.width != width || this.height != height) {
                throw new WebPException("Inconsistent VP8L frame dimensions");
            }

            bitReader.readBits(1); // alpha used bit, tracked by the container
            int version = bitReader.readBits(3);
            if (version != 0) {
                throw new WebPException("Unsupported VP8L version: " + version);
            }
        }

        int transformedWidth = readTransforms();
        decodeImageStream(transformedWidth, this.height, true, buffer);

        int currentWidth = transformedWidth;
        for (int i = transformOrderSize - 1; i >= 0; i--) {
            LosslessTransforms.Transform transform = transforms[transformOrder[i]];
            switch (transform.kind) {
                case LosslessTransforms.PREDICTOR -> LosslessTransforms.applyPredictorTransform(buffer, currentWidth, this.height, transform.sizeBits, transform.data);
                case LosslessTransforms.COLOR -> LosslessTransforms.applyColorTransform(buffer, currentWidth, transform.sizeBits, transform.data);
                case LosslessTransforms.SUBTRACT_GREEN -> LosslessTransforms.applySubtractGreenTransform(buffer);
                case LosslessTransforms.COLOR_INDEXING -> {
                    currentWidth = this.width;
                    LosslessTransforms.applyColorIndexingTransform(buffer, currentWidth, this.height, transform.tableSize, transform.data);
                }
                default -> throw new WebPException("Unknown VP8L transform kind");
            }
        }
    }

    private void resetState() {
        Arrays.fill(transforms, null);
        transformOrderSize = 0;
        width = 0;
        height = 0;
    }

    private void decodeImageStream(int xsize, int ysize, boolean readMeta, int[] data) throws WebPException {
        Integer colorCacheBits = readColorCache();
        ColorCache colorCache = colorCacheBits == null ? null : new ColorCache(colorCacheBits);
        HuffmanInfo huffmanInfo = readHuffmanCodes(readMeta, xsize, ysize, colorCache);
        decodeImageData(xsize, ysize, huffmanInfo, data);
    }

    private int readTransforms() throws WebPException {
        int xsize = width;
        while (bitReader.readBits(1) == 1) {
            int transformTypeValue = bitReader.readBits(2);
            if (transforms[transformTypeValue] != null) {
                throw new WebPException("Duplicate VP8L transform");
            }

            transformOrder[transformOrderSize++] = transformTypeValue;
            LosslessTransforms.Transform transform;
            switch (transformTypeValue) {
                case LosslessTransforms.PREDICTOR -> {
                    int sizeBits = bitReader.readBits(3) + 2;
                    int blockXSize = LosslessTransforms.subsampleSize(xsize, sizeBits);
                    int blockYSize = LosslessTransforms.subsampleSize(height, sizeBits);
                    int[] predictorData = new int[blockXSize * blockYSize];
                    decodeImageStream(blockXSize, blockYSize, false, predictorData);
                    transform = LosslessTransforms.Transform.predictor(sizeBits, predictorData);
                }
                case LosslessTransforms.COLOR -> {
                    int sizeBits = bitReader.readBits(3) + 2;
                    int blockXSize = LosslessTransforms.subsampleSize(xsize, sizeBits);
                    int blockYSize = LosslessTransforms.subsampleSize(height, sizeBits);
                    int[] transformData = new int[blockXSize * blockYSize];
                    decodeImageStream(blockXSize, blockYSize, false, transformData);
                    transform = LosslessTransforms.Transform.color(sizeBits, transformData);
                }
                case LosslessTransforms.SUBTRACT_GREEN -> transform = LosslessTransforms.Transform.subtractGreen();
                case LosslessTransforms.COLOR_INDEXING -> {
                    int colorTableSize = bitReader.readBits(8) + 1;
                    int[] colorMap = new int[colorTableSize];
                    decodeImageStream(colorTableSize, 1, false, colorMap);

                    int bits;
                    if (colorTableSize <= 2) {
                        bits = 3;
                    } else if (colorTableSize <= 4) {
                        bits = 2;
                    } else if (colorTableSize <= 16) {
                        bits = 1;
                    } else {
                        bits = 0;
                    }
                    xsize = LosslessTransforms.subsampleSize(xsize, bits);
                    adjustColorMap(colorMap);
                    transform = LosslessTransforms.Transform.colorIndexing(colorTableSize, colorMap);
                }
                default -> throw new WebPException("Invalid VP8L transform type");
            }
            transforms[transformTypeValue] = transform;
        }
        return xsize;
    }

    private void adjustColorMap(int[] colorMap) {
        for (int i = 1; i < colorMap.length; i++) {
            colorMap[i] = Argb.add(colorMap[i], colorMap[i - 1]);
        }
    }

    private HuffmanInfo readHuffmanCodes(boolean readMeta, int xsize, int ysize, @Nullable ColorCache colorCache) throws WebPException {
        int numHuffGroups = 1;
        int huffmanBits = 0;
        int huffmanXSize = 1;
        int huffmanYSize = 1;
        int[] entropyImage = new int[0];

        if (readMeta && bitReader.readBits(1) == 1) {
            huffmanBits = bitReader.readBits(3) + 2;
            huffmanXSize = LosslessTransforms.subsampleSize(xsize, huffmanBits);
            huffmanYSize = LosslessTransforms.subsampleSize(ysize, huffmanBits);

            int[] data = new int[huffmanXSize * huffmanYSize];
            decodeImageStream(huffmanXSize, huffmanYSize, false, data);
            entropyImage = new int[huffmanXSize * huffmanYSize];
            for (int i = 0; i < entropyImage.length; i++) {
                int metaHuffCode = (Argb.red(data[i]) << 8) | Argb.green(data[i]);
                if (metaHuffCode >= numHuffGroups) {
                    numHuffGroups = metaHuffCode + 1;
                }
                entropyImage[i] = metaHuffCode;
            }
        }

        List<LosslessHuffmanTree[]> groups = new ArrayList<>(numHuffGroups);
        for (int groupIndex = 0; groupIndex < numHuffGroups; groupIndex++) {
            LosslessHuffmanTree[] group = new LosslessHuffmanTree[HUFFMAN_CODES_PER_META_CODE];
            for (int j = 0; j < HUFFMAN_CODES_PER_META_CODE; j++) {
                int alphabetSize = ALPHABET_SIZE[j];
                if (j == GREEN && colorCache != null) {
                    alphabetSize += 1 << colorCache.colorCacheBits;
                }
                group[j] = readHuffmanCode(alphabetSize);
            }
            groups.add(group);
        }

        int huffmanMask = huffmanBits == 0 ? -1 : (1 << huffmanBits) - 1;
        return new HuffmanInfo(huffmanXSize, colorCache, entropyImage, huffmanBits, huffmanMask, groups);
    }

    private LosslessHuffmanTree readHuffmanCode(int alphabetSize) throws WebPException {
        boolean simple = bitReader.readBits(1) == 1;
        if (simple) {
            int numSymbols = bitReader.readBits(1) + 1;
            int isFirst8Bits = bitReader.readBits(1);
            int zeroSymbol = bitReader.readBits(1 + 7 * isFirst8Bits);
            if (zeroSymbol >= alphabetSize) {
                throw new WebPException("Corrupt VP8L bitstream");
            }
            if (numSymbols == 1) {
                return LosslessHuffmanTree.single(zeroSymbol);
            }

            int oneSymbol = bitReader.readBits(8);
            if (oneSymbol >= alphabetSize) {
                throw new WebPException("Corrupt VP8L bitstream");
            }
            return LosslessHuffmanTree.pair(zeroSymbol, oneSymbol);
        }

        int[] codeLengthCodeLengths = new int[LosslessConstants.CODE_LENGTH_CODES];
        int numCodeLengths = 4 + bitReader.readBits(4);
        for (int i = 0; i < numCodeLengths; i++) {
            codeLengthCodeLengths[LosslessConstants.CODE_LENGTH_CODE_ORDER[i]] = bitReader.readBits(3);
        }
        int[] codeLengths = readHuffmanCodeLengths(codeLengthCodeLengths, alphabetSize);
        return LosslessHuffmanTree.implicit(codeLengths);
    }

    private int[] readHuffmanCodeLengths(int[] codeLengthCodeLengths, int numSymbols) throws WebPException {
        LosslessHuffmanTree table = LosslessHuffmanTree.implicit(codeLengthCodeLengths);
        int maxSymbol;
        if (bitReader.readBits(1) == 1) {
            int lengthBits = 2 + 2 * bitReader.readBits(3);
            int maxMinusTwo = bitReader.readBits(lengthBits);
            if (maxMinusTwo > numSymbols - 2) {
                throw new WebPException("Corrupt VP8L bitstream");
            }
            maxSymbol = 2 + maxMinusTwo;
        } else {
            maxSymbol = numSymbols;
        }

        int[] codeLengths = new int[numSymbols];
        int previousCodeLength = 8;
        int symbol = 0;
        while (symbol < numSymbols) {
            if (maxSymbol == 0) {
                break;
            }
            maxSymbol--;

            bitReader.fill();
            int codeLength = table.readSymbol(bitReader);
            if (codeLength < 16) {
                codeLengths[symbol++] = codeLength;
                if (codeLength != 0) {
                    previousCodeLength = codeLength;
                }
            } else {
                boolean usePrevious = codeLength == 16;
                int slot = codeLength - 16;
                int extraBits;
                int repeatOffset;
                if (slot == 0) {
                    extraBits = 2;
                    repeatOffset = 3;
                } else if (slot == 1) {
                    extraBits = 3;
                    repeatOffset = 3;
                } else if (slot == 2) {
                    extraBits = 7;
                    repeatOffset = 11;
                } else {
                    throw new WebPException("Corrupt VP8L bitstream");
                }

                int repeat = bitReader.readBits(extraBits) + repeatOffset;
                if (symbol + repeat > numSymbols) {
                    throw new WebPException("Corrupt VP8L bitstream");
                }

                int value = usePrevious ? previousCodeLength : 0;
                while (repeat-- > 0) {
                    codeLengths[symbol++] = value;
                }
            }
        }
        return codeLengths;
    }

    private void decodeImageData(int width, int height, HuffmanInfo huffmanInfo, int[] data) throws WebPException {
        int numValues = width * height;
        LosslessHuffmanTree[] tree = huffmanInfo.huffmanCodeGroups.get(huffmanInfo.getHuffIndex(0, 0));
        int index = 0;
        int nextBlockStart = 0;

        while (index < numValues) {
            bitReader.fill();

            if (index >= nextBlockStart) {
                int x = index % width;
                int y = index / width;
                nextBlockStart = Math.min(x | huffmanInfo.mask, width - 1) + y * width + 1;
                tree = huffmanInfo.huffmanCodeGroups.get(huffmanInfo.getHuffIndex(x, y));

                boolean allSingle = true;
                for (int channel = 0; channel < 4; channel++) {
                    if (!tree[channel].isSingleNode()) {
                        allSingle = false;
                        break;
                    }
                }
                if (allSingle) {
                    int code = tree[GREEN].readSymbol(bitReader);
                    if (code < 256) {
                        int count = huffmanInfo.bits == 0 ? numValues : nextBlockStart - index;
                        int red = tree[RED].readSymbol(bitReader);
                        int blue = tree[BLUE].readSymbol(bitReader);
                        int alpha = tree[ALPHA].readSymbol(bitReader);
                        int value = Argb.pack(alpha, red, code, blue);

                        for (int i = 0; i < count; i++) {
                            data[index + i] = value;
                        }
                        if (huffmanInfo.colorCache != null) {
                            huffmanInfo.colorCache.insert(value);
                        }
                        index += count;
                        continue;
                    }
                }
            }

            int code = tree[GREEN].readSymbol(bitReader);
            if (code < 256) {
                int green = code;
                int red = tree[RED].readSymbol(bitReader);
                int blue = tree[BLUE].readSymbol(bitReader);
                if (bitReader.bitCount() < 15) {
                    bitReader.fill();
                }
                int alpha = tree[ALPHA].readSymbol(bitReader);

                int value = Argb.pack(alpha, red, green, blue);
                data[index] = value;

                if (huffmanInfo.colorCache != null) {
                    huffmanInfo.colorCache.insert(value);
                }
                index++;
            } else if (code < 256 + 24) {
                int lengthSymbol = code - 256;
                int length = getCopyDistance(lengthSymbol);
                int distSymbol = tree[DIST].readSymbol(bitReader);
                int distCode = getCopyDistance(distSymbol);
                int dist = planeCodeToDistance(width, distCode);

                if (index < dist || numValues - index < length) {
                    throw new WebPException("Corrupt VP8L bitstream");
                }

                if (dist == 1) {
                    int value = data[index - 1];
                    for (int i = 0; i < length; i++) {
                        data[index + i] = value;
                    }
                } else {
                    for (int i = 0; i < length; i++) {
                        data[index + i] = data[index + i - dist];
                    }
                    if (huffmanInfo.colorCache != null) {
                        for (int i = 0; i < length; i++) {
                            huffmanInfo.colorCache.insert(data[index + i]);
                        }
                    }
                }
                index += length;
            } else {
                if (huffmanInfo.colorCache == null) {
                    throw new WebPException("Corrupt VP8L bitstream");
                }
                data[index] = huffmanInfo.colorCache.lookup(code - 280);
                index++;

                if (index < nextBlockStart) {
                    LosslessHuffmanTree.PeekedSymbol peeked = tree[GREEN].peekSymbol(bitReader);
                    if (peeked != null && peeked.symbol() >= 280) {
                        bitReader.consume(peeked.bits());
                        data[index] = huffmanInfo.colorCache.lookup(peeked.symbol() - 280);
                        index++;
                    }
                }
            }
        }
    }

    private @Nullable Integer readColorCache() throws WebPException {
        if (bitReader.readBits(1) == 1) {
            int codeBits = bitReader.readBits(4);
            if (codeBits < 1 || codeBits > 11) {
                throw new WebPException("Invalid VP8L color cache bits: " + codeBits);
            }
            return codeBits;
        }
        return null;
    }

    private int getCopyDistance(int prefixCode) throws WebPException {
        if (prefixCode < 4) {
            return prefixCode + 1;
        }
        int extraBits = (prefixCode - 2) >> 1;
        int offset = (2 + (prefixCode & 1)) << extraBits;
        int bits = (int) bitReader.peek(extraBits);
        bitReader.consume(extraBits);
        return offset + bits + 1;
    }

    private int planeCodeToDistance(int xsize, int planeCode) {
        if (planeCode > 120) {
            return planeCode - 120;
        }
        int[] offset = LosslessConstants.DISTANCE_MAP[planeCode - 1];
        int distance = offset[0] + offset[1] * xsize;
        return Math.max(distance, 1);
    }

    @NotNullByDefault
    private static final class HuffmanInfo {
        final int xsize;
        final @Nullable ColorCache colorCache;
        final int[] image;
        final int bits;
        final int mask;
        final List<LosslessHuffmanTree[]> huffmanCodeGroups;

        private HuffmanInfo(int xsize, @Nullable ColorCache colorCache, int[] image, int bits, int mask, List<LosslessHuffmanTree[]> huffmanCodeGroups) {
            this.xsize = xsize;
            this.colorCache = colorCache;
            this.image = image;
            this.bits = bits;
            this.mask = mask;
            this.huffmanCodeGroups = huffmanCodeGroups;
        }

        int getHuffIndex(int x, int y) {
            if (bits == 0) {
                return 0;
            }
            return image[(y >> bits) * xsize + (x >> bits)];
        }
    }

    @NotNullByDefault
    private static final class ColorCache {
        final int colorCacheBits;
        final int[] colorCache;

        private ColorCache(int colorCacheBits) {
            this.colorCacheBits = colorCacheBits;
            this.colorCache = new int[1 << colorCacheBits];
        }

        void insert(int color) {
            int index = (0x1e35a7bd * color) >>> (32 - colorCacheBits);
            colorCache[index] = color;
        }

        int lookup(int index) {
            return colorCache[index];
        }
    }
}
