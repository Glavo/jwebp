// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.IntBuffer;

/// Packed `ARGB` pixel helpers.
///
/// Decoder workspaces use non-premultiplied `0xAARRGGBB` integers. Public frame pixels may remain
/// in that representation or be converted to premultiplied `INT_ARGB_PRE` output.
@NotNullByDefault
public final class Argb {

    /// Smallest unsigned packed pixel whose alpha channel is fully opaque.
    private static final int OPAQUE_PIXEL_MIN = 0xFF00_0000;

    /// Number of pixels checked by each aggregate opaque-prefix scan.
    private static final int OPAQUE_SCAN_BLOCK_SIZE = 8;

    private Argb() {
    }

    /// Packs one non-premultiplied pixel.
    public static int pack(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
    }

    /// Packs one opaque non-premultiplied pixel.
    public static int opaque(int red, int green, int blue) {
        return pack(0xFF, red, green, blue);
    }

    /// Returns the alpha channel.
    public static int alpha(int argb) {
        return argb >>> 24;
    }

    /// Returns the number of leading opaque pixels in an array.
    ///
    /// @param argb the packed `ARGB` pixels to inspect
    /// @return the number of consecutive pixels from index zero whose alpha channel is `255`
    public static int countOpaquePrefix(int[] argb) {
        int index = 0;
        int scalarLimit = Math.min(argb.length, OPAQUE_SCAN_BLOCK_SIZE);
        while (index < scalarLimit
                && Integer.compareUnsigned(argb[index], OPAQUE_PIXEL_MIN) >= 0) {
            index++;
        }
        if (index < scalarLimit) {
            return index;
        }

        int blockLimit = argb.length - (OPAQUE_SCAN_BLOCK_SIZE - 1);
        while (index < blockLimit) {
            // The high byte remains 0xFF only when every pixel in the block is opaque.
            int alphaIntersection = argb[index]
                    & argb[index + 1]
                    & argb[index + 2]
                    & argb[index + 3]
                    & argb[index + 4]
                    & argb[index + 5]
                    & argb[index + 6]
                    & argb[index + 7];
            if (Integer.compareUnsigned(alphaIntersection, OPAQUE_PIXEL_MIN) < 0) {
                break;
            }
            index += OPAQUE_SCAN_BLOCK_SIZE;
        }
        while (index < argb.length
                && Integer.compareUnsigned(argb[index], OPAQUE_PIXEL_MIN) >= 0) {
            index++;
        }
        return index;
    }

    /// Returns the number of leading opaque pixels in a buffer's remaining region.
    ///
    /// The buffer's position and limit are not changed.
    ///
    /// @param argb the packed `ARGB` pixels to inspect
    /// @return the number of consecutive pixels from the current position whose alpha channel is
    ///         `255`
    public static int countOpaquePrefix(IntBuffer argb) {
        int position = argb.position();
        int index = position;
        int limit = argb.limit();
        int scalarLimit = position + Math.min(limit - position, OPAQUE_SCAN_BLOCK_SIZE);
        while (index < scalarLimit
                && Integer.compareUnsigned(argb.get(index), OPAQUE_PIXEL_MIN) >= 0) {
            index++;
        }
        if (index < scalarLimit) {
            return index - position;
        }

        int blockLimit = limit - (OPAQUE_SCAN_BLOCK_SIZE - 1);
        while (index < blockLimit) {
            // The high byte remains 0xFF only when every pixel in the block is opaque.
            int alphaIntersection = argb.get(index)
                    & argb.get(index + 1)
                    & argb.get(index + 2)
                    & argb.get(index + 3)
                    & argb.get(index + 4)
                    & argb.get(index + 5)
                    & argb.get(index + 6)
                    & argb.get(index + 7);
            if (Integer.compareUnsigned(alphaIntersection, OPAQUE_PIXEL_MIN) < 0) {
                break;
            }
            index += OPAQUE_SCAN_BLOCK_SIZE;
        }
        while (index < limit
                && Integer.compareUnsigned(argb.get(index), OPAQUE_PIXEL_MIN) >= 0) {
            index++;
        }
        return index - position;
    }

    /// Returns the red channel.
    public static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    /// Returns the green channel.
    public static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    /// Returns the blue channel.
    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /// Returns the same pixel with a replaced alpha channel.
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FF_FFFF) | ((alpha & 0xFF) << 24);
    }

    /// Converts a non-premultiplied pixel to premultiplied `ARGB`.
    ///
    /// Color channels are rounded to the nearest representable value. A fully transparent input is
    /// converted to zero because premultiplied pixels cannot preserve hidden color channels.
    ///
    /// @param argb the non-premultiplied pixel
    /// @return the premultiplied pixel
    public static int premultiply(int argb) {
        int alpha = alpha(argb);
        if (alpha == 0xFF) {
            return argb;
        }
        if (alpha == 0) {
            return 0;
        }

        int red = (red(argb) * alpha + 0x7F) / 0xFF;
        int green = (green(argb) * alpha + 0x7F) / 0xFF;
        int blue = (blue(argb) * alpha + 0x7F) / 0xFF;
        return pack(alpha, red, green, blue);
    }

    /// Converts a premultiplied pixel to non-premultiplied `ARGB`.
    ///
    /// Conversion cannot recover color information discarded by premultiplication. Fully
    /// transparent input therefore remains zero.
    ///
    /// @param argbPre the premultiplied pixel
    /// @return the non-premultiplied pixel
    public static int unpremultiply(int argbPre) {
        int alpha = alpha(argbPre);
        if (alpha == 0xFF || alpha == 0) {
            return argbPre;
        }

        int halfAlpha = alpha >>> 1;
        int red = unpremultiplyChannel(red(argbPre), alpha, halfAlpha);
        int green = unpremultiplyChannel(green(argbPre), alpha, halfAlpha);
        int blue = unpremultiplyChannel(blue(argbPre), alpha, halfAlpha);
        return pack(alpha, red, green, blue);
    }

    /// Adds two pixels channel-wise with 8-bit wrapping semantics.
    ///
    /// VP8L inverse transforms operate on channels modulo 256, so the packed representation still
    /// needs explicit per-channel addition rather than normal integer addition.
    public static int add(int left, int right) {
        // The unused byte between each selected lane absorbs carries before the final mask.
        int redBlue = (left & 0x00FF_00FF) + (right & 0x00FF_00FF);
        int alphaGreen = (left & 0xFF00_FF00) + (right & 0xFF00_FF00);
        return (redBlue & 0x00FF_00FF) | (alphaGreen & 0xFF00_FF00);
    }

    /// Computes the channel-wise average of two pixels.
    public static int average2(int left, int right) {
        // The masked xor cannot carry into an adjacent byte when added to the common bits.
        return (left & right) + (((left ^ right) >>> 1) & 0x7F7F_7F7F);
    }

    /// Converts one premultiplied color channel to its non-premultiplied value.
    ///
    /// @param value the premultiplied channel
    /// @param alpha the nonzero, non-opaque alpha channel
    /// @param halfAlpha half of `alpha`, used for rounding
    /// @return the unpremultiplied channel clamped to 255
    private static int unpremultiplyChannel(int value, int alpha, int halfAlpha) {
        return value >= alpha ? 0xFF : (value * 0xFF + halfAlpha) / alpha;
    }
}
