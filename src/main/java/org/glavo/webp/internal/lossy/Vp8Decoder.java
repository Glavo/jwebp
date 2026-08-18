// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.glavo.webp.internal.ArrayUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.lossy.LossyCommon.ChromaMode;
import org.glavo.webp.internal.lossy.LossyCommon.IntraMode;
import org.glavo.webp.internal.lossy.LossyCommon.LumaMode;
import org.glavo.webp.internal.lossy.LossyCommon.Plane;
import org.glavo.webp.internal.lossy.LossyCommon.Segment;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.Objects;

/// Pure-Java VP8 keyframe decoder.
///
/// The implementation follows the structure of the reference decoder in
/// `external/image-webp`: parse the frame header, decode macroblock prediction modes and
/// residual coefficients, reconstruct YUV planes, then apply the in-loop deblocking filter. Only
/// VP8 keyframes are supported because WebP still images and animated frame subchunks store
/// keyframe payloads.
@NotNullByDefault
public final class Vp8Decoder {

    private static final int[] CHROMA_GROUP_STARTS = {5, 7};
    private static final int FILTER_INFO_SEGMENT_MASK = 0x03;
    private static final int FILTER_INFO_LUMA_MODE_SHIFT = 2;
    private static final int FILTER_INFO_LUMA_MODE_MASK = 0x07;
    private static final int FILTER_INFO_COEFFICIENTS_SKIPPED = 1 << 5;
    private static final int FILTER_INFO_NON_ZERO_DCT = 1 << 6;

    /// Encoded VP8 payload shared by the header and token partitions.
    private byte[] input = ArrayUtils.EMPTY_BYTE_ARRAY;

    /// Index of the next unread payload byte.
    private int inputPosition;

    /// Exclusive end index of the VP8 payload.
    private int inputLimit;
    private final LossyArithmeticDecoder headerDecoder = new LossyArithmeticDecoder();

    private int macroblockWidth;
    private int macroblockHeight;
    private byte[] macroblockFilterInfo = ArrayUtils.EMPTY_BYTE_ARRAY;
    private final Vp8Frame frame = new Vp8Frame();

    private boolean segmentsEnabled;
    private boolean segmentsUpdateMap;
    private final Segment[] segments = new Segment[LossyCommon.MAX_SEGMENTS];

    private boolean loopFilterAdjustmentsEnabled;
    private final int[] refDelta = new int[4];
    private final int[] modeDelta = new int[4];

    private final LossyArithmeticDecoder[] partitions = {
            new LossyArithmeticDecoder(),
            new LossyArithmeticDecoder(),
            new LossyArithmeticDecoder(),
            new LossyArithmeticDecoder(),
            new LossyArithmeticDecoder(),
            new LossyArithmeticDecoder(),
            new LossyArithmeticDecoder(),
            new LossyArithmeticDecoder()
    };
    private int numPartitions = 1;

    private final int[] segmentProbs = {255, 255, 255};
    /// Coefficient probabilities, shared with the immutable defaults until the frame updates one.
    private int[] tokenProbs = LossyTables.FLAT_COEFF_PROBS;
    /// Mutable coefficient probabilities allocated only for streams that update the defaults.
    private int @Nullable [] mutableTokenProbs;

    /// Probability of a false skip-coefficients flag, or `-1` when the flag is absent.
    private int probSkipFalse = -1;
    private byte[] topBpred = ArrayUtils.EMPTY_BYTE_ARRAY;
    private byte[] topComplexity = ArrayUtils.EMPTY_BYTE_ARRAY;
    private final byte[] leftBpred = new byte[4];
    private final byte[] leftComplexity = new byte[9];

    private byte[] topBorderY = ArrayUtils.EMPTY_BYTE_ARRAY;
    private byte[] leftBorderY = ArrayUtils.EMPTY_BYTE_ARRAY;
    private byte[] topBorderU = ArrayUtils.EMPTY_BYTE_ARRAY;
    private byte[] leftBorderU = ArrayUtils.EMPTY_BYTE_ARRAY;
    private byte[] topBorderV = ArrayUtils.EMPTY_BYTE_ARRAY;
    private byte[] leftBorderV = ArrayUtils.EMPTY_BYTE_ARRAY;
    private final int[] residualDataScratch = new int[384];
    private final int[] y2BlockScratch = new int[16];
    private final int[] zeroResidualData = new int[384];
    private final byte[] lumaWorkspace = new byte[LossyPrediction.LUMA_BLOCK_SIZE];
    private final byte[] uWorkspace = new byte[LossyPrediction.CHROMA_BLOCK_SIZE];
    private final byte[] vWorkspace = new byte[LossyPrediction.CHROMA_BLOCK_SIZE];
    /// Prediction and coefficient metadata reused for every macroblock.
    private final MacroBlock macroBlock = new MacroBlock();

    /// Creates a reusable decoder with no input attached.
    ///
    /// The decoder is stateful and must not be used concurrently. Retained work arrays grow to fit
    /// the largest frame decoded by this instance.
    public Vp8Decoder() {
        initializeSegments();
    }

    /// Creates mutable segment records retained for the lifetime of this decoder.
    private void initializeSegments() {
        for (int i = 0; i < segments.length; i++) {
            segments[i] = new Segment();
        }
    }

    /// Selects an encoded buffer range and resets per-frame state.
    ///
    /// The caller's position and limit are not changed.
    ///
    /// @param input the encoded VP8 payload
    private void resetInput(ByteBuffer input) {
        Objects.requireNonNull(input, "input");
        if (input.hasArray()) {
            this.input = input.array();
            this.inputPosition = input.arrayOffset() + input.position();
            this.inputLimit = this.inputPosition + input.remaining();
        } else {
            byte[] copy = new byte[input.remaining()];
            input.duplicate().get(copy);
            this.input = copy;
            this.inputPosition = 0;
            this.inputLimit = copy.length;
        }
        resetFrameState();
    }

    /// Selects an encoded payload array and resets per-frame state.
    ///
    /// @param input the encoded VP8 payload
    private void resetInput(byte[] input) {
        this.input = Objects.requireNonNull(input, "input");
        this.inputPosition = 0;
        this.inputLimit = input.length;
        resetFrameState();
    }

    /// Restores bitstream-dependent fields to their keyframe defaults.
    private void resetFrameState() {
        segmentsEnabled = false;
        segmentsUpdateMap = false;
        loopFilterAdjustmentsEnabled = false;
        numPartitions = 1;
        probSkipFalse = -1;
        tokenProbs = LossyTables.FLAT_COEFF_PROBS;
        Arrays.fill(refDelta, 0);
        Arrays.fill(modeDelta, 0);
        Arrays.fill(segmentProbs, 255);
        for (Segment segment : segments) {
            segment.ydc = 0;
            segment.yac = 0;
            segment.y2dc = 0;
            segment.y2ac = 0;
            segment.uvdc = 0;
            segment.uvac = 0;
            segment.deltaValues = false;
            segment.quantizerLevel = 0;
            segment.loopFilterLevel = 0;
        }
    }

