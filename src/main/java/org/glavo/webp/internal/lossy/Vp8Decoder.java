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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Pure-Java VP8 keyframe decoder.
///
/// The implementation follows the structure of the reference decoder in
/// `external/image-webp`: parse the frame header, decode macroblock prediction modes and
/// residual coefficients, reconstruct YUV planes, then apply the in-loop deblocking filter. Only
/// VP8 keyframes are supported because WebP still images and animated frame subchunks store
/// keyframe payloads.
@NotNullByDefault
public final class Vp8Decoder {

    private final InputStream input;
    private final LossyArithmeticDecoder headerDecoder = new LossyArithmeticDecoder();

    private int macroblockWidth;
    private int macroblockHeight;
    private final List<MacroBlock> macroblocks = new ArrayList<>();
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

    private TreeNode[] segmentTreeNodes = LossyTables.copyTreeNodes(LossyTables.SEGMENT_TREE_NODE_DEFAULTS);
    private TreeNode[][][][] tokenProbs = LossyTables.copyCoeffProbNodes();

    private @Nullable Integer probSkipFalse;
    private PreviousMacroBlock[] top = new PreviousMacroBlock[0];
    private PreviousMacroBlock left = new PreviousMacroBlock();

    private byte[] topBorderY = new byte[0];
    private byte[] leftBorderY = new byte[0];
    private byte[] topBorderU = new byte[0];
    private byte[] leftBorderU = new byte[0];
    private byte[] topBorderV = new byte[0];
    private byte[] leftBorderV = new byte[0];

    private Vp8Decoder(InputStream input) {
        this.input = input;
        for (int i = 0; i < segments.length; i++) {
            segments[i] = new Segment();
        }
    }

    /// Reads the VP8 frame header and initializes partition state.
    ///
    /// The returned frame object is not yet fully reconstructed. It carries dimensions, loop
    /// filter parameters and allocated YUV planes, which is enough for subsequent macroblock decode
    /// stages.
    ///
    /// @param input the raw VP8 payload stream
    /// @return a frame object initialized from the VP8 header
    /// @throws WebPException if the VP8 stream is malformed
    static Vp8Frame decodeFrameHeader(InputStream input) throws WebPException {
        try {
            Vp8Decoder decoder = new Vp8Decoder(input);
            decoder.readFrameHeader();
            return decoder.frame;
        } catch (IOException ex) {
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            throw new WebPException("Failed to read the VP8 frame header", ex);
        }
    }

    /// Decodes one raw VP8 frame payload to packed `ARGB` pixels.
    ///
    /// The input stream must expose exactly the payload bytes of a `VP8` chunk. The
    /// decoder reconstructs the internal YUV planes first and only performs color conversion once
    /// the whole frame is available, matching the ordering used by the reference implementation.
    ///
    /// @param input the raw VP8 frame payload
    /// @param fancyUpsampling whether to use the high-quality chroma upsampler
    /// @return tightly packed non-premultiplied `ARGB` pixels
    /// @throws WebPException if the VP8 bitstream is malformed
    public static int[] decodeArgb(InputStream input, boolean fancyUpsampling) throws WebPException {
        try {
            Vp8Frame frame = new Vp8Decoder(input).decodeFrameInternal();
            int[] argb = new int[frame.width * frame.height];
            frame.fillArgb(argb, fancyUpsampling);
            return argb;
        } catch (IOException ex) {
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            throw new WebPException("Failed to decode the VP8 frame", ex);
        }
    }

    private void updateTokenProbabilities() throws IOException, WebPException {
        LossyArithmeticDecoder.BitResultAccumulator accumulator = headerDecoder.startAccumulatedResult();
        for (int i = 0; i < LossyTables.COEFF_UPDATE_PROBS.length; i++) {
            for (int j = 0; j < LossyTables.COEFF_UPDATE_PROBS[i].length; j++) {
                for (int k = 0; k < LossyTables.COEFF_UPDATE_PROBS[i][j].length; k++) {
                    for (int t = 0; t < LossyCommon.NUM_DCT_TOKENS - 1; t++) {
                        int prob = LossyTables.COEFF_UPDATE_PROBS[i][j][k][t];
                        if (headerDecoder.readBool(prob).orAccumulate(accumulator)) {
                            int updated = headerDecoder.readLiteral(8).orAccumulate(accumulator);
                            tokenProbs[i][j][k][t].prob = updated;
                        }
                    }
                }
            }
        }
        headerDecoder.check(accumulator, null);
    }

