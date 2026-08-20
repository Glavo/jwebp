// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
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

    /// Creates a heap-backed non-premultiplied frame and takes ownership of its pixel array.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds, or `0` for a still image
    /// @param argbPixels tightly packed non-premultiplied `ARGB` pixels
    WebPFrame(int width, int height, int durationMillis, int[] argbPixels) {
        this(
                width,
                height,
                durationMillis,
                argbPixels,
                WebPPixelFormat.INT_ARGB,
                false
        );
    }

    /// Creates a frame from non-premultiplied decoder output.
    ///
    /// If `copyArgb` is `false`, construction takes ownership of `argbPixels` and may convert its
    /// contents in place. If it is `true`, the source array remains unmodified.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds, or `0` for a still image
    /// @param argbPixels tightly packed non-premultiplied `ARGB` source pixels
    /// @param pixelFormat the requested stored pixel representation
    /// @param copyArgb whether heap output must copy the source array before conversion
    /// @throws IllegalArgumentException if dimensions, duration, or source length are invalid
    WebPFrame(
            int width,
            int height,
            int durationMillis,
            int[] argbPixels,
            WebPPixelFormat pixelFormat,
            boolean copyArgb
    ) {
        Objects.requireNonNull(argbPixels, "argbPixels");
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive: " + width + "x" + height);
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis < 0: " + durationMillis);
        }
        int pixelCount;
        try {
            pixelCount = Math.multiplyExact(width, height);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Frame dimensions are too large: " + width + "x" + height, ex);
        }
        if (argbPixels.length != pixelCount) {
            throw new IllegalArgumentException(
                    "Pixel buffer length does not match frame dimensions: "
                            + argbPixels.length + " != " + pixelCount
            );
        }
        this.width = width;
        this.height = height;
        this.scanlineStride = width;
        this.durationMillis = durationMillis;
        this.pixelFormat = pixelFormat;
        this.customPixelBuffer = false;
        int[] output = copyArgb ? argbPixels.clone() : argbPixels;
        boolean opaque = true;
        if (pixelFormat == WebPPixelFormat.INT_ARGB_PRE) {
            for (int index = 0; index < output.length; index++) {
                int pixel = output[index];
                opaque &= (pixel >>> 24) == 0xFF;
                output[index] = Argb.premultiply(pixel);
            }
        }
        this.opaque = pixelFormat == WebPPixelFormat.INT_ARGB_PRE && opaque;
        this.pixels = IntBuffer.wrap(output).asReadOnlyBuffer();
    }

    /// Creates a frame by converting and retaining caller-provided pixel storage.
    ///
    /// The remaining buffer region must contain exactly one tightly packed non-premultiplied
    /// `ARGB` pixel per frame pixel. The pixels are converted in place when `pixelFormat` requires
    /// premultiplication. The caller must not modify the retained region while the frame remains in
    /// use.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds, or `0` for a still image
    /// @param pixelFormat the stored pixel representation
    /// @param pixels writable, tightly packed non-premultiplied `ARGB` storage to retain
    /// @param opaque whether every source pixel is known to be fully opaque
    /// @throws IllegalArgumentException if dimensions, duration, or buffer size are invalid
    /// @throws ReadOnlyBufferException if `pixels` is read-only
    WebPFrame(
            int width,
            int height,
            int durationMillis,
            WebPPixelFormat pixelFormat,
            IntBuffer pixels,
            boolean opaque
    ) {
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive: " + width + "x" + height);
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis < 0: " + durationMillis);
        }
        int pixelCount;
        try {
            pixelCount = Math.multiplyExact(width, height);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Frame dimensions are too large: " + width + "x" + height, ex);
        }
        if (pixels.remaining() != pixelCount) {
            throw new IllegalArgumentException(
                    "Pixel buffer size does not match frame dimensions: "
                            + pixels.remaining() + " != " + pixelCount
            );
        }
        if (pixels.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        boolean allOpaque = opaque;
        if (pixelFormat == WebPPixelFormat.INT_ARGB_PRE) {
            if (!opaque) {
                allOpaque = true;
                for (int index = pixels.position(); index < pixels.limit(); index++) {
                    int pixel = pixels.get(index);
                    allOpaque &= (pixel >>> 24) == 0xFF;
                    pixels.put(index, Argb.premultiply(pixel));
                }
            }
        }
        this.width = width;
        this.height = height;
        this.scanlineStride = width;
        this.durationMillis = durationMillis;
        this.pixelFormat = pixelFormat;
        this.customPixelBuffer = true;
        this.opaque = pixelFormat == WebPPixelFormat.INT_ARGB_PRE && allOpaque;
        this.pixels = pixels.slice().asReadOnlyBuffer();
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
