// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Shared VP8 lossy constants and compact mode representations.
///
/// This class intentionally starts with the mode definitions and dimensions that are required by
/// the prediction and reconstruction helpers. Additional probability tables are added alongside the
/// main VP8 decoder port, where they are actually consumed.
@NotNullByDefault
final class LossyCommon {

    /// Number of independently configurable VP8 segments.
    static final int MAX_SEGMENTS = 4;

    /// Number of VP8 DCT coefficient tokens.
    static final int NUM_DCT_TOKENS = 12;

    /// Number of bits used for one packed 4x4 intra-prediction mode.
    private static final int PACKED_INTRA_MODE_BITS = 4;

    /// Mask selecting one packed 4x4 intra-prediction mode.
    private static final long PACKED_INTRA_MODE_MASK = (1L << PACKED_INTRA_MODE_BITS) - 1L;

    /// Number of 4x4 intra-prediction modes stored in one packed macroblock value.
    static final int PACKED_INTRA_MODE_COUNT = Long.SIZE / PACKED_INTRA_MODE_BITS;

    /// Prevents instantiation.
    private LossyCommon() {
    }

    /// Identifies a VP8 coefficient probability plane.
    @MagicConstant(valuesFromClass = Plane.class)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.PARAMETER})
    @NotNullByDefault
    @interface Plane {
        /// Luma coefficients whose DC terms come from the Y2 transform.
        int Y_COEFF_1 = 0;

        /// Walsh-Hadamard transformed luma DC coefficients.
        int Y2 = 1;

        /// Chroma coefficients.
        int CHROMA = 2;

        /// Luma coefficients containing their own DC terms.
        int Y_COEFF_0 = 3;
    }

    /// Identifies a VP8 macroblock luma prediction mode.
    @MagicConstant(valuesFromClass = LumaMode.class)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.PARAMETER})
    @NotNullByDefault
    @interface LumaMode {
        /// DC prediction.
        int DC = 0;

        /// Vertical prediction.
        int V = 1;

        /// Horizontal prediction.
        int H = 2;

        /// True-motion prediction.
        int TM = 3;

        /// Independently predicted 4x4 luma blocks.
        int B = 4;
    }

    /// Identifies a VP8 macroblock chroma prediction mode.
    @MagicConstant(valuesFromClass = ChromaMode.class)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.PARAMETER})
    @NotNullByDefault
    @interface ChromaMode {
        /// DC prediction.
        int DC = 0;

        /// Vertical prediction.
        int V = 1;

        /// Horizontal prediction.
        int H = 2;

        /// True-motion prediction.
        int TM = 3;
    }

    /// Identifies a VP8 4x4 luma intra-prediction mode.
    @MagicConstant(valuesFromClass = IntraMode.class)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.PARAMETER})
    @NotNullByDefault
    @interface IntraMode {
        /// DC prediction.
        int DC = 0;

        /// True-motion prediction.
        int TM = 1;

        /// Vertical-edge prediction.
        int VE = 2;

        /// Horizontal-edge prediction.
        int HE = 3;

        /// Left-down diagonal prediction.
        int LD = 4;

        /// Right-down diagonal prediction.
        int RD = 5;

        /// Vertical-right diagonal prediction.
        int VR = 6;

        /// Vertical-left diagonal prediction.
        int VL = 7;

        /// Horizontal-down diagonal prediction.
        int HD = 8;

        /// Horizontal-up diagonal prediction.
        int HU = 9;
    }

    /// Converts a macroblock luma mode to its shared 4x4 intra-prediction mode.
    ///
    /// @param mode the luma mode, which must not be [LumaMode#B]
    /// @return the corresponding intra-prediction mode
    /// @throws IllegalArgumentException if `mode` is [LumaMode#B] or is not a luma mode
    @IntraMode
    static int toIntraMode(@LumaMode int mode) {
        return switch (mode) {
            case LumaMode.DC -> IntraMode.DC;
            case LumaMode.V -> IntraMode.VE;
            case LumaMode.H -> IntraMode.HE;
            case LumaMode.TM -> IntraMode.TM;
            default -> throw new IllegalArgumentException("Luma block mode has no shared intra mode: " + mode);
        };
    }

    /// Replaces one mode in a packed sequence of 16 intra-prediction modes.
    ///
    /// The mode at index zero occupies the least-significant four bits.
    ///
    /// @param modes the packed modes to update
    /// @param index the mode index, from `0` through `15`
    /// @param mode the replacement intra-prediction mode
    /// @return the updated packed modes
    static long setIntraMode(long modes, int index, @IntraMode int mode) {
        int shift = index << 2;
        long shiftedMask = PACKED_INTRA_MODE_MASK << shift;
        return (modes & ~shiftedMask) | ((long) mode << shift);
    }

    /// Returns one mode from a packed sequence of 16 intra-prediction modes.
    ///
    /// @param modes the packed modes
    /// @param index the mode index, from `0` through `15`
    /// @return the intra-prediction mode at `index`
    @IntraMode
    static int getIntraMode(long modes, int index) {
        //noinspection MagicConstant
        return (int) ((modes >>> (index << 2)) & PACKED_INTRA_MODE_MASK);
    }

}
