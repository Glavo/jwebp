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

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import org.jetbrains.annotations.NotNullByDefault;

import javafx.scene.image.WritableImage;

import java.nio.IntBuffer;

/// A decoded frame represented as tightly packed non-premultiplied `ARGB` pixels.
///
/// For static images the library returns a single frame with a duration of `0`. For
/// animated images each frame already represents the fully composited canvas for the corresponding
/// presentation step, which makes the object suitable for direct JavaFX display.
@NotNullByDefault
public final class WebPFrame {

    private final int width;
    private final int height;
    private final int scanlineStride;
    private final int durationMillis;
    private final int[] argbPixels;

    /// Creates a frame from decoded `ARGB` pixels.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds, or `0` for still images
    /// @param argbPixels tightly packed non-premultiplied `ARGB` pixels stored as `0xAARRGGBB`;
    ///                   ownership of the array is transferred to this frame
    WebPFrame(int width, int height, int durationMillis, int[] argbPixels) {
        this.width = width;
        this.height = height;
        this.scanlineStride = width;
        this.durationMillis = durationMillis;
        this.argbPixels = argbPixels;
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

    /// Returns the number of packed pixels between two adjacent rows.
    ///
    /// The library always stores pixels as tightly packed `ARGB` integers, so the scanline stride
    /// is always equal to `width`.
    ///
    /// @return the scanline stride in `int` pixels
    public int getScanlineStride() {
        return scanlineStride;
    }

    /// Returns the frame duration in milliseconds.
    ///
    /// @return the presentation duration, or `0` for still images
    public int getDurationMillis() {
        return durationMillis;
    }

    /// Returns a read-only view of the underlying `ARGB` pixel buffer.
    ///
    /// Each invocation returns a fresh view whose position is set to `0`. The returned
    /// buffer is safe to share, but mutating it is not allowed.
    ///
    /// @return a read-only `ARGB` pixel buffer
    public IntBuffer getArgbPixels() {
        return IntBuffer.wrap(argbPixels).asReadOnlyBuffer();
    }

    /// Returns a defensive copy of the packed `ARGB` pixels.
    ///
    /// @return a newly allocated `ARGB` array
    public int[] getArgbArray() {
        return argbPixels.clone();
    }

    /// Creates a JavaFX [WritableImage] from this frame.
    ///
    /// The packed `ARGB` integers can be written directly through JavaFX's
    /// `PixelFormat.getIntArgbInstance()` format without any channel swizzle or premultiplication.
    ///
    /// @return a newly allocated JavaFX image
    public WritableImage toWritableImage() {
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        writer.setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argbPixels, 0, width);
        return image;
    }
}
