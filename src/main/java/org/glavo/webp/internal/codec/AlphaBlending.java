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
