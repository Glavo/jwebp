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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.lossy.LossyCommon.ChromaMode;
import org.glavo.webp.internal.lossy.LossyCommon.IntraMode;
import org.glavo.webp.internal.lossy.LossyCommon.LumaMode;
import org.glavo.webp.internal.lossy.LossyCommon.Plane;
import org.glavo.webp.internal.lossy.LossyCommon.Segment;

import java.nio.ByteBuffer;
import java.util.Arrays;

/// Pure-Java VP8 keyframe decoder.
///
/// The implementation follows the structure of the reference decoder in
/// `external/image-webp`: parse the frame header, decode macroblock prediction modes and
/// residual coefficients, reconstruct YUV planes, then apply the in-loop deblocking filter. Only
/// VP8 keyframes are supported because WebP still images and animated frame subchunks store
/// keyframe payloads.
@NotNullByDefault
public final class Vp8Decoder {

    /// Reusable storage for the decoded VP8 color planes.
    ///
    /// A workspace may be shared by consecutive calls from one thread. It retains only byte
    /// arrays; all bitstream and prediction state remains local to each decoder invocation.
    @NotNullByDefault
    public static final class DecodeWorkspace {
        /// Reusable full-resolution luma plane.
        private byte[] yBuffer = new byte[0];

        /// Reusable half-resolution blue-difference chroma plane.
        private byte[] uBuffer = new byte[0];

        /// Reusable half-resolution red-difference chroma plane.
        private byte[] vBuffer = new byte[0];

        /// Creates an empty workspace whose planes are allocated on first use.
        public DecodeWorkspace() {
        }

        /// Returns a luma plane with at least the requested capacity.
        ///
        /// @param length the minimum plane length
        /// @return the retained luma plane
        private byte[] acquireYBuffer(int length) {
            if (yBuffer.length < length) {
                yBuffer = new byte[length];
            }
            return yBuffer;
        }

        /// Returns a blue-difference chroma plane with at least the requested capacity.
        ///
        /// @param length the minimum plane length
        /// @return the retained blue-difference chroma plane
        private byte[] acquireUBuffer(int length) {
            if (uBuffer.length < length) {
                uBuffer = new byte[length];
            }
            return uBuffer;
        }

        /// Returns a red-difference chroma plane with at least the requested capacity.
        ///
        /// @param length the minimum plane length
        /// @return the retained red-difference chroma plane
        private byte[] acquireVBuffer(int length) {
            if (vBuffer.length < length) {
                vBuffer = new byte[length];
            }
            return vBuffer;
        }
    }

    private static final int[] CHROMA_GROUP_STARTS = {5, 7};
    private static final int FILTER_INFO_SEGMENT_MASK = 0x03;
    private static final int FILTER_INFO_LUMA_MODE_SHIFT = 2;
    private static final int FILTER_INFO_LUMA_MODE_MASK = 0x07;
    private static final int FILTER_INFO_COEFFICIENTS_SKIPPED = 1 << 5;
    private static final int FILTER_INFO_NON_ZERO_DCT = 1 << 6;
    private static final IntraMode[] INTRA_MODE_BY_CODE = buildIntraModeByCode();

    private final ByteBuffer input;
    /// Optional color-plane storage retained by the caller across frame decodes.
    private final @Nullable DecodeWorkspace workspace;
    private final LossyArithmeticDecoder headerDecoder = new LossyArithmeticDecoder();

    private int macroblockWidth;
    private int macroblockHeight;
    private byte[] macroblockFilterInfo = new byte[0];
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
    private final int[][][][] tokenProbs = LossyTables.copyCoeffProbs();

    private @Nullable Integer probSkipFalse;
    private byte[] topBpred = new byte[0];
    private byte[] topComplexity = new byte[0];
    private final byte[] leftBpred = new byte[4];
    private final byte[] leftComplexity = new byte[9];