    private void initPartitions(int partitionCount) throws IOException, WebPException {
        if (partitionCount > 1) {
            byte[] sizes = readExactly(3 * partitionCount - 3);
            for (int i = 0; i < partitionCount - 1; i++) {
                int sizeOffset = i * 3;
                int partitionSize = (sizes[sizeOffset] & 0xFF)
                        | ((sizes[sizeOffset + 1] & 0xFF) << 8)
                        | ((sizes[sizeOffset + 2] & 0xFF) << 16);
                partitions[i].init(readExactly(partitionSize));
            }
        }
        partitions[partitionCount - 1].init(input.readAllBytes());
    }

    private void readQuantizationIndices() throws IOException, WebPException {
        LossyArithmeticDecoder.BitResultAccumulator accumulator = headerDecoder.startAccumulatedResult();
        int yacAbs = headerDecoder.readLiteral(7).orAccumulate(accumulator);
        int ydcDelta = headerDecoder.readOptionalSignedValue(4).orAccumulate(accumulator);
        int y2dcDelta = headerDecoder.readOptionalSignedValue(4).orAccumulate(accumulator);
        int y2acDelta = headerDecoder.readOptionalSignedValue(4).orAccumulate(accumulator);
        int uvdcDelta = headerDecoder.readOptionalSignedValue(4).orAccumulate(accumulator);
        int uvacDelta = headerDecoder.readOptionalSignedValue(4).orAccumulate(accumulator);

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

        headerDecoder.check(accumulator, null);
    }

    private void readLoopFilterAdjustments() throws IOException, WebPException {
        LossyArithmeticDecoder.BitResultAccumulator accumulator = headerDecoder.startAccumulatedResult();
        if (headerDecoder.readFlag().orAccumulate(accumulator)) {
            for (int i = 0; i < 4; i++) {
                refDelta[i] = headerDecoder.readOptionalSignedValue(6).orAccumulate(accumulator);
            }
            for (int i = 0; i < 4; i++) {
                modeDelta[i] = headerDecoder.readOptionalSignedValue(6).orAccumulate(accumulator);
            }
        }
        headerDecoder.check(accumulator, null);
    }

    private void readSegmentUpdates() throws IOException, WebPException {
        LossyArithmeticDecoder.BitResultAccumulator accumulator = headerDecoder.startAccumulatedResult();
        segmentsUpdateMap = headerDecoder.readFlag().orAccumulate(accumulator);
        boolean updateSegmentFeatureData = headerDecoder.readFlag().orAccumulate(accumulator);

        if (updateSegmentFeatureData) {
            boolean segmentFeatureMode = headerDecoder.readFlag().orAccumulate(accumulator);
            for (Segment segment : segments) {
                segment.deltaValues = !segmentFeatureMode;
            }
            for (Segment segment : segments) {
                segment.quantizerLevel = headerDecoder.readOptionalSignedValue(7).orAccumulate(accumulator).byteValue();
            }
            for (Segment segment : segments) {
                segment.loopFilterLevel = headerDecoder.readOptionalSignedValue(6).orAccumulate(accumulator).byteValue();
            }
        }

        if (segmentsUpdateMap) {
            for (int i = 0; i < 3; i++) {
                boolean update = headerDecoder.readFlag().orAccumulate(accumulator);
                segmentTreeNodes[i].prob = update ? headerDecoder.readLiteral(8).orAccumulate(accumulator) : 255;
            }
        }

        headerDecoder.check(accumulator, null);
    }

