// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import java.nio.IntBuffer;
import java.util.Objects;

/// An immutable decoded presentation frame.
///
/// Pixels are tightly packed as `0xAARRGGBB` integers in the representation reported by
/// [#getPixelFormat()]. For animated images, each frame contains the fully composited canvas for
/// that presentation step. Static images contain one frame whose duration is zero.
///
/// A frame created with caller-provided pixel storage may have aliases outside this object. The
/// caller must not modify that storage while the frame remains in use.
@NotNullByDefault
public final class WebPFrame {

    /// Frame width in pixels.
    private final int width;

    /// Frame height in pixels.
    private final int height;

    /// Number of packed integer pixels between adjacent rows.
    private final int scanlineStride;

    /// Frame presentation duration in milliseconds.
    private final int durationMillis;

    /// Pixel representation used by [#pixels].
    private final WebPPixelFormat pixelFormat;

    /// Whether the pixel storage was supplied by the reader's caller.
    private final boolean customPixelBuffer;

    /// Whether every stored premultiplied pixel is known to be fully opaque.
    private final boolean opaque;

    /// Read-only, position-zero view of the frame's tightly packed pixels.
    private final @UnmodifiableView IntBuffer pixels;

    /// Creates a frame from fully prepared state.
    ///
    /// The supplied values must already satisfy all frame invariants. In particular, `pixels` must
    /// be a position-zero, read-only view containing exactly `width * height` pixels.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds, or `0` for a still image
    /// @param pixelFormat the stored pixel representation
    /// @param customPixelBuffer whether the storage was supplied by the reader's caller
    /// @param opaque whether every stored premultiplied pixel is known to be fully opaque
    /// @param pixels the prepared read-only pixel view
    WebPFrame(
            int width,
            int height,
            int durationMillis,
            WebPPixelFormat pixelFormat,
            boolean customPixelBuffer,
            boolean opaque,
            @UnmodifiableView IntBuffer pixels
    ) {
        this.width = width;
        this.height = height;
        this.scanlineStride = width;
        this.durationMillis = durationMillis;
        this.pixelFormat = pixelFormat;
        this.customPixelBuffer = customPixelBuffer;
        this.opaque = opaque;
        this.pixels = pixels;
    }

    /// Returns the frame width.
    ///
    /// @return the frame width in pixels
    public int getWidth() {
        return width;
    }

    /// Returns the frame height.
    ///
    /// @return the frame height in pixels
    public int getHeight() {
        return height;
    }

    /// Returns the number of packed integer pixels between adjacent rows.
    ///
    /// Frames are tightly packed, so this value is always equal to [#getWidth()].
    ///
    /// @return the scanline stride in `int` pixels
    public int getScanlineStride() {
        return scanlineStride;
    }

    /// Returns the frame duration in milliseconds.
    ///
    /// @return the presentation duration, or `0` for a still image
    public int getDurationMillis() {
        return durationMillis;
    }

    /// Returns the representation used by the stored pixels.
    ///
    /// @return the pixel format
    public WebPPixelFormat getPixelFormat() {
        return pixelFormat;
    }

    /// Returns whether this frame retains caller-provided pixel storage.
    ///
    /// A custom pixel buffer may have writable aliases outside this frame. A `true` result does
    /// not permit modifying the pixel region while this frame remains in use.
    ///
    /// @return `true` if the pixel storage was supplied to
    ///         [WebPImageReader#readNextFrame(WebPPixelFormat, IntBuffer)]
    public boolean usesCustomPixelBuffer() {
        return customPixelBuffer;
    }

    /// Returns an equivalent frame whose pixel storage is owned by its enclosing decoded image.
    ///
    /// No pixel data is copied. This frame and the returned frame retain the same read-only pixel
    /// region.
    ///
    /// @return this frame when its storage is already owned, otherwise an owned-storage view
    WebPFrame asOwned() {
        if (!customPixelBuffer) {
            return this;
        }
        return new WebPFrame(
                width,
                height,
                durationMillis,
                pixelFormat,
                false,
                opaque,
                pixels
        );
    }

    /// Returns the non-premultiplied `ARGB` pixel at the supplied coordinates.
    ///
    /// Premultiplied storage is converted on demand. That conversion cannot recover color data
    /// discarded during premultiplication.
    ///
    /// @param x the horizontal coordinate in pixels
    /// @param y the vertical coordinate in pixels
    /// @return the packed non-premultiplied `ARGB` value
    /// @throws IndexOutOfBoundsException if either coordinate is outside the frame
    public int getArgb(int x, int y) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        int pixel = pixels.get(y * scanlineStride + x);
        return pixelFormat.isPremultiplied() && !opaque ? Argb.unpremultiply(pixel) : pixel;
    }

    /// Returns a read-only view of the pixels in [#getPixelFormat()] representation.
    ///
    /// Each invocation returns a new view with position zero and limit `width * height`. The view
    /// remains valid for the lifetime of this frame. Its [IntBuffer#isDirect()] result indicates
    /// whether the frame uses direct storage.
    ///
    /// @return a read-only view of the stored pixels
    public @UnmodifiableView IntBuffer getPixels() {
        return pixels.asReadOnlyBuffer();
    }

    /// Returns non-premultiplied `ARGB` pixels in a read-only buffer.
    ///
    /// Each invocation returns a buffer with position zero and limit `width * height`. Callers must
    /// not rely on whether the returned buffer shares storage with this frame or another returned
    /// buffer. Use [#getPixels()] when the stored representation is acceptable.
    ///
    /// @return a position-zero, read-only non-premultiplied `ARGB` buffer
    public @UnmodifiableView IntBuffer getArgbPixels() {
        if (pixelFormat == WebPPixelFormat.INT_ARGB || opaque) {
            return getPixels();
        }
        return IntBuffer.wrap(getArgbArray()).asReadOnlyBuffer();
    }

    /// Returns a defensive copy of the pixels as non-premultiplied `ARGB` values.
    ///
    /// Premultiplied storage is converted while copying. That conversion cannot recover color data
    /// discarded during premultiplication.
    ///
    /// @return a newly allocated non-premultiplied `ARGB` array
    public int[] getArgbArray() {
        int[] argb = new int[pixels.capacity()];
        IntBuffer source = pixels.asReadOnlyBuffer();
        source.get(argb);
        if (pixelFormat.isPremultiplied() && !opaque) {
            for (int index = 0; index < argb.length; index++) {
                argb[index] = Argb.unpremultiply(argb[index]);
            }
        }
        return argb;
    }

}