    private byte[] topBorderY = new byte[0];
    private byte[] leftBorderY = new byte[0];
    private byte[] topBorderU = new byte[0];
    private byte[] leftBorderU = new byte[0];
    private byte[] topBorderV = new byte[0];
    private byte[] leftBorderV = new byte[0];
    private final int[] residualDataScratch = new int[384];
    private final int[] y2BlockScratch = new int[16];
    private final int[] zeroResidualData = new int[384];
    private final byte[] lumaWorkspace = new byte[LossyPrediction.LUMA_BLOCK_SIZE];
    private final byte[] uWorkspace = new byte[LossyPrediction.CHROMA_BLOCK_SIZE];
    private final byte[] vWorkspace = new byte[LossyPrediction.CHROMA_BLOCK_SIZE];

    private Vp8Decoder(ByteBuffer input) {
        this(input, null);
    }

    private Vp8Decoder(ByteBuffer input, @Nullable DecodeWorkspace workspace) {
        this.input = input;
        this.workspace = workspace;
        for (int i = 0; i < segments.length; i++) {
            segments[i] = new Segment();
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
        Vp8Decoder decoder = new Vp8Decoder(input.slice());
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
        Vp8Frame frame = new Vp8Decoder(input.slice()).decodeFrameInternal();
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
        decodeArgb(input, fancyUpsampling, argb, null);
    }

    /// Decodes one raw VP8 frame payload into an existing packed `ARGB` buffer while reusing plane
    /// storage.
    ///
    /// The workspace must not be used concurrently. Its retained arrays may grow to fit the
    /// largest decoded frame. The supplied destination must contain exactly one entry per decoded
    /// frame pixel.
    ///
    /// @param input the raw VP8 frame payload
    /// @param fancyUpsampling whether to use the high-quality chroma upsampler
    /// @param argb the destination for tightly packed non-premultiplied `ARGB` pixels
    /// @param workspace reusable color-plane storage, or `null` to allocate per call
    /// @throws IllegalArgumentException if the destination size does not match the frame dimensions
    /// @throws WebPException if the VP8 bitstream is malformed
    public static void decodeArgb(
            ByteBuffer input,
            boolean fancyUpsampling,
            int[] argb,
            @Nullable DecodeWorkspace workspace
    ) throws WebPException {
        Vp8Frame frame = new Vp8Decoder(input.slice(), workspace).decodeFrameInternal();
        int expectedLength = frame.width * frame.height;
        if (argb.length != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer length does not match VP8 frame dimensions: "
                            + argb.length + " != " + expectedLength
            );
        }
        frame.fillArgb(argb, fancyUpsampling);
    }

    private void updateTokenProbabilities() throws WebPException {
        for (int i = 0; i < LossyTables.COEFF_UPDATE_PROBS.length; i++) {
            for (int j = 0; j < LossyTables.COEFF_UPDATE_PROBS[i].length; j++) {
                for (int k = 0; k < LossyTables.COEFF_UPDATE_PROBS[i][j].length; k++) {
                    for (int t = 0; t < LossyCommon.NUM_DCT_TOKENS - 1; t++) {
                        int prob = LossyTables.COEFF_UPDATE_PROBS[i][j][k][t];
                        if (headerDecoder.readBool(prob)) {
                            int updated = headerDecoder.readLiteral(8);
                            tokenProbs[i][j][k][t] = updated;
                        }
                    }
                }
            }
        }
        headerDecoder.ensureNotPastEof();
    }

