// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Provides immutable lookup tables used by the VP8 lossy decoder.
@NotNullByDefault
final class LossyTables {

    /// Segment-ID decoding tree.
    static final byte @Unmodifiable [] SEGMENT_ID_TREE = {2, 4, 0, -1, -2, -3};

    /// Keyframe luma-mode decoding tree.
    static final byte @Unmodifiable [] KEYFRAME_YMODE_TREE = {
            -LossyCommon.LumaMode.B, 2, 4, 6,
            -LossyCommon.LumaMode.DC, -LossyCommon.LumaMode.V,
            -LossyCommon.LumaMode.H, -LossyCommon.LumaMode.TM
    };

    /// Keyframe luma-mode probabilities in tree-node order.
    static final byte @Unmodifiable [] KEYFRAME_YMODE_PROBS = {
            (byte) 145, (byte) 156, (byte) 163, (byte) 128
    };

    /// Keyframe B-pred mode decoding tree.
    static final byte @Unmodifiable [] KEYFRAME_BPRED_MODE_TREE = {
            -LossyCommon.IntraMode.DC, 2, -LossyCommon.IntraMode.TM, 4,
            -LossyCommon.IntraMode.VE, 6, 8, 12,
            -LossyCommon.IntraMode.HE, 10, -LossyCommon.IntraMode.RD, -LossyCommon.IntraMode.VR,
            -LossyCommon.IntraMode.LD, 14, -LossyCommon.IntraMode.VL, 16,
            -LossyCommon.IntraMode.HD, -LossyCommon.IntraMode.HU
    };
    /// Number of branch probabilities in one B-pred mode context.
    static final int BPRED_MODE_PROBABILITY_COUNT = 9;

    /// Number of neighboring B-pred modes represented by each context dimension.
    static final int BPRED_MODE_CONTEXT_COUNT = 10;

    /// Number of probability entries for all left-mode contexts under one above mode.
    static final int BPRED_MODE_ABOVE_STRIDE = BPRED_MODE_CONTEXT_COUNT * BPRED_MODE_PROBABILITY_COUNT;

