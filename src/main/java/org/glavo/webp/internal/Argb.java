// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Objects;

/// Packed `ARGB` pixel helpers.
///
/// Decoder workspaces use non-premultiplied `0xAARRGGBB` integers. Public frame pixels may remain
/// in that representation or be converted to premultiplied `INT_ARGB_PRE` output.
@NotNullByDefault
public final class Argb {

    /// Prevents instantiation.
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
        return countOpaquePrefix(argb, 0, argb.length);
    }

    /// Returns the absolute index after the leading opaque pixels in an array range.
    ///
    /// @param argb the packed `ARGB` pixels to inspect
    /// @param fromIndex the inclusive range start
    /// @param toIndex the exclusive range end
    /// @return the first non-opaque index, or `toIndex` if the range is fully opaque
    /// @throws IndexOutOfBoundsException if the range is outside the array
    private static int countOpaquePrefix(int[] argb, int fromIndex, int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, argb.length);
        int index = fromIndex;
        int blockEnd = toIndex - ((toIndex - fromIndex) & 3);
        for (; index < blockEnd; index += 4) {
            int all = argb[index]
                    & argb[index + 1]
                    & argb[index + 2]
                    & argb[index + 3];
            if ((all & 0xFF00_0000) != 0xFF00_0000) {
                break;
            }
        }
        for (; index < toIndex; index++) {
            if (alpha(argb[index]) != 0xFF) {
                break;
            }
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
        int blockEnd = limit - ((limit - position) & 3);
        for (; index < blockEnd; index += 4) {
            int all = argb.get(index)
                    & argb.get(index + 1)
                    & argb.get(index + 2)
                    & argb.get(index + 3);
            if ((all & 0xFF00_0000) != 0xFF00_0000) {
                break;
            }
        }
        for (; index < limit; index++) {
            if (alpha(argb.get(index)) != 0xFF) {
                break;
            }
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

        return premultiplyNonOpaque(argb, alpha);
    }

    /// Premultiplies an array in place and reports whether all input pixels were opaque.
    ///
    /// Fully opaque pixels are not written. The empty array is considered fully opaque.
    ///
    /// @param argb the non-premultiplied pixels to convert
    /// @return `true` if every input pixel was fully opaque; otherwise `false`
    public static boolean premultiply(int[] argb) {
        return premultiply(argb, 0, argb.length);
    }

    /// Premultiplies a buffer's remaining pixels in place and reports whether every input pixel
    /// was opaque.
    ///
    /// The buffer's position and limit are not changed. Heap-backed writable buffers use their
    /// backing array directly.
    ///
    /// @param argb the writable non-premultiplied pixels to convert
    /// @return `true` if every input pixel was fully opaque; otherwise `false`
    /// @throws java.nio.ReadOnlyBufferException if a non-opaque pixel must be converted and the
    ///                                           buffer is read-only
    public static boolean premultiply(IntBuffer argb) {
        int position = argb.position();
        int limit = argb.limit();
        if (argb.hasArray()) {
            int arrayOffset = argb.arrayOffset();
            return premultiply(argb.array(), arrayOffset + position, arrayOffset + limit);
        }

        int opaquePrefix = countOpaquePrefix(argb);
        for (int index = position + opaquePrefix; index < limit; index++) {
            int pixel = argb.get(index);
            int alpha = alpha(pixel);
            if (alpha != 0xFF) {
                argb.put(index, premultiplyNonOpaque(pixel, alpha));
            }
        }
        return opaquePrefix == limit - position;
    }

    /// Copies non-premultiplied pixels into a premultiplied destination.
    ///
    /// The destination's remaining region must contain exactly one element per source pixel. Its
    /// position and limit are not changed. Source and destination storage must not overlap.
    ///
    /// @param source the non-premultiplied source pixels
    /// @param target the writable destination region
    /// @return `true` if every source pixel was fully opaque; otherwise `false`
    /// @throws IllegalArgumentException if the destination size differs from the source length or
    ///                                  its storage overlaps the source array
    /// @throws ReadOnlyBufferException if the destination is read-only
    public static boolean copyPremultiplied(int[] source, IntBuffer target) {
        if (target.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        if (target.remaining() != source.length) {
            throw new IllegalArgumentException(
                    "Target size does not match source length: "
                            + target.remaining() + " != " + source.length
            );
        }

        // The JVM's bulk array-to-direct-buffer copy followed by a sequential
        // conversion is faster than interleaved scalar direct-buffer writes.
        if (target.isDirect()) {
            target.put(target.position(), source, 0, source.length);
            return premultiply(target);
        }

        if (target.hasArray()) {
            if (target.array() == source) {
                throw new IllegalArgumentException("Source and target storage overlap");
            }
            return copyPremultiplied(
                    source,
                    target.array(),
                    target.arrayOffset() + target.position()
            );
        }

        int targetPosition = target.position();
        int opaquePrefix = countOpaquePrefix(source);
        if (opaquePrefix > 0) {
            target.put(targetPosition, source, 0, opaquePrefix);
        }
        for (int index = opaquePrefix; index < source.length; index++) {
            int pixel = source[index];
            int alpha = alpha(pixel);
            target.put(
                    targetPosition + index,
                    alpha == 0xFF ? pixel : premultiplyNonOpaque(pixel, alpha)
            );
        }
        return opaquePrefix == source.length;
    }

    /// Copies non-premultiplied pixels into a distinct premultiplied array.
    ///
    /// The arrays must have equal lengths and must not be the same array.
    ///
    /// @param source the non-premultiplied source pixels
    /// @param target the destination pixels
    /// @return `true` if every source pixel was fully opaque; otherwise `false`
    /// @throws IllegalArgumentException if the arrays differ in length or are the same array
    public static boolean copyPremultiplied(int[] source, int[] target) {
        if (source == target) {
            throw new IllegalArgumentException("Source and target arrays must be distinct");
        }
        if (source.length != target.length) {
            throw new IllegalArgumentException(
                    "Target length does not match source length: "
                            + target.length + " != " + source.length
            );
        }
        return copyPremultiplied(source, target, 0);
    }

    /// Copies non-premultiplied pixels into an array region while premultiplying them.
    ///
    /// @param source the non-premultiplied source pixels
    /// @param target the destination array
    /// @param targetOffset the first destination index
    /// @return `true` if every source pixel was fully opaque; otherwise `false`
    private static boolean copyPremultiplied(int[] source, int[] target, int targetOffset) {
        int opaquePrefix = countOpaquePrefix(source);
        if (opaquePrefix > 0) {
            System.arraycopy(source, 0, target, targetOffset, opaquePrefix);
        }
        for (int index = opaquePrefix; index < source.length; index++) {
            int pixel = source[index];
            int alpha = alpha(pixel);
            target[targetOffset + index] = alpha == 0xFF
                    ? pixel
                    : premultiplyNonOpaque(pixel, alpha);
        }
        return opaquePrefix == source.length;
    }

    /// Premultiplies an array range in place and reports whether every input pixel was opaque.
    ///
    /// @param argb the non-premultiplied pixels to convert
    /// @param fromIndex the inclusive range start
    /// @param toIndex the exclusive range end
    /// @return `true` if every input pixel was fully opaque; otherwise `false`
    /// @throws IndexOutOfBoundsException if the range is outside the array
    private static boolean premultiply(int[] argb, int fromIndex, int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, argb.length);
        int opaquePrefix = countOpaquePrefix(argb, fromIndex, toIndex);
        for (int index = opaquePrefix; index < toIndex; index++) {
            int pixel = argb[index];
            int alpha = alpha(pixel);
            if (alpha != 0xFF) {
                argb[index] = premultiplyNonOpaque(pixel, alpha);
            }
        }
        return opaquePrefix == toIndex;
    }

    /// Premultiplies a pixel whose alpha channel is known not to be opaque.
    ///
    /// @param argb the non-premultiplied pixel
    /// @param alpha the pixel's alpha channel in the range 0 through 254
    /// @return the premultiplied pixel
    private static int premultiplyNonOpaque(int argb, int alpha) {
        if (alpha == 0) {
            return 0;
        }

        // The 16-bit lanes keep the red and blue products independent. Adding each rounded
        // product's high byte implements exact division by 255 without integer division.
        long redBlue = (argb & 0x00FF_00FFL) * alpha + 0x0080_0080L;
        redBlue += (redBlue >>> 8) & 0x00FF_00FFL;
        redBlue = (redBlue >>> 8) & 0x00FF_00FFL;

        int green = green(argb) * alpha + 0x80;
        green = (green + (green >>> 8)) >>> 8;
        return (alpha << 24) | (int) redBlue | (green << 8);
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