    private void initPartitions(int partitionCount) throws WebPException {
        if (partitionCount > 1) {
            ByteBuffer sizes = readExactly(3 * partitionCount - 3);
            for (int i = 0; i < partitionCount - 1; i++) {
                int sizeOffset = i * 3;
                int partitionSize = Byte.toUnsignedInt(sizes.get(sizeOffset))
                        | (Byte.toUnsignedInt(sizes.get(sizeOffset + 1)) << 8)
                        | (Byte.toUnsignedInt(sizes.get(sizeOffset + 2)) << 16);
                partitions[i].init(readExactly(partitionSize));
            }
        }
        partitions[partitionCount - 1].init(readExactly(input.remaining()));
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
        int tag = readU24LE(input);
        if ((tag & 1) != 0) {
            throw new WebPException("Only VP8 keyframes are supported");
        }
        frame.keyframe = true;
        frame.version = (byte) ((tag >> 1) & 0x7);
        frame.forDisplay = ((tag >> 4) & 1) != 0;

        int firstPartitionSize = tag >> 5;
        int signature0 = readU8(input);
        int signature1 = readU8(input);
        int signature2 = readU8(input);
        if (signature0 != 0x9D || signature1 != 0x01 || signature2 != 0x2A) {
            throw new WebPException("Invalid VP8 frame signature");
        }

        int widthBits = readU16LE(input);
        int heightBits = readU16LE(input);
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
        Arrays.fill(topBpred, 0, topBpredLength, IntraMode.DC.code);
        int topComplexityLength = macroblockWidth * 9;
        if (topComplexity.length < topComplexityLength) {
            topComplexity = new byte[topComplexityLength];
        }
        Arrays.fill(topComplexity, 0, topComplexityLength, (byte) 0);

        int yBufferLength = macroblockWidth * 16 * macroblockHeight * 16;
        int chromaBufferLength = macroblockWidth * 8 * macroblockHeight * 8;
        if (workspace == null) {
            frame.yBuffer = new byte[yBufferLength];
            frame.uBuffer = new byte[chromaBufferLength];
            frame.vBuffer = new byte[chromaBufferLength];
        } else {
            frame.yBuffer = workspace.acquireYBuffer(yBufferLength);
            frame.uBuffer = workspace.acquireUBuffer(chromaBufferLength);
            frame.vBuffer = workspace.acquireVBuffer(chromaBufferLength);
        }

        topBorderY = filled(frame.width + 20, (byte) 127);
        leftBorderY = filled(17, (byte) 129);
        topBorderU = filled(8 * macroblockWidth, (byte) 127);
        leftBorderU = filled(9, (byte) 129);
        topBorderV = filled(8 * macroblockWidth, (byte) 127);
        leftBorderV = filled(9, (byte) 129);

        headerDecoder.init(readExactly(firstPartitionSize));

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
        probSkipFalse = macroblockNoSkipCoeff == 1 ? headerDecoder.readLiteral(8) : null;
        headerDecoder.ensureNotPastEof();
    }