    /// Keyframe B-pred probabilities in above-mode, left-mode, and tree-node order.
    ///
    /// Mode numbers in the grouping comments correspond to [LossyCommon.IntraMode].
    static final byte @Unmodifiable [] KEYFRAME_BPRED_MODE_PROBS = {
            // Above mode 0
            (byte) 231, 120, 48, 89, 115, 113, 120, (byte) 152, 112, // Left mode 0
            (byte) 152, (byte) 179, 64, 126, (byte) 170, 118, 46, 70, 95, // Left mode 1
            (byte) 175, 69, (byte) 143, 80, 85, 82, 72, (byte) 155, 103, // Left mode 2
            56, 58, 10, (byte) 171, (byte) 218, (byte) 189, 17, 13, (byte) 152, // Left mode 3
            (byte) 144, 71, 10, 38, (byte) 171, (byte) 213, (byte) 144, 34, 26, // Left mode 4
            114, 26, 17, (byte) 163, 44, (byte) 195, 21, 10, (byte) 173, // Left mode 5
            121, 24, 80, (byte) 195, 26, 62, 44, 64, 85, // Left mode 6
            (byte) 170, 46, 55, 19, (byte) 136, (byte) 160, 33, (byte) 206, 71, // Left mode 7
            63, 20, 8, 114, 114, (byte) 208, 12, 9, (byte) 226, // Left mode 8
            81, 40, 11, 96, (byte) 182, 84, 29, 16, 36, // Left mode 9
            // Above mode 1
            (byte) 134, (byte) 183, 89, (byte) 137, 98, 101, 106, (byte) 165, (byte) 148, // Left mode 0
            72, (byte) 187, 100, (byte) 130, (byte) 157, 111, 32, 75, 80, // Left mode 1
            66, 102, (byte) 167, 99, 74, 62, 40, (byte) 234, (byte) 128, // Left mode 2
            41, 53, 9, (byte) 178, (byte) 241, (byte) 141, 26, 8, 107, // Left mode 3
            104, 79, 12, 27, (byte) 217, (byte) 255, 87, 17, 7, // Left mode 4
            74, 43, 26, (byte) 146, 73, (byte) 166, 49, 23, (byte) 157, // Left mode 5
            65, 38, 105, (byte) 160, 51, 52, 31, 115, (byte) 128, // Left mode 6
            87, 68, 71, 44, 114, 51, 15, (byte) 186, 23, // Left mode 7
            47, 41, 14, 110, (byte) 182, (byte) 183, 21, 17, (byte) 194, // Left mode 8
            66, 45, 25, 102, (byte) 197, (byte) 189, 23, 18, 22, // Left mode 9
            // Above mode 2
            88, 88, (byte) 147, (byte) 150, 42, 46, 45, (byte) 196, (byte) 205, // Left mode 0
            43, 97, (byte) 183, 117, 85, 38, 35, (byte) 179, 61, // Left mode 1
            39, 53, (byte) 200, 87, 26, 21, 43, (byte) 232, (byte) 171, // Left mode 2
            56, 34, 51, 104, 114, 102, 29, 93, 77, // Left mode 3
            107, 54, 32, 26, 51, 1, 81, 43, 31, // Left mode 4
            39, 28, 85, (byte) 171, 58, (byte) 165, 90, 98, 64, // Left mode 5
            34, 22, 116, (byte) 206, 23, 34, 43, (byte) 166, 73, // Left mode 6
            68, 25, 106, 22, 64, (byte) 171, 36, (byte) 225, 114, // Left mode 7
            34, 19, 21, 102, (byte) 132, (byte) 188, 16, 76, 124, // Left mode 8
            62, 18, 78, 95, 85, 57, 50, 48, 51, // Left mode 9
            // Above mode 3
            (byte) 193, 101, 35, (byte) 159, (byte) 215, 111, 89, 46, 111, // Left mode 0
            60, (byte) 148, 31, (byte) 172, (byte) 219, (byte) 228, 21, 18, 111, // Left mode 1
            112, 113, 77, 85, (byte) 179, (byte) 255, 38, 120, 114, // Left mode 2
            40, 42, 1, (byte) 196, (byte) 245, (byte) 209, 10, 25, 109, // Left mode 3
            100, 80, 8, 43, (byte) 154, 1, 51, 26, 71, // Left mode 4
            88, 43, 29, (byte) 140, (byte) 166, (byte) 213, 37, 43, (byte) 154, // Left mode 5
            61, 63, 30, (byte) 155, 67, 45, 68, 1, (byte) 209, // Left mode 6
            (byte) 142, 78, 78, 16, (byte) 255, (byte) 128, 34, (byte) 197, (byte) 171, // Left mode 7
            41, 40, 5, 102, (byte) 211, (byte) 183, 4, 1, (byte) 221, // Left mode 8
            51, 50, 17, (byte) 168, (byte) 209, (byte) 192, 23, 25, 82, // Left mode 9
            // Above mode 4
            125, 98, 42, 88, 104, 85, 117, (byte) 175, 82, // Left mode 0
            95, 84, 53, 89, (byte) 128, 100, 113, 101, 45, // Left mode 1
            75, 79, 123, 47, 51, (byte) 128, 81, (byte) 171, 1, // Left mode 2
            57, 17, 5, 71, 102, 57, 53, 41, 49, // Left mode 3
            115, 21, 2, 10, 102, (byte) 255, (byte) 166, 23, 6, // Left mode 4
            38, 33, 13, 121, 57, 73, 26, 1, 85, // Left mode 5
            41, 10, 67, (byte) 138, 77, 110, 90, 47, 114, // Left mode 6
            101, 29, 16, 10, 85, (byte) 128, 101, (byte) 196, 26, // Left mode 7
            57, 18, 10, 102, 102, (byte) 213, 34, 20, 43, // Left mode 8
            117, 20, 15, 36, (byte) 163, (byte) 128, 68, 1, 26, // Left mode 9
            // Above mode 5
            (byte) 138, 31, 36, (byte) 171, 27, (byte) 166, 38, 44, (byte) 229, // Left mode 0
            67, 87, 58, (byte) 169, 82, 115, 26, 59, (byte) 179, // Left mode 1
            63, 59, 90, (byte) 180, 59, (byte) 166, 93, 73, (byte) 154, // Left mode 2
            40, 40, 21, 116, (byte) 143, (byte) 209, 34, 39, (byte) 175, // Left mode 3
            57, 46, 22, 24, (byte) 128, 1, 54, 17, 37, // Left mode 4
            47, 15, 16, (byte) 183, 34, (byte) 223, 49, 45, (byte) 183, // Left mode 5
            46, 17, 33, (byte) 183, 6, 98, 15, 32, (byte) 183, // Left mode 6
            65, 32, 73, 115, 28, (byte) 128, 23, (byte) 128, (byte) 205, // Left mode 7
            40, 3, 9, 115, 51, (byte) 192, 18, 6, (byte) 223, // Left mode 8
            87, 37, 9, 115, 59, 77, 64, 21, 47, // Left mode 9
            // Above mode 6
            104, 55, 44, (byte) 218, 9, 54, 53, (byte) 130, (byte) 226, // Left mode 0
            64, 90, 70, (byte) 205, 40, 41, 23, 26, 57, // Left mode 1
            54, 57, 112, (byte) 184, 5, 41, 38, (byte) 166, (byte) 213, // Left mode 2
            30, 34, 26, (byte) 133, (byte) 152, 116, 10, 32, (byte) 134, // Left mode 3
            75, 32, 12, 51, (byte) 192, (byte) 255, (byte) 160, 43, 51, // Left mode 4
            39, 19, 53, (byte) 221, 26, 114, 32, 73, (byte) 255, // Left mode 5
            31, 9, 65, (byte) 234, 2, 15, 1, 118, 73, // Left mode 6
            88, 31, 35, 67, 102, 85, 55, (byte) 186, 85, // Left mode 7
            56, 21, 23, 111, 59, (byte) 205, 45, 37, (byte) 192, // Left mode 8
            55, 38, 70, 124, 73, 102, 1, 34, 98, // Left mode 9
            // Above mode 7
            102, 61, 71, 37, 34, 53, 31, (byte) 243, (byte) 192, // Left mode 0
            69, 60, 71, 38, 73, 119, 28, (byte) 222, 37, // Left mode 1
            68, 45, (byte) 128, 34, 1, 47, 11, (byte) 245, (byte) 171, // Left mode 2
            62, 17, 19, 70, (byte) 146, 85, 55, 62, 70, // Left mode 3
            75, 15, 9, 9, 64, (byte) 255, (byte) 184, 119, 16, // Left mode 4
            37, 43, 37, (byte) 154, 100, (byte) 163, 85, (byte) 160, 1, // Left mode 5
            63, 9, 92, (byte) 136, 28, 64, 32, (byte) 201, 85, // Left mode 6
            86, 6, 28, 5, 64, (byte) 255, 25, (byte) 248, 1, // Left mode 7
            56, 8, 17, (byte) 132, (byte) 137, (byte) 255, 55, 116, (byte) 128, // Left mode 8
            58, 15, 20, 82, (byte) 135, 57, 26, 121, 40, // Left mode 9
            // Above mode 8
            (byte) 164, 50, 31, (byte) 137, (byte) 154, (byte) 133, 25, 35, (byte) 218, // Left mode 0
            51, 103, 44, (byte) 131, (byte) 131, 123, 31, 6, (byte) 158, // Left mode 1
            86, 40, 64, (byte) 135, (byte) 148, (byte) 224, 45, (byte) 183, (byte) 128, // Left mode 2
            22, 26, 17, (byte) 131, (byte) 240, (byte) 154, 14, 1, (byte) 209, // Left mode 3
            83, 12, 13, 54, (byte) 192, (byte) 255, 68, 47, 28, // Left mode 4
            45, 16, 21, 91, 64, (byte) 222, 7, 1, (byte) 197, // Left mode 5
            56, 21, 39, (byte) 155, 60, (byte) 138, 23, 102, (byte) 213, // Left mode 6
            85, 26, 85, 85, (byte) 128, (byte) 128, 32, (byte) 146, (byte) 171, // Left mode 7
            18, 11, 7, 63, (byte) 144, (byte) 171, 4, 4, (byte) 246, // Left mode 8
            35, 27, 10, (byte) 146, (byte) 174, (byte) 171, 12, 26, (byte) 128, // Left mode 9
            // Above mode 9
            (byte) 190, 80, 35, 99, (byte) 180, 80, 126, 54, 45, // Left mode 0
            85, 126, 47, 87, (byte) 176, 51, 41, 20, 32, // Left mode 1
            101, 75, (byte) 128, (byte) 139, 118, (byte) 146, 116, (byte) 128, 85, // Left mode 2
            56, 41, 15, (byte) 176, (byte) 236, 85, 37, 9, 62, // Left mode 3
            (byte) 146, 36, 19, 30, (byte) 171, (byte) 255, 97, 27, 20, // Left mode 4
            71, 30, 17, 119, 118, (byte) 255, 17, 18, (byte) 138, // Left mode 5
            101, 38, 60, (byte) 138, 55, 70, 43, 26, (byte) 142, // Left mode 6
            (byte) 138, 45, 61, 62, (byte) 219, 1, 81, (byte) 188, 64, // Left mode 7
            32, 41, 20, 117, (byte) 151, (byte) 142, 20, 21, (byte) 163, // Left mode 8
            112, 19, 12, 61, (byte) 195, (byte) 128, 48, 4, 24 // Left mode 9
    };
    /// Keyframe chroma-mode decoding tree.
    static final byte @Unmodifiable [] KEYFRAME_UV_MODE_TREE = {
            -LossyCommon.ChromaMode.DC, 2, -LossyCommon.ChromaMode.V, 4,
            -LossyCommon.ChromaMode.H, -LossyCommon.ChromaMode.TM
    };

