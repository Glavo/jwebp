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
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;

/// Packed `ARGB` pixel helpers.
///
/// Decoder workspaces use non-premultiplied `0xAARRGGBB` integers. Public frame pixels may remain
/// in that representation or be converted to premultiplied `INT_ARGB_PRE` output.
@NotNullByDefault
public final class Argb {

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
        int alphaGreen = ((left >>> 8) & 0x00FF_00FF) + ((right >>> 8) & 0x00FF_00FF);
        return (redBlue & 0x00FF_00FF) | ((alphaGreen & 0x00FF_00FF) << 8);
    }

    /// Computes the channel-wise average of two pixels.
    public static int average2(int left, int right) {
        return pack(
                (alpha(left) + alpha(right)) / 2,
                (red(left) + red(right)) / 2,
                (green(left) + green(right)) / 2,
                (blue(left) + blue(right)) / 2
        );
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