    /// Reads the VP8 frame header and initializes partition state.
    ///
    /// The supplied buffer is read from its current position without mutating the caller's
    /// `position()` or `limit()`.
    ///
    /// @param input the raw VP8 payload buffer
    /// @return a frame object initialized from the VP8 header
    /// @throws WebPException if the VP8 stream is malformed
    static Vp8Frame decodeFrameHeader(ByteBuffer input) throws WebPException {
        Vp8Decoder decoder = new Vp8Decoder();
        decoder.resetInput(input);
        decoder.readFrameHeader();
        return decoder.frame;
    }

    /// Decodes one raw VP8 frame payload to packed `ARGB` pixels.
    ///
    /// The supplied buffer is read from its current position without mutating the caller's
    /// `position()` or `limit()`.
    ///
    /// @param input the raw VP8 frame payload
    /// @param fancyUpsampling whether to use the high-quality chroma upsampler
    /// @return tightly packed non-premultiplied `ARGB` pixels
    /// @throws WebPException if the VP8 bitstream is malformed
    public static int[] decodeArgb(ByteBuffer input, boolean fancyUpsampling) throws WebPException {
        Vp8Decoder decoder = new Vp8Decoder();
        decoder.resetInput(input);
        Vp8Frame frame = decoder.decodeFrameInternal();
        int[] argb = new int[frame.width * frame.height];
        frame.fillArgb(argb, fancyUpsampling);
        return argb;
    }

