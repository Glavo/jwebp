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
package org.glavo.webp;

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Objects;

/// An immutable decoded presentation frame.
///
/// Pixels are tightly packed as `0xAARRGGBB` integers in the representation reported by
/// [#getPixelFormat()]. For animated images, each frame contains the fully composited canvas for
/// that presentation step. Static images contain one frame whose duration is zero.
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

    /// Resolved storage location used by [#pixels].
    private final WebPFrameStorage frameStorage;

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
                WebPFrameStorage.HEAP,
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
    /// @param frameStorage the resolved storage location; [WebPFrameStorage#AUTO] is not permitted
    /// @param copyArgb whether heap output must copy the source array before conversion
    /// @throws IllegalArgumentException if dimensions, duration, source length, or storage are invalid
    WebPFrame(
            int width,
            int height,
            int durationMillis,
            int[] argbPixels,
            WebPPixelFormat pixelFormat,
            WebPFrameStorage frameStorage,
            boolean copyArgb
    ) {
        Objects.requireNonNull(argbPixels, "argbPixels");
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        Objects.requireNonNull(frameStorage, "frameStorage");
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
        if (frameStorage == WebPFrameStorage.AUTO) {
            throw new IllegalArgumentException("Frame storage must be resolved before construction");
        }

        this.width = width;
        this.height = height;
        this.scanlineStride = width;
        this.durationMillis = durationMillis;
        this.pixelFormat = pixelFormat;
        this.frameStorage = frameStorage;
        this.pixels = createPixels(argbPixels, pixelFormat, frameStorage, copyArgb);
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

    /// Returns the resolved storage location used by the pixel buffer.
    ///
    /// The result is always [WebPFrameStorage#HEAP] or [WebPFrameStorage#DIRECT], even when the
    /// decoder was configured with [WebPFrameStorage#AUTO].
    ///
    /// @return the resolved frame storage
    public WebPFrameStorage getFrameStorage() {
        return frameStorage;
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
        return pixelFormat.isPremultiplied() ? Argb.unpremultiply(pixel) : pixel;
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
    /// For [WebPPixelFormat#INT_ARGB] this is a view of the frame storage. For
    /// [WebPPixelFormat#INT_ARGB_PRE] this method allocates and converts a snapshot. Use
    /// [#getPixels()] when the stored representation is acceptable and allocation must be avoided.
    ///
    /// @return a position-zero, read-only non-premultiplied `ARGB` buffer
    public @UnmodifiableView IntBuffer getArgbPixels() {
        if (pixelFormat == WebPPixelFormat.INT_ARGB) {
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
        if (pixelFormat.isPremultiplied()) {
            for (int index = 0; index < argb.length; index++) {
                argb[index] = Argb.unpremultiply(argb[index]);
            }
        }
        return argb;
    }

    /// Creates immutable pixel storage from non-premultiplied decoder output.
    ///
    /// @param argbPixels the source non-premultiplied pixels
    /// @param pixelFormat the destination representation
    /// @param frameStorage the resolved destination storage
    /// @param copyArgb whether heap output must copy its source before conversion
    /// @return a read-only, position-zero buffer
    private static @UnmodifiableView IntBuffer createPixels(
            int[] argbPixels,
            WebPPixelFormat pixelFormat,
            WebPFrameStorage frameStorage,
            boolean copyArgb
    ) {
        if (frameStorage == WebPFrameStorage.HEAP) {
            int[] output = copyArgb ? argbPixels.clone() : argbPixels;
            if (pixelFormat == WebPPixelFormat.INT_ARGB_PRE) {
                for (int index = 0; index < argbPixels.length; index++) {
                    output[index] = Argb.premultiply(output[index]);
                }
            }
            return IntBuffer.wrap(output).asReadOnlyBuffer();
        }

        int byteCount;
        try {
            byteCount = Math.multiplyExact(argbPixels.length, Integer.BYTES);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Pixel buffer is too large for direct storage", ex);
        }
        IntBuffer output = ByteBuffer.allocateDirect(byteCount)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        if (pixelFormat == WebPPixelFormat.INT_ARGB) {
            output.put(argbPixels);
        } else {
            for (int argbPixel : argbPixels) {
                output.put(Argb.premultiply(argbPixel));
            }
        }
        output.flip();
        return output.asReadOnlyBuffer();
    }
}