    private void readFrameHeader() throws IOException, WebPException {
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

        top = new PreviousMacroBlock[macroblockWidth];
        for (int i = 0; i < top.length; i++) {
            top[i] = new PreviousMacroBlock();
        }
        left = new PreviousMacroBlock();

        frame.yBuffer = new byte[macroblockWidth * 16 * macroblockHeight * 16];
        frame.uBuffer = new byte[macroblockWidth * 8 * macroblockHeight * 8];
        frame.vBuffer = new byte[macroblockWidth * 8 * macroblockHeight * 8];

        topBorderY = filled(frame.width + 20, (byte) 127);
        leftBorderY = filled(17, (byte) 129);
        topBorderU = filled(8 * macroblockWidth, (byte) 127);
        leftBorderU = filled(9, (byte) 129);
        topBorderV = filled(8 * macroblockWidth, (byte) 127);
        leftBorderV = filled(9, (byte) 129);

        headerDecoder.init(readExactly(firstPartitionSize));

        LossyArithmeticDecoder.BitResultAccumulator accumulator = headerDecoder.startAccumulatedResult();
        int colorSpace = headerDecoder.readLiteral(1).orAccumulate(accumulator);
        frame.pixelType = headerDecoder.readLiteral(1).orAccumulate(accumulator).byteValue();
        if (colorSpace != 0) {
            throw new WebPException("Unsupported VP8 color space: " + colorSpace);
        }

        segmentsEnabled = headerDecoder.readFlag().orAccumulate(accumulator);
        if (segmentsEnabled) {
            readSegmentUpdates();
        }

        frame.filterType = headerDecoder.readFlag().orAccumulate(accumulator);
        frame.filterLevel = headerDecoder.readLiteral(6).orAccumulate(accumulator).byteValue();
        frame.sharpnessLevel = headerDecoder.readLiteral(3).orAccumulate(accumulator).byteValue();

        loopFilterAdjustmentsEnabled = headerDecoder.readFlag().orAccumulate(accumulator);
        if (loopFilterAdjustmentsEnabled) {
            readLoopFilterAdjustments();
        }

        int partitionCount = 1 << headerDecoder.readLiteral(2).orAccumulate(accumulator);
        headerDecoder.check(accumulator, null);

        numPartitions = partitionCount;
        initPartitions(partitionCount);
        readQuantizationIndices();

        headerDecoder.readLiteral(1);
        updateTokenProbabilities();

        LossyArithmeticDecoder.BitResultAccumulator skipAccumulator = headerDecoder.startAccumulatedResult();
        int macroblockNoSkipCoeff = headerDecoder.readLiteral(1).orAccumulate(skipAccumulator);
        probSkipFalse = macroblockNoSkipCoeff == 1 ? headerDecoder.readLiteral(8).orAccumulate(skipAccumulator) : null;
        headerDecoder.check(skipAccumulator, null);
    }

