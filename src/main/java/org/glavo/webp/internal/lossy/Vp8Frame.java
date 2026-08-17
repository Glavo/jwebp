// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.glavo.webp.internal.ArrayUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.IntBuffer;

/// Decoded VP8 frame planes.
///
/// The VP8 bitstream stores one full-resolution luma plane plus half-resolution chroma planes.
/// This object keeps those planes until the caller requests packed `ARGB` pixels.
@NotNullByDefault
final class Vp8Frame {

    int width;
    int height;
    byte[] yBuffer = ArrayUtils.EMPTY_BYTE_ARRAY;
    byte[] uBuffer = ArrayUtils.EMPTY_BYTE_ARRAY;
    byte[] vBuffer = ArrayUtils.EMPTY_BYTE_ARRAY;
    byte version;
    boolean keyframe;
    boolean forDisplay;
    byte pixelType;
    boolean filterType;
    byte filterLevel;
    byte sharpnessLevel;

    int chromaWidth() {
        return (width + 1) / 2;
    }

    int bufferWidth() {
        int remainder = width % 16;
        return remainder > 0 ? width + (16 - remainder) : width;
    }

    void fillArgb(int[] buffer, boolean fancyUpsampling) {
        if (fancyUpsampling) {
            LossyYuv.fillArgbBufferFancy(buffer, yBuffer, uBuffer, vBuffer, width, height, bufferWidth());
        } else {
            LossyYuv.fillArgbBufferSimple(buffer, yBuffer, uBuffer, vBuffer, width, chromaWidth(), bufferWidth());
        }
    }

    /// Converts the decoded planes directly into the remaining destination region.
    ///
    /// The destination position and limit are not changed.
    ///
    /// @param buffer the packed `ARGB` destination
    /// @param fancyUpsampling whether to use high-quality chroma upsampling
    void fillArgb(IntBuffer buffer, boolean fancyUpsampling) {
        if (fancyUpsampling) {
            LossyYuv.fillArgbBufferFancy(
                    buffer,
                    yBuffer,
                    uBuffer,
                    vBuffer,
                    width,
                    height,
                    bufferWidth()
            );
        } else {
            LossyYuv.fillArgbBufferSimple(
                    buffer,
                    yBuffer,
                    uBuffer,
                    vBuffer,
                    width,
                    chromaWidth(),
                    bufferWidth()
            );
        }
    }
}