    /// Keyframe chroma-mode probabilities in tree-node order.
    static final byte @Unmodifiable [] KEYFRAME_UV_MODE_PROBS = {
            (byte) 142, 114, (byte) 183
    };

    /// Coefficient-update probabilities in plane, band, context, and token order.
    ///
    /// Plane numbers in the grouping comments correspond to [LossyCommon.Plane].
    static final byte @Unmodifiable [] FLAT_COEFF_UPDATE_PROBS = {
            // Plane 0, band 0
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 0, band 1
            (byte) 176, (byte) 246, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 223, (byte) 241, (byte) 252, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 249, (byte) 253, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 0, band 2
            (byte) 255, (byte) 244, (byte) 252, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 234, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 0, band 3
            (byte) 255, (byte) 246, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 239, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 254, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 0, band 4
            (byte) 255, (byte) 248, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 251, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 0, band 5
            (byte) 255, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 251, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 254, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 0, band 6
            (byte) 255, (byte) 254, (byte) 253, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 250, (byte) 255, (byte) 254, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 0, band 7
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 0
            (byte) 217, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 225, (byte) 252, (byte) 241, (byte) 253, (byte) 255, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 234, (byte) 250, (byte) 241, (byte) 250, (byte) 253, (byte) 255, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 1
            (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 223, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 238, (byte) 253, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 2
            (byte) 255, (byte) 248, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 249, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 3
            (byte) 255, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 247, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 4
            (byte) 255, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 252, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 5
            (byte) 255, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 6
            (byte) 255, (byte) 254, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 250, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 1, band 7
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 0
            (byte) 186, (byte) 251, (byte) 250, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 234, (byte) 251, (byte) 244, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 251, (byte) 251, (byte) 243, (byte) 253, (byte) 254, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 1
            (byte) 255, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 236, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 251, (byte) 253, (byte) 253, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 2
            (byte) 255, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 254, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 3
            (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 4
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 5
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 6
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 2, band 7
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 0
            (byte) 248, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 250, (byte) 254, (byte) 252, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 248, (byte) 254, (byte) 249, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 1
            (byte) 255, (byte) 253, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 246, (byte) 253, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 252, (byte) 254, (byte) 251, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 2
            (byte) 255, (byte) 254, (byte) 252, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 248, (byte) 254, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 253, (byte) 255, (byte) 254, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 3
            (byte) 255, (byte) 251, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 245, (byte) 251, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 253, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 4
            (byte) 255, (byte) 251, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 252, (byte) 253, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 5
            (byte) 255, (byte) 252, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 249, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 6
            (byte) 255, (byte) 255, (byte) 253, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 250, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 2
            // Plane 3, band 7
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 0
            (byte) 254, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, // Context 1
            (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255 // Context 2
    };

    /// Default coefficient probabilities in plane, band, context, and token order.
    ///
    /// Plane numbers in the grouping comments correspond to [LossyCommon.Plane].
    static final byte @Unmodifiable [] FLAT_COEFF_PROBS = {
            // Plane 0, band 0
            (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 0, band 1
            (byte) 253, (byte) 136, (byte) 254, (byte) 255, (byte) 228, (byte) 219, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 189, (byte) 129, (byte) 242, (byte) 255, (byte) 227, (byte) 213, (byte) 255, (byte) 219, (byte) 128, (byte) 128, (byte) 128, // Context 1
            106, 126, (byte) 227, (byte) 252, (byte) 214, (byte) 209, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 0, band 2
            1, 98, (byte) 248, (byte) 255, (byte) 236, (byte) 226, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 181, (byte) 133, (byte) 238, (byte) 254, (byte) 221, (byte) 234, (byte) 255, (byte) 154, (byte) 128, (byte) 128, (byte) 128, // Context 1
            78, (byte) 134, (byte) 202, (byte) 247, (byte) 198, (byte) 180, (byte) 255, (byte) 219, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 0, band 3
            1, (byte) 185, (byte) 249, (byte) 255, (byte) 243, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 184, (byte) 150, (byte) 247, (byte) 255, (byte) 236, (byte) 224, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            77, 110, (byte) 216, (byte) 255, (byte) 236, (byte) 230, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 0, band 4
            1, 101, (byte) 251, (byte) 255, (byte) 241, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 170, (byte) 139, (byte) 241, (byte) 252, (byte) 236, (byte) 209, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 1
            37, 116, (byte) 196, (byte) 243, (byte) 228, (byte) 255, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 0, band 5
            1, (byte) 204, (byte) 254, (byte) 255, (byte) 245, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 207, (byte) 160, (byte) 250, (byte) 255, (byte) 238, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            102, 103, (byte) 231, (byte) 255, (byte) 211, (byte) 171, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 0, band 6
            1, (byte) 152, (byte) 252, (byte) 255, (byte) 240, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 177, (byte) 135, (byte) 243, (byte) 255, (byte) 234, (byte) 225, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            80, (byte) 129, (byte) 211, (byte) 255, (byte) 194, (byte) 224, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 0, band 7
            1, 1, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 246, 1, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 1, band 0
            (byte) 198, 35, (byte) 237, (byte) 223, (byte) 193, (byte) 187, (byte) 162, (byte) 160, (byte) 145, (byte) 155, 62, // Context 0
            (byte) 131, 45, (byte) 198, (byte) 221, (byte) 172, (byte) 176, (byte) 220, (byte) 157, (byte) 252, (byte) 221, 1, // Context 1
            68, 47, (byte) 146, (byte) 208, (byte) 149, (byte) 167, (byte) 221, (byte) 162, (byte) 255, (byte) 223, (byte) 128, // Context 2
            // Plane 1, band 1
            1, (byte) 149, (byte) 241, (byte) 255, (byte) 221, (byte) 224, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 184, (byte) 141, (byte) 234, (byte) 253, (byte) 222, (byte) 220, (byte) 255, (byte) 199, (byte) 128, (byte) 128, (byte) 128, // Context 1
            81, 99, (byte) 181, (byte) 242, (byte) 176, (byte) 190, (byte) 249, (byte) 202, (byte) 255, (byte) 255, (byte) 128, // Context 2
            // Plane 1, band 2
            1, (byte) 129, (byte) 232, (byte) 253, (byte) 214, (byte) 197, (byte) 242, (byte) 196, (byte) 255, (byte) 255, (byte) 128, // Context 0
            99, 121, (byte) 210, (byte) 250, (byte) 201, (byte) 198, (byte) 255, (byte) 202, (byte) 128, (byte) 128, (byte) 128, // Context 1
            23, 91, (byte) 163, (byte) 242, (byte) 170, (byte) 187, (byte) 247, (byte) 210, (byte) 255, (byte) 255, (byte) 128, // Context 2
            // Plane 1, band 3
            1, (byte) 200, (byte) 246, (byte) 255, (byte) 234, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            109, (byte) 178, (byte) 241, (byte) 255, (byte) 231, (byte) 245, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 1
            44, (byte) 130, (byte) 201, (byte) 253, (byte) 205, (byte) 192, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 1, band 4
            1, (byte) 132, (byte) 239, (byte) 251, (byte) 219, (byte) 209, (byte) 255, (byte) 165, (byte) 128, (byte) 128, (byte) 128, // Context 0
            94, (byte) 136, (byte) 225, (byte) 251, (byte) 218, (byte) 190, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 1
            22, 100, (byte) 174, (byte) 245, (byte) 186, (byte) 161, (byte) 255, (byte) 199, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 1, band 5
            1, (byte) 182, (byte) 249, (byte) 255, (byte) 232, (byte) 235, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            124, (byte) 143, (byte) 241, (byte) 255, (byte) 227, (byte) 234, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            35, 77, (byte) 181, (byte) 251, (byte) 193, (byte) 211, (byte) 255, (byte) 205, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 1, band 6
            1, (byte) 157, (byte) 247, (byte) 255, (byte) 236, (byte) 231, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 0
            121, (byte) 141, (byte) 235, (byte) 255, (byte) 225, (byte) 227, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 1
            45, 99, (byte) 188, (byte) 251, (byte) 195, (byte) 217, (byte) 255, (byte) 224, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 1, band 7
            1, 1, (byte) 251, (byte) 255, (byte) 213, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 203, 1, (byte) 248, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 137, 1, (byte) 177, (byte) 255, (byte) 224, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 2, band 0
            (byte) 253, 9, (byte) 248, (byte) 251, (byte) 207, (byte) 208, (byte) 255, (byte) 192, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 175, 13, (byte) 224, (byte) 243, (byte) 193, (byte) 185, (byte) 249, (byte) 198, (byte) 255, (byte) 255, (byte) 128, // Context 1
            73, 17, (byte) 171, (byte) 221, (byte) 161, (byte) 179, (byte) 236, (byte) 167, (byte) 255, (byte) 234, (byte) 128, // Context 2
            // Plane 2, band 1
            1, 95, (byte) 247, (byte) 253, (byte) 212, (byte) 183, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 239, 90, (byte) 244, (byte) 250, (byte) 211, (byte) 209, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 155, 77, (byte) 195, (byte) 248, (byte) 188, (byte) 195, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 2, band 2
            1, 24, (byte) 239, (byte) 251, (byte) 218, (byte) 219, (byte) 255, (byte) 205, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 201, 51, (byte) 219, (byte) 255, (byte) 196, (byte) 186, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            69, 46, (byte) 190, (byte) 239, (byte) 201, (byte) 218, (byte) 255, (byte) 228, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 2, band 3
            1, (byte) 191, (byte) 251, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 223, (byte) 165, (byte) 249, (byte) 255, (byte) 213, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 141, 124, (byte) 248, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 2, band 4
            1, 16, (byte) 248, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 190, 36, (byte) 230, (byte) 255, (byte) 236, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 149, 1, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 2, band 5
            1, (byte) 226, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 247, (byte) 192, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 240, (byte) 128, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 2, band 6
            1, (byte) 134, (byte) 252, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 213, 62, (byte) 250, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            55, 93, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 2, band 7
            (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 3, band 0
            (byte) 202, 24, (byte) 213, (byte) 235, (byte) 186, (byte) 191, (byte) 220, (byte) 160, (byte) 240, (byte) 175, (byte) 255, // Context 0
            126, 38, (byte) 182, (byte) 232, (byte) 169, (byte) 184, (byte) 228, (byte) 174, (byte) 255, (byte) 187, (byte) 128, // Context 1
            61, 46, (byte) 138, (byte) 219, (byte) 151, (byte) 178, (byte) 240, (byte) 170, (byte) 255, (byte) 216, (byte) 128, // Context 2
            // Plane 3, band 1
            1, 112, (byte) 230, (byte) 250, (byte) 199, (byte) 191, (byte) 247, (byte) 159, (byte) 255, (byte) 255, (byte) 128, // Context 0
            (byte) 166, 109, (byte) 228, (byte) 252, (byte) 211, (byte) 215, (byte) 255, (byte) 174, (byte) 128, (byte) 128, (byte) 128, // Context 1
            39, 77, (byte) 162, (byte) 232, (byte) 172, (byte) 180, (byte) 245, (byte) 178, (byte) 255, (byte) 255, (byte) 128, // Context 2
            // Plane 3, band 2
            1, 52, (byte) 220, (byte) 246, (byte) 198, (byte) 199, (byte) 249, (byte) 220, (byte) 255, (byte) 255, (byte) 128, // Context 0
            124, 74, (byte) 191, (byte) 243, (byte) 183, (byte) 193, (byte) 250, (byte) 221, (byte) 255, (byte) 255, (byte) 128, // Context 1
            24, 71, (byte) 130, (byte) 219, (byte) 154, (byte) 170, (byte) 243, (byte) 182, (byte) 255, (byte) 255, (byte) 128, // Context 2
            // Plane 3, band 3
            1, (byte) 182, (byte) 225, (byte) 249, (byte) 219, (byte) 240, (byte) 255, (byte) 224, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 149, (byte) 150, (byte) 226, (byte) 252, (byte) 216, (byte) 205, (byte) 255, (byte) 171, (byte) 128, (byte) 128, (byte) 128, // Context 1
            28, 108, (byte) 170, (byte) 242, (byte) 183, (byte) 194, (byte) 254, (byte) 223, (byte) 255, (byte) 255, (byte) 128, // Context 2
            // Plane 3, band 4
            1, 81, (byte) 230, (byte) 252, (byte) 204, (byte) 203, (byte) 255, (byte) 192, (byte) 128, (byte) 128, (byte) 128, // Context 0
            123, 102, (byte) 209, (byte) 247, (byte) 188, (byte) 196, (byte) 255, (byte) 233, (byte) 128, (byte) 128, (byte) 128, // Context 1
            20, 95, (byte) 153, (byte) 243, (byte) 164, (byte) 173, (byte) 255, (byte) 203, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 3, band 5
            1, (byte) 222, (byte) 248, (byte) 255, (byte) 216, (byte) 213, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 168, (byte) 175, (byte) 246, (byte) 252, (byte) 235, (byte) 205, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 1
            47, 116, (byte) 215, (byte) 255, (byte) 211, (byte) 212, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 3, band 6
            1, 121, (byte) 236, (byte) 253, (byte) 212, (byte) 214, (byte) 255, (byte) 255, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 141, 84, (byte) 213, (byte) 252, (byte) 201, (byte) 202, (byte) 255, (byte) 219, (byte) 128, (byte) 128, (byte) 128, // Context 1
            42, 80, (byte) 160, (byte) 240, (byte) 162, (byte) 185, (byte) 255, (byte) 205, (byte) 128, (byte) 128, (byte) 128, // Context 2
            // Plane 3, band 7
            1, 1, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 0
            (byte) 244, 1, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, // Context 1
            (byte) 238, 1, (byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128 // Context 2
    };

    /// Number of coefficient bands in each VP8 plane probability table.
    static final int COEFF_BAND_COUNT = 8;

    /// Number of neighboring-coefficient contexts in each band.
    static final int COEFF_CONTEXT_COUNT = 3;

    /// Number of branch probabilities in the coefficient token tree.
    static final int COEFF_TOKEN_PROBABILITY_COUNT = LossyCommon.NUM_DCT_TOKENS - 1;

    /// Number of coefficient probabilities for one plane.
    static final int COEFF_PROBABILITY_COUNT_PER_PLANE =
            COEFF_BAND_COUNT * COEFF_CONTEXT_COUNT * COEFF_TOKEN_PROBABILITY_COUNT;

    /// Extra-bit probabilities for coefficient categories 3 through 6.
    static final byte @Unmodifiable [] LARGE_DCT_CATEGORY_PROBABILITIES = {
            (byte) 173, (byte) 148, (byte) 140, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            (byte) 176, (byte) 155, (byte) 140, (byte) 135, 0, 0, 0, 0, 0, 0, 0, 0,
            (byte) 180, (byte) 157, (byte) 141, (byte) 134, (byte) 130, 0, 0, 0, 0, 0, 0, 0,
            (byte) 254, (byte) 254, (byte) 243, (byte) 230, (byte) 196, (byte) 177, (byte) 153, (byte) 140, (byte) 133, (byte) 130, (byte) 129, 0
    };

    /// Number of padded probability entries per large coefficient category.
    static final int LARGE_DCT_CATEGORY_STRIDE = 12;

    /// Base coefficient values for categories 3 through 6.
    static final byte @Unmodifiable [] LARGE_DCT_CATEGORY_BASE = {11, 19, 35, 67};

    /// Flat probability-table offset for each coefficient's band.
    static final short @Unmodifiable [] COEFF_BAND_PROBABILITY_OFFSETS = {
            0, 33, 66, 99, 198, 132, 165, 198,
            198, 198, 198, 198, 198, 198, 198, 231
    };
    /// VP8 DC dequantization values indexed by the clamped quantizer level.
    static final byte @Unmodifiable [] DC_QUANT = {
            4, 5, 6, 7, 8, 9, 10, 10, 11, 12, 13, 14, 15, 16, 17, 17,
            18, 19, 20, 20, 21, 21, 22, 22, 23, 23, 24, 25, 25, 26, 27, 28,
            29, 30, 31, 32, 33, 34, 35, 36, 37, 37, 38, 39, 40, 41, 42, 43,
            44, 45, 46, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58,
            59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74,
            75, 76, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
            91, 93, 95, 96, 98, 100, 101, 102, 104, 106, 108, 110, 112, 114, 116, 118,
            122, 124, 126, (byte) 128, (byte) 130, (byte) 132, (byte) 134, (byte) 136,
            (byte) 138, (byte) 140, (byte) 143, (byte) 145, (byte) 148, (byte) 151,
            (byte) 154, (byte) 157
    };

    /// VP8 AC dequantization values indexed by the clamped quantizer level.
    static final short @Unmodifiable [] AC_QUANT = {
            4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51,
            52, 53, 54, 55, 56, 57, 58, 60, 62, 64, 66, 68, 70, 72, 74, 76,
            78, 80, 82, 84, 86, 88, 90, 92, 94, 96, 98, 100, 102, 104, 106, 108,
            110, 112, 114, 116, 119, 122, 125, 128, 131, 134, 137, 140, 143, 146, 149, 152,
            155, 158, 161, 164, 167, 170, 173, 177, 181, 185, 189, 193, 197, 201, 205, 209,
            213, 217, 221, 225, 229, 234, 239, 245, 249, 254, 259, 264, 269, 274, 279, 284
    };
    /// Maps coefficient order to inverse-transform positions.
    static final byte @Unmodifiable [] ZIGZAG = {
            0, 1, 4, 8, 5, 2, 3, 6, 9, 12, 13, 10, 7, 11, 14, 15
    };

    /// Prevents instantiation.
    private LossyTables() {
    }

}
