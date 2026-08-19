// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.glavo.webp.internal.ArrayUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.Argb;

import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

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
    /// Temporary arrays reused while constructing consecutive Huffman trees.
    private final LosslessHuffmanTree.BuildWorkspace huffmanBuildWorkspace =
            new LosslessHuffmanTree.BuildWorkspace();
    /// Code lengths for the code-length alphabet.
    private final byte[] codeLengthCodeLengths = new byte[LosslessConstants.CODE_LENGTH_CODES];
    /// Growable code-length storage reused across alphabets.
    private byte[] codeLengths = ArrayUtils.EMPTY_BYTE_ARRAY;
    private int transformOrderSize;
    private int width;
    private int height;

    /// Creates a decoder for one VP8L payload.
    ///
    /// @param data the encoded VP8L bytes
    public LosslessDecoder(byte[] data) {
        this.bitReader = new LosslessBitReader(data);
    }

    /// Creates a decoder for a VP8L payload range within an existing array.
    ///
    /// @param data the array containing the encoded VP8L bytes
    /// @param offset the first encoded byte
    /// @param length the encoded byte count
    /// @throws IndexOutOfBoundsException if the range lies outside the array
    public LosslessDecoder(byte[] data, int offset, int length) {
        this.bitReader = new LosslessBitReader(data, offset, length);
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
        int transformedWidth = prepareFrame(width, height, implicitDimensions, IntBuffer.wrap(buffer));
        decodeImageStream(transformedWidth, this.height, true, buffer);

        int currentWidth = transformedWidth;
        for (int i = transformOrderSize - 1; i >= 0; i--) {
            LosslessTransforms.Transform transform = transforms[transformOrder[i]];
            switch (transform.kind) {
                case LosslessTransforms.PREDICTOR -> LosslessTransforms.applyPredictorTransform(
                        buffer,
                        currentWidth,
                        this.height,
                        transform.sizeBits,
                        transform.blockData
                );
                case LosslessTransforms.COLOR -> LosslessTransforms.applyColorTransform(
                        buffer,
                        currentWidth,
                        transform.sizeBits,
                        transform.blockData
                );
                case LosslessTransforms.SUBTRACT_GREEN -> LosslessTransforms.applySubtractGreenTransform(buffer);
                case LosslessTransforms.COLOR_INDEXING -> {
                    currentWidth = this.width;
                    LosslessTransforms.applyColorIndexingTransform(
                            buffer,
                            currentWidth,
                            this.height,
                            transform.tableSize,
                            transform.colorTable
                    );
                }
                default -> throw new WebPException("Unknown VP8L transform kind");
            }
        }
    }

    /// Decodes a VP8L frame directly into a writable `ARGB` buffer.
    ///
    /// The buffer must have exactly `width * height` remaining entries. Decoding uses absolute
    /// access and therefore does not change its position or limit.
    ///
    /// @param width the expected width
    /// @param height the expected height
    /// @param implicitDimensions whether the VP8L header should be skipped because dimensions are
    ///                           defined externally, as in ALPH chunks
    /// @param buffer the writable `ARGB` destination buffer
    /// @throws IllegalArgumentException if the destination size does not match the dimensions
    /// @throws ReadOnlyBufferException if the destination is read-only
    /// @throws WebPException if the bitstream is malformed or inconsistent
    public void decodeFrame(
            int width,
            int height,
            boolean implicitDimensions,
            IntBuffer buffer
    ) throws WebPException {
        if (buffer.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        int expectedLength;
        try {
            expectedLength = Math.multiplyExact(width, height);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Frame dimensions are too large: " + width + "x" + height, ex);
        }
        if (buffer.remaining() != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer size does not match VP8L frame dimensions: "
                            + buffer.remaining() + " != " + expectedLength
            );
        }

        IntBuffer output = buffer.slice();
        int transformedWidth = prepareFrame(width, height, implicitDimensions, output);
        decodeImageStream(transformedWidth, this.height, true, output);

        int currentWidth = transformedWidth;
        for (int i = transformOrderSize - 1; i >= 0; i--) {
            LosslessTransforms.Transform transform = transforms[transformOrder[i]];
            switch (transform.kind) {
                case LosslessTransforms.PREDICTOR -> LosslessTransforms.applyPredictorTransform(
                        output,
                        currentWidth,
                        this.height,
                        transform.sizeBits,
                        transform.blockData
                );
                case LosslessTransforms.COLOR -> LosslessTransforms.applyColorTransform(
                        output,
                        currentWidth,
                        transform.sizeBits,
                        transform.blockData
                );
                case LosslessTransforms.SUBTRACT_GREEN ->
                        LosslessTransforms.applySubtractGreenTransform(output);
                case LosslessTransforms.COLOR_INDEXING -> {
                    currentWidth = this.width;
                    LosslessTransforms.applyColorIndexingTransform(
                            output,
                            currentWidth,
                            this.height,
                            transform.tableSize,
                            transform.colorTable
                    );
                }
                default -> throw new WebPException("Unknown VP8L transform kind");
            }
        }
    }

    /// Reads and validates the frame header and its transform descriptions.
    ///
    /// @param width the expected frame width
    /// @param height the expected frame height
    /// @param implicitDimensions whether dimensions are supplied by an enclosing ALPH chunk
    /// @param scratch destination storage that may be reused while decoding metadata images
    /// @return the transformed image width used by entropy decoding
    /// @throws WebPException if the bitstream is malformed or inconsistent
    private int prepareFrame(
            int width,
            int height,
            boolean implicitDimensions,
            IntBuffer scratch
    ) throws WebPException {
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

        return readTransforms(scratch);
    }

    /// Clears state retained while decoding a previous frame.
    private void resetState() {
        Arrays.fill(transforms, null);
        transformOrderSize = 0;
        width = 0;
        height = 0;
    }

    private void decodeImageStream(int xsize, int ysize, boolean readMeta, int[] data) throws WebPException {
        int colorCacheBits = readColorCacheBits();
        ColorCache colorCache = colorCacheBits == 0 ? null : new ColorCache(colorCacheBits);
        HuffmanInfo huffmanInfo = readHuffmanCodes(readMeta, xsize, ysize, colorCache, IntBuffer.wrap(data));
        decodeImageData(xsize, ysize, huffmanInfo, data);
    }

    /// Decodes one lossless image stream directly into an integer buffer.
    ///
    /// @param xsize the current transformed width
    /// @param ysize the image height
    /// @param readMeta whether to read meta-Huffman image data
    /// @param data the position-zero destination buffer
    /// @throws WebPException if the stream is malformed
    private void decodeImageStream(
            int xsize,
            int ysize,
            boolean readMeta,
            IntBuffer data
    ) throws WebPException {
        int colorCacheBits = readColorCacheBits();
        ColorCache colorCache = colorCacheBits == 0 ? null : new ColorCache(colorCacheBits);
        HuffmanInfo huffmanInfo = readHuffmanCodes(readMeta, xsize, ysize, colorCache, data);
        decodeImageData(xsize, ysize, huffmanInfo, data);
    }

    /// Reads transform descriptions and retains their compact metadata.
    ///
    /// @param scratch destination storage that may be reused while decoding metadata images
    /// @return the transformed image width used by the main entropy stream
    /// @throws WebPException if a transform description or metadata image is malformed
    private int readTransforms(IntBuffer scratch) throws WebPException {
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
                    IntBuffer decoded = decodeMetadataImage(blockXSize, blockYSize, scratch);
                    byte[] predictorData = new byte[decoded.limit()];
                    for (int i = 0; i < predictorData.length; i++) {
                        predictorData[i] = (byte) Argb.green(decoded.get(i));
                    }
                    transform = LosslessTransforms.Transform.predictor(sizeBits, predictorData);
                }
                case LosslessTransforms.COLOR -> {
                    int sizeBits = bitReader.readBits(3) + 2;
                    int blockXSize = LosslessTransforms.subsampleSize(xsize, sizeBits);
                    int blockYSize = LosslessTransforms.subsampleSize(height, sizeBits);
                    IntBuffer decoded = decodeMetadataImage(blockXSize, blockYSize, scratch);
                    byte[] transformData = new byte[decoded.limit() * 3];
                    for (int i = 0, offset = 0; i < decoded.limit(); i++) {
                        int value = decoded.get(i);
                        transformData[offset++] = (byte) Argb.red(value);
                        transformData[offset++] = (byte) Argb.green(value);
                        transformData[offset++] = (byte) Argb.blue(value);
                    }
                    transform = LosslessTransforms.Transform.color(sizeBits, transformData);
                }
                case LosslessTransforms.SUBTRACT_GREEN -> transform = LosslessTransforms.Transform.subtractGreen();
                case LosslessTransforms.COLOR_INDEXING -> {
                    int colorTableSize = bitReader.readBits(8) + 1;
                    int[] colorMap = new int[colorTableSize];
                    IntBuffer decoded = decodeMetadataImage(colorTableSize, 1, scratch);
                    decoded.get(0, colorMap);

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

    /// Decodes a transform or entropy metadata image into reusable destination storage.
    ///
    /// The returned position-zero buffer contains exactly `xsize * ysize` decoded pixels. The
    /// supplied scratch buffer is used when large enough; otherwise a heap buffer is allocated.
    ///
    /// @param xsize the metadata image width
    /// @param ysize the metadata image height
    /// @param scratch reusable destination storage
    /// @return the decoded position-zero metadata pixels
    /// @throws WebPException if the metadata image is malformed
    private IntBuffer decodeMetadataImage(int xsize, int ysize, IntBuffer scratch) throws WebPException {
        int pixelCount = xsize * ysize;
        IntBuffer decoded;
        if (pixelCount <= scratch.capacity()) {
            decoded = scratch.duplicate();
            decoded.clear();
            decoded.limit(pixelCount);
        } else {
            decoded = IntBuffer.allocate(pixelCount);
        }
        decodeImageStream(xsize, ysize, false, decoded);
        return decoded;
    }

    private void adjustColorMap(int[] colorMap) {
        for (int i = 1; i < colorMap.length; i++) {
            colorMap[i] = Argb.add(colorMap[i], colorMap[i - 1]);
        }
    }

    /// Reads the Huffman groups and their optional compact entropy-group image.
    ///
    /// @param readMeta whether an entropy-group image may precede the Huffman groups
    /// @param xsize the current image width
    /// @param ysize the current image height
    /// @param colorCache the current color cache, or `null` when disabled
    /// @param scratch destination storage that may be reused for the entropy-group image
    /// @return the decoded Huffman metadata
    /// @throws WebPException if the Huffman metadata is malformed
    private HuffmanInfo readHuffmanCodes(
            boolean readMeta,
            int xsize,
            int ysize,
            @Nullable ColorCache colorCache,
            IntBuffer scratch
    ) throws WebPException {
        int numHuffGroups = 1;
        int huffmanBits = 0;
        int huffmanXSize = 1;
        int huffmanYSize = 1;
        char[] entropyImage = ArrayUtils.EMPTY_CHAR_ARRAY;

        if (readMeta && bitReader.readBits(1) == 1) {
            huffmanBits = bitReader.readBits(3) + 2;
            huffmanXSize = LosslessTransforms.subsampleSize(xsize, huffmanBits);
            huffmanYSize = LosslessTransforms.subsampleSize(ysize, huffmanBits);

            IntBuffer decoded = decodeMetadataImage(huffmanXSize, huffmanYSize, scratch);
            entropyImage = new char[decoded.limit()];
            for (int i = 0; i < entropyImage.length; i++) {
                int value = decoded.get(i);
                int metaHuffCode = (Argb.red(value) << 8) | Argb.green(value);
                if (metaHuffCode >= numHuffGroups) {
                    numHuffGroups = metaHuffCode + 1;
                }
                entropyImage[i] = (char) metaHuffCode;
            }
        }

        LosslessHuffmanTree[][] groups = new LosslessHuffmanTree[numHuffGroups][];
        for (int groupIndex = 0; groupIndex < numHuffGroups; groupIndex++) {
            LosslessHuffmanTree[] group = new LosslessHuffmanTree[HUFFMAN_CODES_PER_META_CODE];
            for (int j = 0; j < HUFFMAN_CODES_PER_META_CODE; j++) {
                int alphabetSize = ALPHABET_SIZE[j];
                if (j == GREEN && colorCache != null) {
                    alphabetSize += 1 << colorCache.colorCacheBits;
                }
                group[j] = readHuffmanCode(alphabetSize);
            }
            groups[groupIndex] = group;
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

        Arrays.fill(codeLengthCodeLengths, (byte) 0);
        int numCodeLengths = 4 + bitReader.readBits(4);
        for (int i = 0; i < numCodeLengths; i++) {
            codeLengthCodeLengths[LosslessConstants.CODE_LENGTH_CODE_ORDER[i]] = (byte) bitReader.readBits(3);
        }
        byte[] codeLengths = readHuffmanCodeLengths(codeLengthCodeLengths, alphabetSize);
        return LosslessHuffmanTree.implicit(codeLengths, alphabetSize, huffmanBuildWorkspace);
    }

    private byte[] readHuffmanCodeLengths(byte[] codeLengthCodeLengths, int numSymbols) throws WebPException {
        LosslessHuffmanTree table = LosslessHuffmanTree.implicit(
                codeLengthCodeLengths,
                codeLengthCodeLengths.length,
                huffmanBuildWorkspace
        );
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

        if (codeLengths.length < numSymbols) {
            codeLengths = new byte[numSymbols];
        } else {
            Arrays.fill(codeLengths, 0, numSymbols, (byte) 0);
        }
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
                codeLengths[symbol++] = (byte) codeLength;
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
                    codeLengths[symbol++] = (byte) value;
                }
            }
        }
        return codeLengths;
    }

    private void decodeImageData(int width, int height, HuffmanInfo huffmanInfo, int[] data) throws WebPException {
        int numValues = width * height;
        LosslessHuffmanTree[] tree = huffmanInfo.huffmanCodeGroups[huffmanInfo.getHuffIndex(0, 0)];
        int index = 0;
        int nextBlockStart = 0;

        while (index < numValues) {
            bitReader.fill();

            if (index >= nextBlockStart) {
                int x = index % width;
                int y = index / width;
                nextBlockStart = Math.min(x | huffmanInfo.mask, width - 1) + y * width + 1;
                tree = huffmanInfo.huffmanCodeGroups[huffmanInfo.getHuffIndex(x, y)];

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

                        Arrays.fill(data, index, index + count, value);
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
                    Arrays.fill(data, index, index + length, value);
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
                    int peeked = tree[GREEN].peekSymbol(bitReader);
                    int peekedSymbol = peeked & 0xFFF;
                    if (peeked >= 0 && peekedSymbol >= 280) {
                        bitReader.consume(peeked >>> 12);
                        data[index] = huffmanInfo.colorCache.lookup(peekedSymbol - 280);
                        index++;
                    }
                }
            }
        }
    }

    /// Decodes entropy-coded pixels directly into an integer buffer.
    ///
    /// @param width the current transformed width
    /// @param height the image height
    /// @param huffmanInfo the decoded Huffman metadata
    /// @param data the position-zero destination buffer
    /// @throws WebPException if the stream is malformed
    private void decodeImageData(
            int width,
            int height,
            HuffmanInfo huffmanInfo,
            IntBuffer data
    ) throws WebPException {
        int numValues = width * height;
        LosslessHuffmanTree[] tree = huffmanInfo.huffmanCodeGroups[huffmanInfo.getHuffIndex(0, 0)];
        int index = 0;
        int nextBlockStart = 0;

        while (index < numValues) {
            bitReader.fill();

            if (index >= nextBlockStart) {
                int x = index % width;
                int y = index / width;
                nextBlockStart = Math.min(x | huffmanInfo.mask, width - 1) + y * width + 1;
                tree = huffmanInfo.huffmanCodeGroups[huffmanInfo.getHuffIndex(x, y)];

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

                        for (int offset = 0; offset < count; offset++) {
                            data.put(index + offset, value);
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
                data.put(index, value);

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
                    int value = data.get(index - 1);
                    for (int offset = 0; offset < length; offset++) {
                        data.put(index + offset, value);
                    }
                } else {
                    for (int offset = 0; offset < length; offset++) {
                        data.put(index + offset, data.get(index + offset - dist));
                    }
                    if (huffmanInfo.colorCache != null) {
                        for (int offset = 0; offset < length; offset++) {
                            huffmanInfo.colorCache.insert(data.get(index + offset));
                        }
                    }
                }
                index += length;
            } else {
                if (huffmanInfo.colorCache == null) {
                    throw new WebPException("Corrupt VP8L bitstream");
                }
                data.put(index, huffmanInfo.colorCache.lookup(code - 280));
                index++;

                if (index < nextBlockStart) {
                    int peeked = tree[GREEN].peekSymbol(bitReader);
                    int peekedSymbol = peeked & 0xFFF;
                    if (peeked >= 0 && peekedSymbol >= 280) {
                        bitReader.consume(peeked >>> 12);
                        data.put(index, huffmanInfo.colorCache.lookup(peekedSymbol - 280));
                        index++;
                    }
                }
            }
        }
    }

    /// Reads the optional color-cache width.
    ///
    /// @return the cache width in bits, or `0` when the stream has no color cache
    /// @throws WebPException if the encoded cache width is invalid
    private int readColorCacheBits() throws WebPException {
        if (bitReader.readBits(1) == 1) {
            int codeBits = bitReader.readBits(4);
            if (codeBits < 1 || codeBits > 11) {
                throw new WebPException("Invalid VP8L color cache bits: " + codeBits);
            }
            return codeBits;
        }
        return 0;
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
        int packed = LosslessConstants.DISTANCE_MAP[planeCode - 1];
        int dx = (packed >>> 4) & 0xF;
        int dy = packed & 0x7;
        if (dx >= 9) {
            dx -= 16;
        }
        int distance = dx + dy * xsize;
        return Math.max(distance, 1);
    }

    @NotNullByDefault
    private static final class HuffmanInfo {
        final int xsize;
        final @Nullable ColorCache colorCache;
        final char @Unmodifiable [] image;
        final int bits;
        final int mask;
        /// Huffman trees indexed first by entropy group and then by channel.
        final LosslessHuffmanTree @Unmodifiable [] @Unmodifiable [] huffmanCodeGroups;

        private HuffmanInfo(int xsize, @Nullable ColorCache colorCache, char @Unmodifiable [] image, int bits, int mask,
                            LosslessHuffmanTree @Unmodifiable [] @Unmodifiable [] huffmanCodeGroups) {
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