    private MacroBlock readMacroblockHeader(int macroblockX) throws IOException, WebPException {
        MacroBlock macroBlock = new MacroBlock();
        LossyArithmeticDecoder.BitResultAccumulator accumulator = headerDecoder.startAccumulatedResult();

        if (segmentsEnabled && segmentsUpdateMap) {
            macroBlock.segmentId = headerDecoder.readWithTree(segmentTreeNodes).orAccumulate(accumulator);
        }

        macroBlock.coefficientsSkipped = probSkipFalse != null && headerDecoder.readBool(probSkipFalse).orAccumulate(accumulator);

        int lumaModeCode = headerDecoder.readWithTree(LossyTables.KEYFRAME_YMODE_NODES).orAccumulate(accumulator);
        macroBlock.lumaMode = LumaMode.fromCode(lumaModeCode);
        if (macroBlock.lumaMode == null) {
            throw new WebPException("Invalid VP8 luma prediction mode: " + lumaModeCode);
        }

        IntraMode sharedMode = macroBlock.lumaMode.asIntraMode();
        if (sharedMode == null) {
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    IntraMode topMode = top[macroblockX].bpred[x];
                    IntraMode leftMode = left.bpred[y];
                    int intraCode = headerDecoder.readWithTree(LossyTables.KEYFRAME_BPRED_MODE_NODES[topMode.ordinal()][leftMode.ordinal()]).orAccumulate(accumulator);
                    IntraMode blockMode = IntraMode.fromCode(intraCode);
                    if (blockMode == null) {
                        throw new WebPException("Invalid VP8 intra prediction mode: " + intraCode);
                    }
                    macroBlock.bpred[x + y * 4] = blockMode;
                    top[macroblockX].bpred[x] = blockMode;
                    left.bpred[y] = blockMode;
                }
            }
        } else {
            Arrays.fill(macroBlock.bpred, sharedMode);
            Arrays.fill(left.bpred, sharedMode);
        }

        int chromaModeCode = headerDecoder.readWithTree(LossyTables.KEYFRAME_UV_MODE_NODES).orAccumulate(accumulator);
        macroBlock.chromaMode = ChromaMode.fromCode(chromaModeCode);
        if (macroBlock.chromaMode == null) {
            throw new WebPException("Invalid VP8 chroma prediction mode: " + chromaModeCode);
        }

        System.arraycopy(macroBlock.bpred, 12, top[macroblockX].bpred, 0, 4);
        return headerDecoder.check(accumulator, macroBlock);
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
        byte[] workspace = LossyPrediction.createBorderLuma(
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
            case B -> LossyPrediction.predict4x4(workspace, stride, macroBlock.bpred, residualData);
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
        byte[] uWorkspace = LossyPrediction.createBorderChroma(macroblockX, macroblockY, topBorderU, leftBorderU);
        byte[] vWorkspace = LossyPrediction.createBorderChroma(macroblockX, macroblockY, topBorderV, leftBorderV);

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
    ) throws IOException, WebPException {
        assert complexity <= 2;

        int firstCoeff = plane == Plane.Y_COEFF_1 ? 1 : 0;
        TreeNode[][][] probabilities = tokenProbs[plane.ordinal()];
        LossyArithmeticDecoder decoder = partitions[partition];
        LossyArithmeticDecoder.BitResultAccumulator accumulator = decoder.startAccumulatedResult();

        int complexityState = complexity;
        boolean hasCoefficients = false;
        boolean skip = false;

        for (int i = firstCoeff; i < 16; i++) {
            int band = LossyTables.COEFF_BANDS[i];
            TreeNode[] tree = probabilities[band][complexityState];
            int token = decoder.readWithTreeWithFirstNode(tree, tree[skip ? 1 : 0]).orAccumulate(accumulator);

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
                    extra = extra + extra + (decoder.readBool(probability).orAccumulate(accumulator) ? 1 : 0);
                }
                absoluteValue = LossyTables.DCT_CAT_BASE[token - LossyTables.DCT_CAT1] + extra;
            } else {
                throw new WebPException("Unknown VP8 DCT token: " + token);
            }

            skip = false;
            complexityState = absoluteValue == 0 ? 0 : (absoluteValue == 1 ? 1 : 2);
            if (decoder.readSign().orAccumulate(accumulator)) {
                absoluteValue = -absoluteValue;
            }

            int zigzag = LossyTables.ZIGZAG[i];
            block[blockOffset + zigzag] = absoluteValue * (zigzag > 0 ? acq : dcq);
            hasCoefficients = true;
        }

        return decoder.check(accumulator, hasCoefficients);
    }

    /*
     * Residual decoding follows the VP8 block order used by the reference implementation:
     * optional Y2 first, then 16 luma 4x4 blocks, then two 2x2 chroma groups. Complexity context
     * is propagated through the cached top/left macroblock state so coefficient probabilities can
     * adapt across block boundaries.
     */
    private int[] readResidualData(MacroBlock macroBlock, int macroblockX, int partition) throws IOException, WebPException {
        int segmentIndex = macroBlock.segmentId;
        int[] blocks = new int[384];
        Plane plane = macroBlock.lumaMode == LumaMode.B ? Plane.Y_COEFF_0 : Plane.Y2;

        if (plane == Plane.Y2) {
            int complexity = top[macroblockX].complexity[0] + left.complexity[0];
            int[] y2Block = new int[16];
            boolean present = readCoefficients(
                    y2Block,
                    0,
                    partition,
                    plane,
                    complexity,
                    segments[segmentIndex].y2dc,
                    segments[segmentIndex].y2ac
            );

            left.complexity[0] = present ? 1 : 0;
            top[macroblockX].complexity[0] = present ? 1 : 0;

            LossyTransform.iwht4x4(y2Block);
            for (int k = 0; k < 16; k++) {
                blocks[16 * k] = y2Block[k];
            }

            plane = Plane.Y_COEFF_1;
        }

        for (int blockY = 0; blockY < 4; blockY++) {
            int leftComplexity = left.complexity[blockY + 1];
            for (int blockX = 0; blockX < 4; blockX++) {
                int blockIndex = blockX + blockY * 4;
                int blockOffset = blockIndex * 16;
                int complexity = top[macroblockX].complexity[blockX + 1] + leftComplexity;

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

                leftComplexity = present ? 1 : 0;
                top[macroblockX].complexity[blockX + 1] = present ? 1 : 0;
            }
            left.complexity[blockY + 1] = leftComplexity;
        }

        for (int groupStart : new int[]{5, 7}) {
            for (int blockY = 0; blockY < 2; blockY++) {
                int leftComplexity = left.complexity[blockY + groupStart];
                for (int blockX = 0; blockX < 2; blockX++) {
                    int blockIndex = blockX + blockY * 2 + (groupStart == 5 ? 16 : 20);
                    int blockOffset = blockIndex * 16;
                    int complexity = top[macroblockX].complexity[blockX + groupStart] + leftComplexity;

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

                    leftComplexity = present ? 1 : 0;
                    top[macroblockX].complexity[blockX + groupStart] = present ? 1 : 0;
                }
                left.complexity[blockY + groupStart] = leftComplexity;
            }
        }

        return blocks;
    }

    private void loopFilter(int macroblockX, int macroblockY, MacroBlock macroBlock) {
        int lumaWidth = macroblockWidth * 16;
        int chromaWidth = macroblockWidth * 8;
        FilterParameters parameters = calculateFilterParameters(macroBlock);

        if (parameters.filterLevel == 0) {
            return;
        }

        int macroblockEdgeLimit = (parameters.filterLevel + 2) * 2 + parameters.interiorLimit;
        int subblockEdgeLimit = parameters.filterLevel * 2 + parameters.interiorLimit;
        boolean doSubblockFiltering = macroBlock.lumaMode == LumaMode.B
                || (!macroBlock.coefficientsSkipped && macroBlock.nonZeroDct);

        if (macroblockX > 0) {
            if (frame.filterType) {
                for (int y = 0; y < 16; y++) {
                    int row = macroblockY * 16 + y;
                    int x0 = macroblockX * 16;
                    int start = row * lumaWidth + x0 - 4;
                    byte[] window = Arrays.copyOfRange(frame.yBuffer, start, start + 8);
                    LossyLoopFilter.simpleSegmentHorizontal(macroblockEdgeLimit, window);
                    System.arraycopy(window, 0, frame.yBuffer, start, 8);
                }
            } else {
                for (int y = 0; y < 16; y++) {
                    int row = macroblockY * 16 + y;
                    int x0 = macroblockX * 16;
                    int start = row * lumaWidth + x0 - 4;
                    byte[] window = Arrays.copyOfRange(frame.yBuffer, start, start + 8);
                    LossyLoopFilter.macroblockFilterHorizontal(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            macroblockEdgeLimit,
                            window
                    );
                    System.arraycopy(window, 0, frame.yBuffer, start, 8);
                }

                for (int y = 0; y < 8; y++) {
                    int row = macroblockY * 8 + y;
                    int x0 = macroblockX * 8;
                    int uStart = row * chromaWidth + x0 - 4;
                    byte[] uWindow = Arrays.copyOfRange(frame.uBuffer, uStart, uStart + 8);
                    LossyLoopFilter.macroblockFilterHorizontal(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            macroblockEdgeLimit,
                            uWindow
                    );
                    System.arraycopy(uWindow, 0, frame.uBuffer, uStart, 8);

                    int vStart = row * chromaWidth + x0 - 4;
                    byte[] vWindow = Arrays.copyOfRange(frame.vBuffer, vStart, vStart + 8);
                    LossyLoopFilter.macroblockFilterHorizontal(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            macroblockEdgeLimit,
                            vWindow
                    );
                    System.arraycopy(vWindow, 0, frame.vBuffer, vStart, 8);
                }
            }
        }

        if (doSubblockFiltering) {
            if (frame.filterType) {
                for (int x = 4; x < 15; x += 4) {
                    for (int y = 0; y < 16; y++) {
                        int row = macroblockY * 16 + y;
                        int x0 = macroblockX * 16 + x;
                        int start = row * lumaWidth + x0 - 4;
                        byte[] window = Arrays.copyOfRange(frame.yBuffer, start, start + 8);
                        LossyLoopFilter.simpleSegmentHorizontal(subblockEdgeLimit, window);
                        System.arraycopy(window, 0, frame.yBuffer, start, 8);
                    }
                }
            } else {
                for (int x = 4; x < 13; x += 4) {
                    for (int y = 0; y < 16; y++) {
                        int row = macroblockY * 16 + y;
                        int x0 = macroblockX * 16 + x;
                        int start = row * lumaWidth + x0 - 4;
                        byte[] window = Arrays.copyOfRange(frame.yBuffer, start, start + 8);
                        LossyLoopFilter.subblockFilterHorizontal(
                                parameters.hevThreshold,
                                parameters.interiorLimit,
                                subblockEdgeLimit,
                                window
                        );
                        System.arraycopy(window, 0, frame.yBuffer, start, 8);
                    }
                }

                for (int y = 0; y < 8; y++) {
                    int row = macroblockY * 8 + y;
                    int x0 = macroblockX * 8 + 4;
                    int uStart = row * chromaWidth + x0 - 4;
                    byte[] uWindow = Arrays.copyOfRange(frame.uBuffer, uStart, uStart + 8);
                    LossyLoopFilter.subblockFilterHorizontal(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            subblockEdgeLimit,
                            uWindow
                    );
                    System.arraycopy(uWindow, 0, frame.uBuffer, uStart, 8);

                    int vStart = row * chromaWidth + x0 - 4;
                    byte[] vWindow = Arrays.copyOfRange(frame.vBuffer, vStart, vStart + 8);
                    LossyLoopFilter.subblockFilterHorizontal(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            subblockEdgeLimit,
                            vWindow
                    );
                    System.arraycopy(vWindow, 0, frame.vBuffer, vStart, 8);
                }
            }
        }

        if (macroblockY > 0) {
            if (frame.filterType) {
                for (int x = 0; x < 16; x++) {
                    int y0 = macroblockY * 16;
                    int x0 = macroblockX * 16 + x;
                    LossyLoopFilter.simpleSegmentVertical(
                            macroblockEdgeLimit,
                            frame.yBuffer,
                            y0 * lumaWidth + x0,
                            lumaWidth
                    );
                }
            } else {
                for (int x = 0; x < 16; x++) {
                    int y0 = macroblockY * 16;
                    int x0 = macroblockX * 16 + x;
                    LossyLoopFilter.macroblockFilterVertical(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            macroblockEdgeLimit,
                            frame.yBuffer,
                            y0 * lumaWidth + x0,
                            lumaWidth
                    );
                }

                for (int x = 0; x < 8; x++) {
                    int y0 = macroblockY * 8;
                    int x0 = macroblockX * 8 + x;
                    LossyLoopFilter.macroblockFilterVertical(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            macroblockEdgeLimit,
                            frame.uBuffer,
                            y0 * chromaWidth + x0,
                            chromaWidth
                    );
                    LossyLoopFilter.macroblockFilterVertical(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            macroblockEdgeLimit,
                            frame.vBuffer,
                            y0 * chromaWidth + x0,
                            chromaWidth
                    );
                }
            }
        }

        if (doSubblockFiltering) {
            if (frame.filterType) {
                for (int y = 4; y < 15; y += 4) {
                    for (int x = 0; x < 16; x++) {
                        int y0 = macroblockY * 16 + y;
                        int x0 = macroblockX * 16 + x;
                        LossyLoopFilter.simpleSegmentVertical(
                                subblockEdgeLimit,
                                frame.yBuffer,
                                y0 * lumaWidth + x0,
                                lumaWidth
                        );
                    }
                }
            } else {
                for (int y = 4; y < 13; y += 4) {
                    for (int x = 0; x < 16; x++) {
                        int y0 = macroblockY * 16 + y;
                        int x0 = macroblockX * 16 + x;
                        LossyLoopFilter.subblockFilterVertical(
                                parameters.hevThreshold,
                                parameters.interiorLimit,
                                subblockEdgeLimit,
                                frame.yBuffer,
                                y0 * lumaWidth + x0,
                                lumaWidth
                        );
                    }
                }

                for (int x = 0; x < 8; x++) {
                    int y0 = macroblockY * 8 + 4;
                    int x0 = macroblockX * 8 + x;
                    LossyLoopFilter.subblockFilterVertical(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            subblockEdgeLimit,
                            frame.uBuffer,
                            y0 * chromaWidth + x0,
                            chromaWidth
                    );
                    LossyLoopFilter.subblockFilterVertical(
                            parameters.hevThreshold,
                            parameters.interiorLimit,
                            subblockEdgeLimit,
                            frame.vBuffer,
                            y0 * chromaWidth + x0,
                            chromaWidth
                    );
                }
            }
        }
    }

    private FilterParameters calculateFilterParameters(MacroBlock macroBlock) {
        Segment segment = segments[macroBlock.segmentId];
        int filterLevel = frame.filterLevel;
        if (filterLevel == 0) {
            return new FilterParameters(0, 0, 0);
        }

        if (segmentsEnabled) {
            filterLevel = segment.deltaValues ? filterLevel + segment.loopFilterLevel : segment.loopFilterLevel;
        }
        filterLevel = Math.max(0, Math.min(63, filterLevel));

        if (loopFilterAdjustmentsEnabled) {
            filterLevel += refDelta[0];
            if (macroBlock.lumaMode == LumaMode.B) {
                filterLevel += modeDelta[0];
            }
        }
        filterLevel = Math.max(0, Math.min(63, filterLevel));

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
        return new FilterParameters(filterLevel, interiorLimit, hevThreshold);
    }

    private Vp8Frame decodeFrameInternal() throws IOException, WebPException {
        readFrameHeader();

        for (int macroblockY = 0; macroblockY < macroblockHeight; macroblockY++) {
            int partition = macroblockY % numPartitions;
            left = new PreviousMacroBlock();

            for (int macroblockX = 0; macroblockX < macroblockWidth; macroblockX++) {
                MacroBlock macroBlock = readMacroblockHeader(macroblockX);
                int[] blocks;
                if (!macroBlock.coefficientsSkipped) {
                    blocks = readResidualData(macroBlock, macroblockX, partition);
                } else {
                    if (macroBlock.lumaMode != LumaMode.B) {
                        left.complexity[0] = 0;
                        top[macroblockX].complexity[0] = 0;
                    }
                    for (int i = 1; i < 9; i++) {
                        left.complexity[i] = 0;
                        top[macroblockX].complexity[i] = 0;
                    }
                    blocks = new int[384];
                }

                intraPredictLuma(macroblockX, macroblockY, macroBlock, blocks);
                intraPredictChroma(macroblockX, macroblockY, macroBlock, blocks);
                macroblocks.add(macroBlock);
            }

            leftBorderY = filled(17, (byte) 129);
            leftBorderU = filled(9, (byte) 129);
            leftBorderV = filled(9, (byte) 129);
        }

        for (int macroblockY = 0; macroblockY < macroblockHeight; macroblockY++) {
            for (int macroblockX = 0; macroblockX < macroblockWidth; macroblockX++) {
                MacroBlock macroBlock = macroblocks.get(macroblockY * macroblockWidth + macroblockX);
                loopFilter(macroblockX, macroblockY, macroBlock);
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

    private static short dcQuant(int index) {
        return LossyTables.DC_QUANT[Math.max(0, Math.min(127, index))];
    }

    private static short acQuant(int index) {
        return LossyTables.AC_QUANT[Math.max(0, Math.min(127, index))];
    }

    private byte[] readExactly(int length) throws IOException, WebPException {
        byte[] data = input.readNBytes(length);
        if (data.length != length) {
            throw new WebPException("Unexpected end of VP8 partition data");
        }
        return data;
    }

    private static int readU8(InputStream input) throws IOException, WebPException {
        int value = input.read();
        if (value < 0) {
            throw new WebPException("Unexpected end of VP8 stream");
        }
        return value;
    }

    private static int readU16LE(InputStream input) throws IOException, WebPException {
        return readU8(input) | (readU8(input) << 8);
    }

    private static int readU24LE(InputStream input) throws IOException, WebPException {
        return readU8(input) | (readU8(input) << 8) | (readU8(input) << 16);
    }

    @NotNullByDefault
    private static final class MacroBlock {
        final IntraMode[] bpred = new IntraMode[16];
        LumaMode lumaMode = LumaMode.DC;
        ChromaMode chromaMode = ChromaMode.DC;
        int segmentId;
        boolean coefficientsSkipped;
        boolean nonZeroDct;

        private MacroBlock() {
            Arrays.fill(bpred, IntraMode.DC);
        }
    }

    @NotNullByDefault
    private static final class PreviousMacroBlock {
        final IntraMode[] bpred = new IntraMode[4];
        final int[] complexity = new int[9];

        private PreviousMacroBlock() {
            Arrays.fill(bpred, IntraMode.DC);
        }
    }

    @NotNullByDefault
    private record FilterParameters(int filterLevel, int interiorLimit, int hevThreshold) {
    }
}