    /// Decodes one raw VP8 frame payload into an existing packed `ARGB` buffer.
    ///
    /// The supplied buffer must contain exactly one entry per decoded frame pixel. The input is
    /// read from its current position without mutating the caller's `position()` or `limit()`.
    ///
    /// @param input the raw VP8 frame payload
    /// @param fancyUpsampling whether to use the high-quality chroma upsampler
    /// @param argb the destination for tightly packed non-premultiplied `ARGB` pixels
    /// @throws IllegalArgumentException if the destination size does not match the frame dimensions
    /// @throws WebPException if the VP8 bitstream is malformed
    public static void decodeArgb(ByteBuffer input, boolean fancyUpsampling, int[] argb) throws WebPException {
        Vp8Decoder decoder = new Vp8Decoder();
        decoder.resetInput(input);
        Vp8Frame frame = decoder.decodeFrameInternal();
        int expectedLength = frame.width * frame.height;
        if (argb.length != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer length does not match VP8 frame dimensions: "
                            + argb.length + " != " + expectedLength
            );
        }
        frame.fillArgb(argb, fancyUpsampling);
    }

    /// Decodes one raw VP8 frame payload directly into an existing packed `ARGB` buffer.
    ///
    /// The destination must have exactly one remaining entry per decoded frame pixel. Neither the
    /// input nor destination position or limit is changed.
    ///
    /// @param input the raw VP8 frame payload
    /// @param fancyUpsampling whether to use the high-quality chroma upsampler
    /// @param argb the destination for tightly packed non-premultiplied `ARGB` pixels
    /// @throws IllegalArgumentException if the destination size does not match the frame dimensions
    /// @throws ReadOnlyBufferException if the destination is read-only
    /// @throws WebPException if the VP8 bitstream is malformed
    public static void decodeArgb(ByteBuffer input, boolean fancyUpsampling, IntBuffer argb) throws WebPException {
        if (argb.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        Vp8Decoder decoder = new Vp8Decoder();
        decoder.resetInput(input);
        Vp8Frame frame = decoder.decodeFrameInternal();
        int expectedLength = frame.width * frame.height;
        if (argb.remaining() != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer size does not match VP8 frame dimensions: "
                            + argb.remaining() + " != " + expectedLength
            );
        }
        frame.fillArgb(argb, fancyUpsampling);
    }

    /// Decodes one array-backed VP8 payload into an existing packed `ARGB` array.
    ///
    /// The payload array is read directly and must not be modified during this call. Retained work
    /// arrays grow to fit the largest frame decoded by this instance. The decoder must not be used
    /// concurrently.
    ///
    /// @param input the raw VP8 frame payload
    /// @param fancyUpsampling whether to use the high-quality chroma upsampler
    /// @param argb the destination for tightly packed non-premultiplied `ARGB` pixels
    /// @throws IllegalArgumentException if the destination size does not match the frame dimensions
    /// @throws WebPException if the VP8 bitstream is malformed
    public void decodeArgb(
            byte[] input,
            boolean fancyUpsampling,
            int[] argb
    ) throws WebPException {
        resetInput(input);
        Vp8Frame frame = decodeFrameInternal();
        int expectedLength = frame.width * frame.height;
        if (argb.length != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer length does not match VP8 frame dimensions: "
                            + argb.length + " != " + expectedLength
            );
        }
        frame.fillArgb(argb, fancyUpsampling);
    }

    /// Decodes one array-backed VP8 payload into the RGB bits of an `ARGB` array while preserving
    /// its existing alpha bytes.
    ///
    /// The payload array is read directly and must not be modified during this call. Chroma uses
    /// nearest-neighbor upsampling. The decoder must not be used concurrently.
    ///
    /// @param input the raw VP8 frame payload
    /// @param argb the exact-sized destination containing reconstructed alpha bytes
    /// @throws IllegalArgumentException if the destination size does not match the frame dimensions
    /// @throws WebPException if the VP8 bitstream is malformed
    public void decodeRgbPreservingAlpha(
            byte[] input,
            int[] argb
    ) throws WebPException {
        resetInput(input);
        Vp8Frame frame = decodeFrameInternal();
        int expectedLength = frame.width * frame.height;
        if (argb.length != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer length does not match VP8 frame dimensions: "
                            + argb.length + " != " + expectedLength
            );
        }
        frame.fillRgbPreservingAlpha(argb);
    }

    /// Decodes one array-backed VP8 payload directly into an integer buffer.
    ///
    /// The payload array is read directly and must not be modified during this call. The decoder
    /// must not be used concurrently. The destination position and limit are not changed.
    ///
    /// @param input the raw VP8 frame payload
    /// @param fancyUpsampling whether to use the high-quality chroma upsampler
    /// @param argb the destination for tightly packed non-premultiplied `ARGB` pixels
    /// @throws IllegalArgumentException if the destination size does not match the frame dimensions
    /// @throws ReadOnlyBufferException if the destination is read-only
    /// @throws WebPException if the VP8 bitstream is malformed
    public void decodeArgb(
            byte[] input,
            boolean fancyUpsampling,
            IntBuffer argb
    ) throws WebPException {
        if (argb.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        resetInput(input);
        Vp8Frame frame = decodeFrameInternal();
        int expectedLength = frame.width * frame.height;
        if (argb.remaining() != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer size does not match VP8 frame dimensions: "
                            + argb.remaining() + " != " + expectedLength
            );
        }
        frame.fillArgb(argb, fancyUpsampling);
    }

    /// Decodes one array-backed VP8 payload into the RGB bits of an integer buffer while
    /// preserving its existing alpha bytes.
    ///
    /// The payload array is read directly and must not be modified during this call. Chroma uses
    /// nearest-neighbor upsampling. The decoder must not be used concurrently. The destination
    /// position and limit are not changed.
    ///
    /// @param input the raw VP8 frame payload
    /// @param argb the exact-sized destination containing reconstructed alpha bytes
    /// @throws IllegalArgumentException if the destination size does not match the frame dimensions
    /// @throws ReadOnlyBufferException if the destination is read-only
    /// @throws WebPException if the VP8 bitstream is malformed
    public void decodeRgbPreservingAlpha(
            byte[] input,
            IntBuffer argb
    ) throws WebPException {
        if (argb.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        resetInput(input);
        Vp8Frame frame = decodeFrameInternal();
        int expectedLength = frame.width * frame.height;
        if (argb.remaining() != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer size does not match VP8 frame dimensions: "
                            + argb.remaining() + " != " + expectedLength
            );
        }
        frame.fillRgbPreservingAlpha(argb);
    }

    private void updateTokenProbabilities() throws WebPException {
        int[] updateProbabilities = LossyTables.FLAT_COEFF_UPDATE_PROBS;
        for (int probabilityIndex = 0; probabilityIndex < updateProbabilities.length; probabilityIndex++) {
            if (headerDecoder.readBool(updateProbabilities[probabilityIndex])) {
                int updated = headerDecoder.readLiteral(8);
                if (tokenProbs == LossyTables.FLAT_COEFF_PROBS) {
                    if (mutableTokenProbs == null) {
                        mutableTokenProbs = LossyTables.FLAT_COEFF_PROBS.clone();
                    } else {
                        System.arraycopy(
                                LossyTables.FLAT_COEFF_PROBS,
                                0,
                                mutableTokenProbs,
                                0,
                                LossyTables.FLAT_COEFF_PROBS.length
                        );
                    }
                    tokenProbs = mutableTokenProbs;
                }
                tokenProbs[probabilityIndex] = updated;
            }
        }
        headerDecoder.ensureNotPastEof();
    }

    private void initPartitions(int partitionCount) throws WebPException {
        if (partitionCount > 1) {
            int sizesOffset = readExactly(3 * partitionCount - 3);
            for (int i = 0; i < partitionCount - 1; i++) {
                int sizeOffset = sizesOffset + i * 3;
                int partitionSize = Short.toUnsignedInt(ArrayUtils.getShortLE(input, sizeOffset))
                        | (Byte.toUnsignedInt(input[sizeOffset + 2]) << 16);
                int partitionOffset = readExactly(partitionSize);
                partitions[i].init(input, partitionOffset, partitionSize);
            }
        }
        int finalPartitionSize = inputLimit - inputPosition;
        int finalPartitionOffset = readExactly(finalPartitionSize);
        partitions[partitionCount - 1].init(input, finalPartitionOffset, finalPartitionSize);
    }

    private void readQuantizationIndices() throws WebPException {
        int yacAbs = headerDecoder.readLiteral(7);
        int ydcDelta = headerDecoder.readOptionalSignedValue(4);
        int y2dcDelta = headerDecoder.readOptionalSignedValue(4);
        int y2acDelta = headerDecoder.readOptionalSignedValue(4);
        int uvdcDelta = headerDecoder.readOptionalSignedValue(4);
        int uvacDelta = headerDecoder.readOptionalSignedValue(4);

        int segmentCount = segmentsEnabled ? LossyCommon.MAX_SEGMENTS : 1;
        for (int i = 0; i < segmentCount; i++) {
            int base = segmentsEnabled
                    ? (segments[i].deltaValues ? segments[i].quantizerLevel + yacAbs : segments[i].quantizerLevel)
                    : yacAbs;

            segments[i].ydc = dcQuant(base + ydcDelta);
            segments[i].yac = acQuant(base);
            segments[i].y2dc = (short) (dcQuant(base + y2dcDelta) * 2);
            segments[i].y2ac = (short) ((acQuant(base + y2acDelta) * 155) / 100);
            segments[i].uvdc = dcQuant(base + uvdcDelta);
            segments[i].uvac = acQuant(base + uvacDelta);

            if (segments[i].y2ac < 8) {
                segments[i].y2ac = 8;
            }
            if (segments[i].uvdc > 132) {
                segments[i].uvdc = 132;
            }
        }

        headerDecoder.ensureNotPastEof();
    }

    private void readLoopFilterAdjustments() throws WebPException {
        if (headerDecoder.readFlag()) {
            for (int i = 0; i < 4; i++) {
                refDelta[i] = headerDecoder.readOptionalSignedValue(6);
            }
            for (int i = 0; i < 4; i++) {
                modeDelta[i] = headerDecoder.readOptionalSignedValue(6);
            }
        }
        headerDecoder.ensureNotPastEof();
    }

    private void readSegmentUpdates() throws WebPException {
        segmentsUpdateMap = headerDecoder.readFlag();
        boolean updateSegmentFeatureData = headerDecoder.readFlag();

        if (updateSegmentFeatureData) {
            boolean segmentFeatureMode = headerDecoder.readFlag();
            for (Segment segment : segments) {
                segment.deltaValues = !segmentFeatureMode;
            }
            for (Segment segment : segments) {
                segment.quantizerLevel = (byte) headerDecoder.readOptionalSignedValue(7);
            }
            for (Segment segment : segments) {
                segment.loopFilterLevel = (byte) headerDecoder.readOptionalSignedValue(6);
            }
        }

        if (segmentsUpdateMap) {
            for (int i = 0; i < 3; i++) {
                boolean update = headerDecoder.readFlag();
                segmentProbs[i] = update ? headerDecoder.readLiteral(8) : 255;
            }
        }

        headerDecoder.ensureNotPastEof();
    }

    private void readFrameHeader() throws WebPException {
        int tag = readU24LE();
        if ((tag & 1) != 0) {
            throw new WebPException("Only VP8 keyframes are supported");
        }
        frame.keyframe = true;
        frame.version = (byte) ((tag >> 1) & 0x7);
        frame.forDisplay = ((tag >> 4) & 1) != 0;

        int firstPartitionSize = tag >> 5;
        int signature0 = readU8();
        int signature1 = readU8();
        int signature2 = readU8();
        if (signature0 != 0x9D || signature1 != 0x01 || signature2 != 0x2A) {
            throw new WebPException("Invalid VP8 frame signature");
        }

        int widthBits = readU16LE();
        int heightBits = readU16LE();
        frame.width = widthBits & 0x3FFF;
        frame.height = heightBits & 0x3FFF;
        if (frame.width <= 0 || frame.height <= 0) {
            throw new WebPException("Invalid VP8 frame dimensions");
        }

        macroblockWidth = (frame.width + 15) / 16;
        macroblockHeight = (frame.height + 15) / 16;
        int macroblockCount = macroblockWidth * macroblockHeight;
        if (macroblockFilterInfo.length < macroblockCount) {
            macroblockFilterInfo = new byte[macroblockCount];
        }
        int topBpredLength = macroblockWidth * 4;
        if (topBpred.length < topBpredLength) {
            topBpred = new byte[topBpredLength];
        }
        Arrays.fill(topBpred, 0, topBpredLength, (byte) IntraMode.DC);
        int topComplexityLength = macroblockWidth * 9;
        if (topComplexity.length < topComplexityLength) {
            topComplexity = new byte[topComplexityLength];
        }
        Arrays.fill(topComplexity, 0, topComplexityLength, (byte) 0);

        int yBufferLength = macroblockWidth * 16 * macroblockHeight * 16;
        int chromaBufferLength = macroblockWidth * 8 * macroblockHeight * 8;
        if (frame.yBuffer.length < yBufferLength) {
            frame.yBuffer = new byte[yBufferLength];
        }
        if (frame.uBuffer.length < chromaBufferLength) {
            frame.uBuffer = new byte[chromaBufferLength];
        }
        if (frame.vBuffer.length < chromaBufferLength) {
            frame.vBuffer = new byte[chromaBufferLength];
        }

        topBorderY = prepareFilled(topBorderY, frame.width + 20, (byte) 127);
        leftBorderY = prepareFilled(leftBorderY, 17, (byte) 129);
        topBorderU = prepareFilled(topBorderU, 8 * macroblockWidth, (byte) 127);
        leftBorderU = prepareFilled(leftBorderU, 9, (byte) 129);
        topBorderV = prepareFilled(topBorderV, 8 * macroblockWidth, (byte) 127);
        leftBorderV = prepareFilled(leftBorderV, 9, (byte) 129);

        int firstPartitionOffset = readExactly(firstPartitionSize);
        headerDecoder.init(input, firstPartitionOffset, firstPartitionSize);

        int colorSpace = headerDecoder.readLiteral(1);
        frame.pixelType = (byte) headerDecoder.readLiteral(1);
        if (colorSpace != 0) {
            throw new WebPException("Unsupported VP8 color space: " + colorSpace);
        }

        segmentsEnabled = headerDecoder.readFlag();
        if (segmentsEnabled) {
            readSegmentUpdates();
        }

        frame.filterType = headerDecoder.readFlag();
        frame.filterLevel = (byte) headerDecoder.readLiteral(6);
        frame.sharpnessLevel = (byte) headerDecoder.readLiteral(3);

        loopFilterAdjustmentsEnabled = headerDecoder.readFlag();
        if (loopFilterAdjustmentsEnabled) {
            readLoopFilterAdjustments();
        }

        int partitionCount = 1 << headerDecoder.readLiteral(2);
        headerDecoder.ensureNotPastEof();

        numPartitions = partitionCount;
        initPartitions(partitionCount);
        readQuantizationIndices();

        headerDecoder.readLiteral(1);
        updateTokenProbabilities();

        int macroblockNoSkipCoeff = headerDecoder.readLiteral(1);
        probSkipFalse = macroblockNoSkipCoeff == 1 ? headerDecoder.readLiteral(8) : -1;
        headerDecoder.ensureNotPastEof();
    }

    /// Reads prediction and segment metadata into a reusable macroblock workspace.
    ///
    /// @param macroblockX the horizontal macroblock index
    /// @param macroBlock the workspace to overwrite
    /// @throws WebPException if the header partition is corrupt
    @SuppressWarnings("MagicConstant")
    private void readMacroblockHeader(int macroblockX, MacroBlock macroBlock) throws WebPException {
        macroBlock.reset();
        int topBpredOffset = macroblockX * 4;

        if (segmentsEnabled && segmentsUpdateMap) {
            macroBlock.segmentId = headerDecoder.readWithTree(LossyTables.SEGMENT_ID_TREE, segmentProbs);
        }

        macroBlock.coefficientsSkipped = probSkipFalse >= 0 && headerDecoder.readBool(probSkipFalse);

        // Static mode trees contain only valid leaves, which are also the only values stored as neighbors.
        @LumaMode int lumaMode = headerDecoder.readWithTree(
                LossyTables.KEYFRAME_YMODE_TREE,
                LossyTables.KEYFRAME_YMODE_PROBS
        );
        macroBlock.lumaMode = lumaMode;

        if (lumaMode == LumaMode.B) {
            long intraModes = 0L;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    @IntraMode int topMode = topBpred[topBpredOffset + x] & 0xFF;
                    @IntraMode int leftMode = leftBpred[y] & 0xFF;
                    @IntraMode int blockMode = headerDecoder.readWithTree(
                            LossyTables.KEYFRAME_BPRED_MODE_TREE,
                            LossyTables.KEYFRAME_BPRED_MODE_PROBS[topMode][leftMode]
                    );
                    intraModes = LossyCommon.setIntraMode(intraModes, x + y * 4, blockMode);
                    topBpred[topBpredOffset + x] = (byte) blockMode;
                    leftBpred[y] = (byte) blockMode;
                }
            }
            macroBlock.intraModes = intraModes;
        } else {
            @IntraMode int sharedMode = LossyCommon.toIntraMode(lumaMode);
            Arrays.fill(leftBpred, (byte) sharedMode);
            Arrays.fill(topBpred, topBpredOffset, topBpredOffset + 4, (byte) sharedMode);
        }

        @ChromaMode int chromaMode = headerDecoder.readWithTree(
                LossyTables.KEYFRAME_UV_MODE_TREE,
                LossyTables.KEYFRAME_UV_MODE_PROBS
        );
        macroBlock.chromaMode = chromaMode;

        headerDecoder.ensureNotPastEof();
    }

    /*
     * Luma reconstruction uses the VP8 prediction workspace layout from RFC 6386 section 12:
     * a 16x16 macroblock plus a left border, a top border and four extra top-right pixels. The
     * border buffers are updated after each macroblock so later predictions can access already
     * reconstructed neighbors without touching the full frame plane.
     */
    private void intraPredictLuma(int macroblockX, int macroblockY, MacroBlock macroBlock, int[] residualData) {
        int stride = LossyPrediction.LUMA_STRIDE;
        int lumaWidth = macroblockWidth * 16;
        byte[] workspace = lumaWorkspace;
        LossyPrediction.fillBorderLuma(
                workspace,
                macroblockX,
                macroblockY,
                macroblockWidth,
                topBorderY,
                leftBorderY
        );

        switch (macroBlock.lumaMode) {
            case LumaMode.V -> LossyPrediction.predictVpred(workspace, 16, 1, 1, stride);
            case LumaMode.H -> LossyPrediction.predictHpred(workspace, 16, 1, 1, stride);
            case LumaMode.TM -> LossyPrediction.predictTmpred(workspace, 16, 1, 1, stride);
            case LumaMode.DC -> LossyPrediction.predictDcpred(
                    workspace,
                    16,
                    stride,
                    macroblockY != 0,
                    macroblockX != 0
            );
            case LumaMode.B -> LossyPrediction.predict4x4(
                    workspace,
                    stride,
                    macroBlock.intraModes,
                    residualData
            );
            default -> throw new AssertionError("Unexpected luma mode: " + macroBlock.lumaMode);
        }

        if (macroBlock.lumaMode != LumaMode.B) {
            for (int blockY = 0; blockY < 4; blockY++) {
                for (int blockX = 0; blockX < 4; blockX++) {
                    int blockIndex = blockX + blockY * 4;
                    LossyPrediction.addResidue(
                            workspace,
                            residualData,
                            blockIndex * 16,
                            1 + blockY * 4,
                            1 + blockX * 4,
                            stride
                    );
                }
            }
        }

        leftBorderY[0] = workspace[16];
        for (int i = 0; i < 16; i++) {
            leftBorderY[i + 1] = workspace[(i + 1) * stride + 16];
        }
        System.arraycopy(workspace, 16 * stride + 1, topBorderY, macroblockX * 16, 16);

        for (int y = 0; y < 16; y++) {
            System.arraycopy(
                    workspace,
                    (1 + y) * stride + 1,
                    frame.yBuffer,
                    (macroblockY * 16 + y) * lumaWidth + macroblockX * 16,
                    16
            );
        }
    }

    private void intraPredictChroma(int macroblockX, int macroblockY, MacroBlock macroBlock, int[] residualData) {
        int stride = LossyPrediction.CHROMA_STRIDE;
        int chromaWidth = macroblockWidth * 8;
        byte[] uWorkspace = this.uWorkspace;
        byte[] vWorkspace = this.vWorkspace;
        LossyPrediction.fillBorderChroma(uWorkspace, macroblockX, macroblockY, topBorderU, leftBorderU);
        LossyPrediction.fillBorderChroma(vWorkspace, macroblockX, macroblockY, topBorderV, leftBorderV);

        switch (macroBlock.chromaMode) {
            case ChromaMode.DC -> {
                LossyPrediction.predictDcpred(uWorkspace, 8, stride, macroblockY != 0, macroblockX != 0);
                LossyPrediction.predictDcpred(vWorkspace, 8, stride, macroblockY != 0, macroblockX != 0);
            }
            case ChromaMode.V -> {
                LossyPrediction.predictVpred(uWorkspace, 8, 1, 1, stride);
                LossyPrediction.predictVpred(vWorkspace, 8, 1, 1, stride);
            }
            case ChromaMode.H -> {
                LossyPrediction.predictHpred(uWorkspace, 8, 1, 1, stride);
                LossyPrediction.predictHpred(vWorkspace, 8, 1, 1, stride);
            }
            case ChromaMode.TM -> {
                LossyPrediction.predictTmpred(uWorkspace, 8, 1, 1, stride);
                LossyPrediction.predictTmpred(vWorkspace, 8, 1, 1, stride);
            }
            default -> throw new AssertionError("Unexpected chroma mode: " + macroBlock.chromaMode);
        }

        for (int blockY = 0; blockY < 2; blockY++) {
            for (int blockX = 0; blockX < 2; blockX++) {
                int blockIndex = blockX + blockY * 2;
                int y0 = 1 + blockY * 4;
                int x0 = 1 + blockX * 4;
                LossyPrediction.addResidue(uWorkspace, residualData, 16 * 16 + blockIndex * 16, y0, x0, stride);
                LossyPrediction.addResidue(vWorkspace, residualData, 20 * 16 + blockIndex * 16, y0, x0, stride);
            }
        }

        setChromaBorder(leftBorderU, topBorderU, uWorkspace, macroblockX);
        setChromaBorder(leftBorderV, topBorderV, vWorkspace, macroblockX);

        for (int y = 0; y < 8; y++) {
            int planeOffset = (macroblockY * 8 + y) * chromaWidth + macroblockX * 8;
            int workspaceOffset = (1 + y) * stride + 1;
            System.arraycopy(uWorkspace, workspaceOffset, frame.uBuffer, planeOffset, 8);
            System.arraycopy(vWorkspace, workspaceOffset, frame.vBuffer, planeOffset, 8);
        }
    }

    private boolean readCoefficients(
            int[] block,
            int blockOffset,
            int partition,
            @Plane int plane,
            int complexity,
            short dcq,
            short acq
    ) throws WebPException {
        assert complexity <= 2;

        int firstCoeff = plane == Plane.Y_COEFF_1 ? 1 : 0;
        LossyArithmeticDecoder decoder = partitions[partition];
        int probabilityPlaneOffset = plane * LossyTables.COEFF_PROBABILITY_COUNT_PER_PLANE;

        int complexityState = complexity;
        boolean hasCoefficients = false;
        int i = firstCoeff;
        while (i < 16) {
            int probabilityOffset = probabilityPlaneOffset
                    + LossyTables.COEFF_BAND_PROBABILITY_OFFSETS[i]
                    + complexityState * LossyTables.COEFF_TOKEN_PROBABILITY_COUNT;

            if (!decoder.readBool(tokenProbs[probabilityOffset])) {
                break;
            }

            while (!decoder.readBool(tokenProbs[probabilityOffset + 1])) {
                hasCoefficients = true;
                complexityState = 0;
                if (++i == 16) {
                    decoder.ensureNotPastEof();
                    return true;
                }
                probabilityOffset = probabilityPlaneOffset
                        + LossyTables.COEFF_BAND_PROBABILITY_OFFSETS[i];
            }

            int absoluteValue;
            if (!decoder.readBool(tokenProbs[probabilityOffset + 2])) {
                absoluteValue = 1;
            } else {
                absoluteValue = readLargeCoefficientValue(decoder, probabilityOffset);
            }

            complexityState = absoluteValue == 1 ? 1 : 2;
            absoluteValue = decoder.readSigned(absoluteValue);

            int zigzag = LossyTables.ZIGZAG[i];
            block[blockOffset + zigzag] = absoluteValue * (zigzag > 0 ? acq : dcq);
            hasCoefficients = true;
            i++;
        }

        decoder.ensureNotPastEof();
        return hasCoefficients;
    }

    /// Reads a coefficient magnitude greater than one from the token probability branches.
    ///
    /// @param decoder the active coefficient-partition decoder
    /// @param probabilityOffset the first token probability for the current coefficient
    /// @return a coefficient magnitude in the range `2` through `2114`
    private int readLargeCoefficientValue(LossyArithmeticDecoder decoder, int probabilityOffset) {
        int[] probabilities = tokenProbs;
        if (!decoder.readBool(probabilities[probabilityOffset + 3])) {
            if (!decoder.readBool(probabilities[probabilityOffset + 4])) {
                return 2;
            }
            return 3 + (decoder.readBool(probabilities[probabilityOffset + 5]) ? 1 : 0);
        }

        if (!decoder.readBool(probabilities[probabilityOffset + 6])) {
            if (!decoder.readBool(probabilities[probabilityOffset + 7])) {
                return 5 + (decoder.readBool(159) ? 1 : 0);
            }
            return 7
                    + (decoder.readBool(165) ? 2 : 0)
                    + (decoder.readBool(145) ? 1 : 0);
        }

        int highCategoryBit = decoder.readBool(probabilities[probabilityOffset + 8]) ? 1 : 0;
        int lowCategoryBit = decoder.readBool(probabilities[probabilityOffset + 9 + highCategoryBit]) ? 1 : 0;
        int category = (highCategoryBit << 1) | lowCategoryBit;
        int categoryOffset = category * LossyTables.LARGE_DCT_CATEGORY_STRIDE;
        int extra = 0;
        int[] categoryProbabilities = LossyTables.LARGE_DCT_CATEGORY_PROBABILITIES;
        for (int index = categoryOffset; categoryProbabilities[index] != 0; index++) {
            extra = extra + extra + (decoder.readBool(categoryProbabilities[index]) ? 1 : 0);
        }
        return LossyTables.LARGE_DCT_CATEGORY_BASE[category] + extra;
    }

    /*
     * Residual decoding follows the VP8 block order used by the reference implementation:
     * optional Y2 first, then 16 luma 4x4 blocks, then two 2x2 chroma groups. Complexity context
     * is propagated through the cached top/left macroblock state so coefficient probabilities can
     * adapt across block boundaries.
     */
    private int[] readResidualData(MacroBlock macroBlock, int macroblockX, int partition) throws WebPException {
        int segmentIndex = macroBlock.segmentId;
        int[] blocks = residualDataScratch;
        Arrays.fill(blocks, 0);
        @Plane int plane = macroBlock.lumaMode == LumaMode.B ? Plane.Y_COEFF_0 : Plane.Y2;
        int topComplexityOffset = macroblockX * 9;

        if (plane == Plane.Y2) {
            int complexity = topComplexity[topComplexityOffset] + leftComplexity[0];
            int[] y2Block = y2BlockScratch;
            Arrays.fill(y2Block, 0);
            boolean present = readCoefficients(
                    y2Block,
                    0,
                    partition,
                    plane,
                    complexity,
                    segments[segmentIndex].y2dc,
                    segments[segmentIndex].y2ac
            );

            leftComplexity[0] = present ? (byte) 1 : 0;
            topComplexity[topComplexityOffset] = present ? (byte) 1 : 0;

            LossyTransform.iwht4x4(y2Block);
            for (int k = 0; k < 16; k++) {
                blocks[16 * k] = y2Block[k];
            }

            plane = Plane.Y_COEFF_1;
        }

        for (int blockY = 0; blockY < 4; blockY++) {
            int leftValue = leftComplexity[blockY + 1];
            for (int blockX = 0; blockX < 4; blockX++) {
                int blockIndex = blockX + blockY * 4;
                int blockOffset = blockIndex * 16;
                int complexity = topComplexity[topComplexityOffset + blockX + 1] + leftValue;

                boolean present = readCoefficients(
                        blocks,
                        blockOffset,
                        partition,
                        plane,
                        complexity,
                        segments[segmentIndex].ydc,
                        segments[segmentIndex].yac
                );

                if (blocks[blockOffset] != 0 || present) {
                    macroBlock.nonZeroDct = true;
                    LossyTransform.idct4x4(blocks, blockOffset);
                }

                leftValue = present ? 1 : 0;
                topComplexity[topComplexityOffset + blockX + 1] = present ? (byte) 1 : 0;
            }
            leftComplexity[blockY + 1] = (byte) leftValue;
        }

        for (int groupStart : CHROMA_GROUP_STARTS) {
            for (int blockY = 0; blockY < 2; blockY++) {
                int leftValue = leftComplexity[blockY + groupStart];
                for (int blockX = 0; blockX < 2; blockX++) {
                    int blockIndex = blockX + blockY * 2 + (groupStart == 5 ? 16 : 20);
                    int blockOffset = blockIndex * 16;
                    int complexity = topComplexity[topComplexityOffset + blockX + groupStart] + leftValue;

                    boolean present = readCoefficients(
                            blocks,
                            blockOffset,
                            partition,
                            Plane.CHROMA,
                            complexity,
                            segments[segmentIndex].uvdc,
                            segments[segmentIndex].uvac
                    );

                    if (blocks[blockOffset] != 0 || present) {
                        macroBlock.nonZeroDct = true;
                        LossyTransform.idct4x4(blocks, blockOffset);
                    }

                    leftValue = present ? 1 : 0;
                    topComplexity[topComplexityOffset + blockX + groupStart] = present ? (byte) 1 : 0;
                }
                leftComplexity[blockY + groupStart] = (byte) leftValue;
            }
        }

        return blocks;
    }

    private void loopFilter(int macroblockX, int macroblockY, int macroBlockInfo) {
        int lumaWidth = macroblockWidth * 16;
        int chromaWidth = macroblockWidth * 8;
        byte[] yBuffer = frame.yBuffer;
        byte[] uBuffer = frame.uBuffer;
        byte[] vBuffer = frame.vBuffer;
        int lumaBase = macroblockY * 16 * lumaWidth + macroblockX * 16;
        int chromaBase = macroblockY * 8 * chromaWidth + macroblockX * 8;
        int filterLevel = calculateFilterLevel(macroBlockInfo);

        if (filterLevel == 0) {
            return;
        }

        int interiorLimit = filterLevel;
        if (frame.sharpnessLevel > 0) {
            interiorLimit >>= frame.sharpnessLevel > 4 ? 2 : 1;
            if (interiorLimit > 9 - frame.sharpnessLevel) {
                interiorLimit = 9 - frame.sharpnessLevel;
            }
        }
        if (interiorLimit == 0) {
            interiorLimit = 1;
        }

        int hevThreshold = filterLevel >= 40 ? 2 : (filterLevel >= 15 ? 1 : 0);
        int macroblockEdgeLimit = (filterLevel + 2) * 2 + interiorLimit;
        int subblockEdgeLimit = filterLevel * 2 + interiorLimit;
        int lumaModeCode = loopFilterLumaModeCode(macroBlockInfo);
        boolean doSubblockFiltering = lumaModeCode == LumaMode.B
                || (!loopFilterCoefficientsSkipped(macroBlockInfo) && loopFilterNonZeroDct(macroBlockInfo));

        if (macroblockX > 0) {
            if (frame.filterType) {
                for (int start = lumaBase - 4, end = start + 16 * lumaWidth; start < end; start += lumaWidth) {
                    LossyLoopFilter.simpleSegmentHorizontal(macroblockEdgeLimit, yBuffer, start);
                }
            } else {
                for (int start = lumaBase - 4, end = start + 16 * lumaWidth; start < end; start += lumaWidth) {
                    LossyLoopFilter.macroblockFilterHorizontal(
                            hevThreshold,
                            interiorLimit,
                            macroblockEdgeLimit,
                            yBuffer,
                            start
                    );
                }

                for (int start = chromaBase - 4, end = start + 8 * chromaWidth; start < end; start += chromaWidth) {
                    LossyLoopFilter.macroblockFilterHorizontal(
                            hevThreshold,
                            interiorLimit,
                            macroblockEdgeLimit,
                            uBuffer,
                            start
                    );

                    LossyLoopFilter.macroblockFilterHorizontal(
                            hevThreshold,
                            interiorLimit,
                            macroblockEdgeLimit,
                            vBuffer,
                            start
                    );
                }
            }
        }

        if (doSubblockFiltering) {
            if (frame.filterType) {
                for (int xOffset = 0; xOffset < 12; xOffset += 4) {
                    for (int start = lumaBase + xOffset, end = start + 16 * lumaWidth; start < end; start += lumaWidth) {
                        LossyLoopFilter.simpleSegmentHorizontal(subblockEdgeLimit, yBuffer, start);
                    }
                }
            } else {
                for (int xOffset = 0; xOffset < 12; xOffset += 4) {
                    for (int start = lumaBase + xOffset, end = start + 16 * lumaWidth; start < end; start += lumaWidth) {
                        LossyLoopFilter.subblockFilterHorizontal(
                                hevThreshold,
                                interiorLimit,
                                subblockEdgeLimit,
                                yBuffer,
                                start
                        );
                    }
                }

                for (int start = chromaBase, end = start + 8 * chromaWidth; start < end; start += chromaWidth) {
                    LossyLoopFilter.subblockFilterHorizontal(
                            hevThreshold,
                            interiorLimit,
                            subblockEdgeLimit,
                            uBuffer,
                            start
                    );

                    LossyLoopFilter.subblockFilterHorizontal(
                            hevThreshold,
                            interiorLimit,
                            subblockEdgeLimit,
                            vBuffer,
                            start
                    );
                }
            }
        }

        if (macroblockY > 0) {
            if (frame.filterType) {
                for (int x = 0; x < 16; x++) {
                    LossyLoopFilter.simpleSegmentVertical(
                            macroblockEdgeLimit,
                            yBuffer,
                            lumaBase + x,
                            lumaWidth
                    );
                }
            } else {
                for (int x = 0; x < 16; x++) {
                    LossyLoopFilter.macroblockFilterVertical(
                            hevThreshold,
                            interiorLimit,
                            macroblockEdgeLimit,
                            yBuffer,
                            lumaBase + x,
                            lumaWidth
                    );
                }

                for (int x = 0; x < 8; x++) {
                    LossyLoopFilter.macroblockFilterVertical(
                            hevThreshold,
                            interiorLimit,
                            macroblockEdgeLimit,
                            uBuffer,
                            chromaBase + x,
                            chromaWidth
                    );
                    LossyLoopFilter.macroblockFilterVertical(
                            hevThreshold,
                            interiorLimit,
                            macroblockEdgeLimit,
                            vBuffer,
                            chromaBase + x,
                            chromaWidth
                    );
                }
            }
        }

        if (doSubblockFiltering) {
            if (frame.filterType) {
                for (int rowOffset = 4 * lumaWidth; rowOffset <= 12 * lumaWidth; rowOffset += 4 * lumaWidth) {
                    for (int x = 0; x < 16; x++) {
                        LossyLoopFilter.simpleSegmentVertical(
                                subblockEdgeLimit,
                                yBuffer,
                                lumaBase + rowOffset + x,
                                lumaWidth
                        );
                    }
                }
            } else {
                for (int rowOffset = 4 * lumaWidth; rowOffset <= 12 * lumaWidth; rowOffset += 4 * lumaWidth) {
                    for (int x = 0; x < 16; x++) {
                        LossyLoopFilter.subblockFilterVertical(
                                hevThreshold,
                                interiorLimit,
                                subblockEdgeLimit,
                                yBuffer,
                                lumaBase + rowOffset + x,
                                lumaWidth
                        );
                    }
                }

                int chromaRowOffset = 4 * chromaWidth;
                for (int x = 0; x < 8; x++) {
                    LossyLoopFilter.subblockFilterVertical(
                            hevThreshold,
                            interiorLimit,
                            subblockEdgeLimit,
                            uBuffer,
                            chromaBase + chromaRowOffset + x,
                            chromaWidth
                    );
                    LossyLoopFilter.subblockFilterVertical(
                            hevThreshold,
                            interiorLimit,
                            subblockEdgeLimit,
                            vBuffer,
                            chromaBase + chromaRowOffset + x,
                            chromaWidth
                    );
                }
            }
        }
    }

    /// Returns the clamped loop-filter level for one macroblock.
    ///
    /// @param macroBlockInfo the packed segment and prediction metadata
    /// @return the filter level in the range `0` through `63`
    private int calculateFilterLevel(int macroBlockInfo) {
        Segment segment = segments[loopFilterSegmentId(macroBlockInfo)];
        int filterLevel = frame.filterLevel;
        if (filterLevel == 0) {
            return 0;
        }

        if (segmentsEnabled) {
            filterLevel = segment.deltaValues ? filterLevel + segment.loopFilterLevel : segment.loopFilterLevel;
        }
        filterLevel = Math.max(0, Math.min(63, filterLevel));

        if (loopFilterAdjustmentsEnabled) {
            filterLevel += refDelta[0];
            if (loopFilterLumaModeCode(macroBlockInfo) == LumaMode.B) {
                filterLevel += modeDelta[0];
            }
        }
        filterLevel = Math.max(0, Math.min(63, filterLevel));

        return filterLevel;
    }

    private static byte packLoopFilterInfo(MacroBlock macroBlock) {
        int info = macroBlock.segmentId & FILTER_INFO_SEGMENT_MASK;
        info |= (macroBlock.lumaMode & FILTER_INFO_LUMA_MODE_MASK) << FILTER_INFO_LUMA_MODE_SHIFT;
        if (macroBlock.coefficientsSkipped) {
            info |= FILTER_INFO_COEFFICIENTS_SKIPPED;
        }
        if (macroBlock.nonZeroDct) {
            info |= FILTER_INFO_NON_ZERO_DCT;
        }
        return (byte) info;
    }

    private static int loopFilterSegmentId(int macroBlockInfo) {
        return macroBlockInfo & FILTER_INFO_SEGMENT_MASK;
    }

    private static int loopFilterLumaModeCode(int macroBlockInfo) {
        return (macroBlockInfo >>> FILTER_INFO_LUMA_MODE_SHIFT) & FILTER_INFO_LUMA_MODE_MASK;
    }

    private static boolean loopFilterCoefficientsSkipped(int macroBlockInfo) {
        return (macroBlockInfo & FILTER_INFO_COEFFICIENTS_SKIPPED) != 0;
    }

    private static boolean loopFilterNonZeroDct(int macroBlockInfo) {
        return (macroBlockInfo & FILTER_INFO_NON_ZERO_DCT) != 0;
    }

    private Vp8Frame decodeFrameInternal() throws WebPException {
        readFrameHeader();
        int macroblockIndex = 0;

        for (int macroblockY = 0; macroblockY < macroblockHeight; macroblockY++) {
            int partition = macroblockY % numPartitions;
            resetLeftState();

            for (int macroblockX = 0; macroblockX < macroblockWidth; macroblockX++) {
                readMacroblockHeader(macroblockX, macroBlock);
                int topComplexityOffset = macroblockX * 9;
                int[] blocks;
                if (!macroBlock.coefficientsSkipped) {
                    blocks = readResidualData(macroBlock, macroblockX, partition);
                } else {
                    if (macroBlock.lumaMode != LumaMode.B) {
                        leftComplexity[0] = 0;
                        topComplexity[topComplexityOffset] = 0;
                    }
                    for (int i = 1; i < 9; i++) {
                        leftComplexity[i] = 0;
                        topComplexity[topComplexityOffset + i] = 0;
                    }
                    blocks = zeroResidualData;
                }

                intraPredictLuma(macroblockX, macroblockY, macroBlock, blocks);
                intraPredictChroma(macroblockX, macroblockY, macroBlock, blocks);
                macroblockFilterInfo[macroblockIndex++] = packLoopFilterInfo(macroBlock);
            }

            Arrays.fill(leftBorderY, (byte) 129);
            Arrays.fill(leftBorderU, (byte) 129);
            Arrays.fill(leftBorderV, (byte) 129);
        }

        for (int macroblockY = 0; macroblockY < macroblockHeight; macroblockY++) {
            for (int macroblockX = 0; macroblockX < macroblockWidth; macroblockX++) {
                loopFilter(macroblockX, macroblockY, macroblockFilterInfo[macroblockY * macroblockWidth + macroblockX] & 0xFF);
            }
        }

        return frame;
    }

    private static void setChromaBorder(byte[] leftBorder, byte[] topBorder, byte[] chromaBlock, int macroblockX) {
        int stride = LossyPrediction.CHROMA_STRIDE;
        leftBorder[0] = chromaBlock[8];
        for (int i = 0; i < 8; i++) {
            leftBorder[i + 1] = chromaBlock[(i + 1) * stride + 8];
        }
        System.arraycopy(chromaBlock, 8 * stride + 1, topBorder, macroblockX * 8, 8);
    }

    /// Returns retained storage initialized over its active prefix.
    ///
    /// @param bytes the previously retained storage
    /// @param length the active prefix length
    /// @param value the initialization value
    /// @return `bytes` when it is large enough, or a newly allocated array otherwise
    private static byte[] prepareFilled(byte[] bytes, int length, byte value) {
        if (bytes.length < length) {
            bytes = new byte[length];
        }
        Arrays.fill(bytes, 0, length, value);
        return bytes;
    }

    private void resetLeftState() {
        Arrays.fill(leftBpred, (byte) IntraMode.DC);
        Arrays.fill(leftComplexity, (byte) 0);
    }

    private static short dcQuant(int index) {
        return LossyTables.DC_QUANT[Math.max(0, Math.min(127, index))];
    }

    private static short acQuant(int index) {
        return LossyTables.AC_QUANT[Math.max(0, Math.min(127, index))];
    }

    /// Advances over an exact payload range and returns its starting offset.
    ///
    /// @param length the required byte count
    /// @return the starting array offset of the consumed range
    /// @throws WebPException if fewer than `length` bytes remain
    private int readExactly(int length) throws WebPException {
        if (length < 0 || inputLimit - inputPosition < length) {
            throw new WebPException("Unexpected end of VP8 partition data");
        }

        int offset = inputPosition;
        inputPosition += length;
        return offset;
    }

    /// Reads one unsigned payload byte.
    ///
    /// @return the next byte widened to an unsigned integer
    /// @throws WebPException if the payload is exhausted
    private int readU8() throws WebPException {
        if (inputPosition >= inputLimit) {
            throw new WebPException("Unexpected end of VP8 stream");
        }
        return Byte.toUnsignedInt(input[inputPosition++]);
    }

    /// Reads one unsigned little-endian 16-bit payload value.
    ///
    /// @return the next unsigned 16-bit value
    /// @throws WebPException if the payload is truncated
    private int readU16LE() throws WebPException {
        int position = inputPosition;
        if (inputLimit - position >= Short.BYTES) {
            inputPosition = position + Short.BYTES;
            return Short.toUnsignedInt(ArrayUtils.getShortLE(input, position));
        }
        return readU8() | (readU8() << 8);
    }

    /// Reads one unsigned little-endian 24-bit payload value.
    ///
    /// @return the next unsigned 24-bit value
    /// @throws WebPException if the payload is truncated
    private int readU24LE() throws WebPException {
        int position = inputPosition;
        if (inputLimit - position >= 3) {
            inputPosition = position + 3;
            return Short.toUnsignedInt(ArrayUtils.getShortLE(input, position))
                    | (Byte.toUnsignedInt(input[position + 2]) << 16);
        }
        return readU8() | (readU8() << 8) | (readU8() << 16);
    }

    /// Reusable prediction and coefficient metadata for one macroblock.
    @NotNullByDefault
    private static final class MacroBlock {
        /// Packed per-block luma prediction modes used when [#lumaMode] is [LumaMode#B].
        long intraModes;

        /// Luma prediction mode for the current macroblock.
        @LumaMode
        int lumaMode = LumaMode.DC;

        /// Chroma prediction mode for the current macroblock.
        @ChromaMode
        int chromaMode = ChromaMode.DC;

        /// Segment containing the current macroblock.
        int segmentId;

        /// Whether coefficient decoding is skipped for the current macroblock.
        boolean coefficientsSkipped;

        /// Whether the current macroblock contains a non-zero coefficient.
        boolean nonZeroDct;

        /// Clears metadata that is not overwritten unconditionally by the next header.
        void reset() {
            segmentId = 0;
            coefficientsSkipped = false;
            nonZeroDct = false;
        }
    }
}