    /// Reads prediction and segment metadata into a reusable macroblock workspace.
    ///
    /// @param macroblockX the horizontal macroblock index
    /// @param macroBlock the workspace to overwrite
    /// @throws WebPException if the header partition is corrupt
    private void readMacroblockHeader(int macroblockX, MacroBlock macroBlock) throws WebPException {
        macroBlock.reset();
        int topBpredOffset = macroblockX * 4;

        if (segmentsEnabled && segmentsUpdateMap) {
            macroBlock.segmentId = headerDecoder.readWithTree(LossyTables.SEGMENT_ID_TREE, segmentProbs);
        }

        macroBlock.coefficientsSkipped = probSkipFalse != null && headerDecoder.readBool(probSkipFalse);

        int lumaModeCode = headerDecoder.readWithTree(
                LossyTables.KEYFRAME_YMODE_TREE,
                LossyTables.KEYFRAME_YMODE_PROBS
        );
        LumaMode lumaMode = LumaMode.fromCode(lumaModeCode);
        if (lumaMode == null) {
            throw new WebPException("Invalid VP8 luma prediction mode: " + lumaModeCode);
        }
        macroBlock.lumaMode = lumaMode;

        IntraMode sharedMode = lumaMode.asIntraMode();
        if (sharedMode == null) {
            IntraMode[] bpred = macroBlock.bpred;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    IntraMode topMode = INTRA_MODE_BY_CODE[topBpred[topBpredOffset + x] & 0xFF];
                    IntraMode leftMode = INTRA_MODE_BY_CODE[leftBpred[y] & 0xFF];
                    int intraCode = headerDecoder.readWithTree(
                            LossyTables.KEYFRAME_BPRED_MODE_TREE,
                            LossyTables.KEYFRAME_BPRED_MODE_PROBS[topMode.ordinal()][leftMode.ordinal()]
                    );
                    IntraMode blockMode = IntraMode.fromCode(intraCode);
                    if (blockMode == null) {
                        throw new WebPException("Invalid VP8 intra prediction mode: " + intraCode);
                    }
                    bpred[x + y * 4] = blockMode;
                    topBpred[topBpredOffset + x] = blockMode.code;
                    leftBpred[y] = blockMode.code;
                }
            }
            for (int x = 0; x < 4; x++) {
                topBpred[topBpredOffset + x] = bpred[12 + x].code;
            }
        } else {
            Arrays.fill(leftBpred, sharedMode.code);
            Arrays.fill(topBpred, topBpredOffset, topBpredOffset + 4, sharedMode.code);
        }

        int chromaModeCode = headerDecoder.readWithTree(
                LossyTables.KEYFRAME_UV_MODE_TREE,
                LossyTables.KEYFRAME_UV_MODE_PROBS
        );
        ChromaMode chromaMode = ChromaMode.fromCode(chromaModeCode);
        if (chromaMode == null) {
            throw new WebPException("Invalid VP8 chroma prediction mode: " + chromaModeCode);
        }
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
            case V -> LossyPrediction.predictVpred(workspace, 16, 1, 1, stride);
            case H -> LossyPrediction.predictHpred(workspace, 16, 1, 1, stride);
            case TM -> LossyPrediction.predictTmpred(workspace, 16, 1, 1, stride);
            case DC -> LossyPrediction.predictDcpred(workspace, 16, stride, macroblockY != 0, macroblockX != 0);
            case B -> {
                IntraMode[] bpred = macroBlock.bpred;
                LossyPrediction.predict4x4(workspace, stride, bpred, residualData);
            }
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
            case DC -> {
                LossyPrediction.predictDcpred(uWorkspace, 8, stride, macroblockY != 0, macroblockX != 0);
                LossyPrediction.predictDcpred(vWorkspace, 8, stride, macroblockY != 0, macroblockX != 0);
            }
            case V -> {
                LossyPrediction.predictVpred(uWorkspace, 8, 1, 1, stride);
                LossyPrediction.predictVpred(vWorkspace, 8, 1, 1, stride);
            }
            case H -> {
                LossyPrediction.predictHpred(uWorkspace, 8, 1, 1, stride);
                LossyPrediction.predictHpred(vWorkspace, 8, 1, 1, stride);
            }
            case TM -> {
                LossyPrediction.predictTmpred(uWorkspace, 8, 1, 1, stride);
                LossyPrediction.predictTmpred(vWorkspace, 8, 1, 1, stride);
            }
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
            Plane plane,
            int complexity,
            short dcq,
            short acq
    ) throws WebPException {
        assert complexity <= 2;

        int firstCoeff = plane == Plane.Y_COEFF_1 ? 1 : 0;
        int[][][] probabilities = tokenProbs[plane.ordinal()];
        LossyArithmeticDecoder decoder = partitions[partition];

        int complexityState = complexity;
        boolean hasCoefficients = false;
        boolean skip = false;

        for (int i = firstCoeff; i < 16; i++) {
            int band = LossyTables.COEFF_BANDS[i];
            int[] tokenProbabilities = probabilities[band][complexityState];
            int token = decoder.readWithTree(
                    LossyTables.DCT_TOKEN_TREE,
                    tokenProbabilities,
                    skip ? 1 : 0
            );

            int absoluteValue;
            if (token == LossyTables.DCT_EOB) {
                break;
            } else if (token == LossyTables.DCT_0) {
                skip = true;
                hasCoefficients = true;
                complexityState = 0;
                continue;
            } else if (token >= LossyTables.DCT_1 && token <= LossyTables.DCT_4) {
                absoluteValue = token;
            } else if (token >= LossyTables.DCT_CAT1 && token <= LossyTables.DCT_CAT6) {
                int[] categoryProbabilities = LossyTables.PROB_DCT_CAT[token - LossyTables.DCT_CAT1];
                int extra = 0;
                for (int probability : categoryProbabilities) {
                    if (probability == 0) {
                        break;
                    }
                    extra = extra + extra + (decoder.readBool(probability) ? 1 : 0);
                }
                absoluteValue = LossyTables.DCT_CAT_BASE[token - LossyTables.DCT_CAT1] + extra;
            } else {
                throw new WebPException("Unknown VP8 DCT token: " + token);
            }

            skip = false;
            complexityState = absoluteValue == 0 ? 0 : (absoluteValue == 1 ? 1 : 2);
            if (decoder.readSign()) {
                absoluteValue = -absoluteValue;
            }

            int zigzag = LossyTables.ZIGZAG[i];
            block[blockOffset + zigzag] = absoluteValue * (zigzag > 0 ? acq : dcq);
            hasCoefficients = true;
        }

        decoder.ensureNotPastEof();
        return hasCoefficients;
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
        Plane plane = macroBlock.lumaMode == LumaMode.B ? Plane.Y_COEFF_0 : Plane.Y2;
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
        boolean doSubblockFiltering = lumaModeCode == LossyCommon.B_PRED
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
            if (loopFilterLumaModeCode(macroBlockInfo) == LossyCommon.B_PRED) {
                filterLevel += modeDelta[0];
            }
        }
        filterLevel = Math.max(0, Math.min(63, filterLevel));

        return filterLevel;
    }

    private static byte packLoopFilterInfo(MacroBlock macroBlock) {
        int info = macroBlock.segmentId & FILTER_INFO_SEGMENT_MASK;
        info |= (macroBlock.lumaMode.code & FILTER_INFO_LUMA_MODE_MASK) << FILTER_INFO_LUMA_MODE_SHIFT;
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
        MacroBlock macroBlock = new MacroBlock();

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

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static IntraMode[] buildIntraModeByCode() {
        IntraMode[] modes = new IntraMode[10];
        for (IntraMode mode : IntraMode.values()) {
            modes[mode.code] = mode;
        }
        return modes;
    }

    private void resetLeftState() {
        Arrays.fill(leftBpred, IntraMode.DC.code);
        Arrays.fill(leftComplexity, (byte) 0);
    }

    private static short dcQuant(int index) {
        return LossyTables.DC_QUANT[Math.max(0, Math.min(127, index))];
    }

    private static short acQuant(int index) {
        return LossyTables.AC_QUANT[Math.max(0, Math.min(127, index))];
    }

    private ByteBuffer readExactly(int length) throws WebPException {
        if (input.remaining() < length) {
            throw new WebPException("Unexpected end of VP8 partition data");
        }

        ByteBuffer slice = input.slice();
        slice.limit(length);
        input.position(input.position() + length);
        return slice;
    }

    private static int readU8(ByteBuffer input) throws WebPException {
        if (!input.hasRemaining()) {
            throw new WebPException("Unexpected end of VP8 stream");
        }
        return Byte.toUnsignedInt(input.get());
    }

    private static int readU16LE(ByteBuffer input) throws WebPException {
        return readU8(input) | (readU8(input) << 8);
    }

    private static int readU24LE(ByteBuffer input) throws WebPException {
        return readU8(input) | (readU8(input) << 8) | (readU8(input) << 16);
    }

    /// Reusable prediction and coefficient metadata for one macroblock.
    @NotNullByDefault
    private static final class MacroBlock {
        /// Per-block luma prediction modes used when [#lumaMode] is [LumaMode#B].
        final IntraMode[] bpred = new IntraMode[16];

        /// Luma prediction mode for the current macroblock.
        LumaMode lumaMode = LumaMode.DC;

        /// Chroma prediction mode for the current macroblock.
        ChromaMode chromaMode = ChromaMode.DC;

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
