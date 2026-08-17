// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.internal.Argb;

/// Integer alpha blending helpers based on the WebP animation reference implementation.
@NotNullByDefault
public final class AlphaBlending {

    private AlphaBlending() {
    }

    /// Blends a single non-premultiplied `ARGB` source pixel over a destination pixel.
    ///
    /// @param src the source `ARGB` pixel
    /// @param dst the destination `ARGB` pixel
    /// @return the blended non-premultiplied `ARGB` pixel
    public static int blend(int src, int dst) {
        int srcA = Argb.alpha(src);
        if (srcA == 0) {
            return dst;
        }
        if (srcA == 0xFF) {
            return src;
        }

        int dstA = Argb.alpha(dst);
        if (dstA == 0) {
            return src;
        }
        int dstFactorA = divBy255(dstA * (255 - srcA));
        int blendA = srcA + dstFactorA;
        int scale = (1 << 24) / blendA;

        int r = blendChannel(Argb.red(src), srcA, Argb.red(dst), dstFactorA, scale);
        int g = blendChannel(Argb.green(src), srcA, Argb.green(dst), dstFactorA, scale);
        int b = blendChannel(Argb.blue(src), srcA, Argb.blue(dst), dstFactorA, scale);
        return Argb.pack(blendA, r, g, b);
    }

    private static int blendChannel(int src, int srcA, int dst, int dstA, int scale) {
        int blended = src * srcA + dst * dstA;
        return (blended * scale) >> 24;
    }

    private static int divBy255(int value) {
        return (((value + 0x80) >> 8) + value + 0x80) >> 8;
    }
}
