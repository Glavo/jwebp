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
package org.glavo.webp.javafx;

import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPPixelFormat;
import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/// Prepares premultiplied pixel storage for JavaFX presentation.
@NotNullByDefault
final class WebPFXImageScaler {

    /// Prevents instantiation.
    private WebPFXImageScaler() {
    }

    /// Prepares static `INT_ARGB_PRE` pixels, sharing compatible intrinsic-size storage.
    ///
    /// @param frame the source frame
    /// @param scalePlan the target dimensions and filtering mode
    /// @return position-zero, read-only presentation pixels
    static @UnmodifiableView IntBuffer prepareStaticPixels(WebPFrame frame, ScalePlan scalePlan) {
        @UnmodifiableView IntBuffer source = frame.getPixels();
        if (!scalePlan.scalingRequired() && frame.getPixelFormat() == WebPPixelFormat.INT_ARGB_PRE) {
            return source;
        }

        if (scalePlan.scalingRequired()) {
            return scaleAsArgbPre(frame, scalePlan);
        }

        IntBuffer target = allocateBuffer(frame.getWidth(), frame.getHeight(), source.isDirect());
        copyAsArgbPre(source, frame.getPixelFormat(), target);
        return target.asReadOnlyBuffer();
    }

    /// Scales one frame into newly allocated `INT_ARGB_PRE` storage.
    ///
    /// @param frame the source frame
    /// @param scalePlan the target dimensions and filtering mode
    /// @return position-zero, read-only scaled pixels
    static @UnmodifiableView IntBuffer scaleAsArgbPre(WebPFrame frame, ScalePlan scalePlan) {
        @UnmodifiableView IntBuffer source = frame.getPixels();
        IntBuffer target = allocateBuffer(
                scalePlan.targetWidth(),
                scalePlan.targetHeight(),
                source.isDirect()
        );
        if (scalePlan.smooth()) {
            scaleBilinear(source, frame.getPixelFormat(), scalePlan, target);
        } else {
            scaleNearest(source, frame.getPixelFormat(), scalePlan, target);
        }
        target.rewind();
        return target.asReadOnlyBuffer();
    }

    /// Allocates writable JavaFX presentation storage in the requested memory location.
    ///
    /// @param width the buffer width in pixels
    /// @param height the buffer height in pixels
    /// @param direct whether to allocate direct storage
    /// @return a position-zero writable buffer
    /// @throws IllegalArgumentException if the dimensions exceed the supported buffer size
    static IntBuffer allocateBuffer(int width, int height, boolean direct) {
        int pixelCount;
        try {
            pixelCount = Math.multiplyExact(width, height);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Image dimensions are too large: " + width + "x" + height, ex);
        }

        if (direct) {
            int byteCount;
            try {
                byteCount = Math.multiplyExact(pixelCount, Integer.BYTES);
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException(
                        "Image dimensions are too large for direct storage: " + width + "x" + height,
                        ex
                );
            }
            return ByteBuffer.allocateDirect(byteCount)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
        }
        return IntBuffer.allocate(pixelCount);
    }

    /// Copies pixels into writable `INT_ARGB_PRE` storage.
    ///
    /// The destination position is reset to zero before this method returns. The source buffer's
    /// position and limit are not changed.
    ///
    /// @param source the source pixels
    /// @param sourceFormat the source pixel representation
    /// @param target the destination with capacity for the entire frame
    static void copyAsArgbPre(
            @UnmodifiableView IntBuffer source,
            WebPPixelFormat sourceFormat,
            IntBuffer target
    ) {
        @UnmodifiableView IntBuffer sourceView = source.asReadOnlyBuffer();
        sourceView.rewind();
        target.clear();
        if (sourceFormat == WebPPixelFormat.INT_ARGB_PRE) {
            target.put(sourceView);
        } else {
            while (sourceView.hasRemaining()) {
                target.put(Argb.premultiply(sourceView.get()));
            }
        }
        target.rewind();
    }

    /// Scales pixels with nearest-neighbor sampling into `INT_ARGB_PRE` storage.
    ///
    /// @param source the position-independent source pixel view
    /// @param sourceFormat the source pixel representation
    /// @param scalePlan the source and target dimensions
    /// @param target the destination buffer
    private static void scaleNearest(
            @UnmodifiableView IntBuffer source,
            WebPPixelFormat sourceFormat,
            ScalePlan scalePlan,
            IntBuffer target
    ) {
        int sourceWidth = scalePlan.sourceWidth();
        int sourceHeight = scalePlan.sourceHeight();
        int targetWidth = scalePlan.targetWidth();
        int targetHeight = scalePlan.targetHeight();

        target.clear();
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = Math.min(
                    sourceHeight - 1,
                    (int) (((2L * y + 1L) * sourceHeight) / (2L * targetHeight))
            );
            int sourceRow = sourceY * sourceWidth;
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(
                        sourceWidth - 1,
                        (int) (((2L * x + 1L) * sourceWidth) / (2L * targetWidth))
                );
                target.put(getArgbPre(source, sourceFormat, sourceRow + sourceX));
            }
        }
    }

    /// Scales pixels with bilinear interpolation in premultiplied color space.
    ///
    /// @param source the position-independent source pixel view
    /// @param sourceFormat the source pixel representation
    /// @param scalePlan the source and target dimensions
    /// @param target the destination buffer
    private static void scaleBilinear(
            @UnmodifiableView IntBuffer source,
            WebPPixelFormat sourceFormat,
            ScalePlan scalePlan,
            IntBuffer target
    ) {
        int sourceWidth = scalePlan.sourceWidth();
        int sourceHeight = scalePlan.sourceHeight();
        int targetWidth = scalePlan.targetWidth();
        int targetHeight = scalePlan.targetHeight();
        double xScale = targetWidth > 1 ? (double) (sourceWidth - 1) / (targetWidth - 1) : 0.0;
        double yScale = targetHeight > 1 ? (double) (sourceHeight - 1) / (targetHeight - 1) : 0.0;

        target.clear();
        for (int y = 0; y < targetHeight; y++) {
            double sourceY = y * yScale;
            int y0 = Math.min(sourceHeight - 1, (int) sourceY);
            int y1 = Math.min(sourceHeight - 1, y0 + 1);
            double yFraction = sourceY - y0;
            int row0 = y0 * sourceWidth;
            int row1 = y1 * sourceWidth;

            for (int x = 0; x < targetWidth; x++) {
                double sourceX = x * xScale;
                int x0 = Math.min(sourceWidth - 1, (int) sourceX);
                int x1 = Math.min(sourceWidth - 1, x0 + 1);
                double xFraction = sourceX - x0;

                int topLeft = getArgbPre(source, sourceFormat, row0 + x0);
                int topRight = getArgbPre(source, sourceFormat, row0 + x1);
                int bottomLeft = getArgbPre(source, sourceFormat, row1 + x0);
                int bottomRight = getArgbPre(source, sourceFormat, row1 + x1);

                int alpha = interpolateChannel(
                        topLeft >>> 24,
                        topRight >>> 24,
                        bottomLeft >>> 24,
                        bottomRight >>> 24,
                        xFraction,
                        yFraction
                );
                int red = Math.min(alpha, interpolateChannel(
                        (topLeft >>> 16) & 0xFF,
                        (topRight >>> 16) & 0xFF,
                        (bottomLeft >>> 16) & 0xFF,
                        (bottomRight >>> 16) & 0xFF,
                        xFraction,
                        yFraction
                ));
                int green = Math.min(alpha, interpolateChannel(
                        (topLeft >>> 8) & 0xFF,
                        (topRight >>> 8) & 0xFF,
                        (bottomLeft >>> 8) & 0xFF,
                        (bottomRight >>> 8) & 0xFF,
                        xFraction,
                        yFraction
                ));
                int blue = Math.min(alpha, interpolateChannel(
                        topLeft & 0xFF,
                        topRight & 0xFF,
                        bottomLeft & 0xFF,
                        bottomRight & 0xFF,
                        xFraction,
                        yFraction
                ));
                target.put(Argb.pack(alpha, red, green, blue));
            }
        }
    }

    /// Interpolates one color channel across four neighboring pixels.
    ///
    /// @param topLeft the top-left channel value
    /// @param topRight the top-right channel value
    /// @param bottomLeft the bottom-left channel value
    /// @param bottomRight the bottom-right channel value
    /// @param xFraction the horizontal interpolation fraction
    /// @param yFraction the vertical interpolation fraction
    /// @return the interpolated 8-bit channel value
    private static int interpolateChannel(
            int topLeft,
            int topRight,
            int bottomLeft,
            int bottomRight,
            double xFraction,
            double yFraction
    ) {
        double top = topLeft + (topRight - topLeft) * xFraction;
        double bottom = bottomLeft + (bottomRight - bottomLeft) * xFraction;
        return (int) Math.round(top + (bottom - top) * yFraction);
    }

    /// Returns one source pixel in premultiplied representation without changing buffer state.
    ///
    /// @param source the source pixel view
    /// @param sourceFormat the source pixel representation
    /// @param index the absolute pixel index
    /// @return the `INT_ARGB_PRE` pixel
    private static int getArgbPre(
            @UnmodifiableView IntBuffer source,
            WebPPixelFormat sourceFormat,
            int index
    ) {
        int pixel = source.get(index);
        return sourceFormat == WebPPixelFormat.INT_ARGB_PRE ? pixel : Argb.premultiply(pixel);
    }

    /// Source dimensions, target dimensions, and filtering mode for one adapter conversion.
    ///
    /// @param sourceWidth the intrinsic width in pixels
    /// @param sourceHeight the intrinsic height in pixels
    /// @param targetWidth the presentation width in pixels
    /// @param targetHeight the presentation height in pixels
    /// @param smooth whether to use bilinear filtering
    @NotNullByDefault
    record ScalePlan(
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight,
            boolean smooth
    ) {

        /// Computes target dimensions using JavaFX-style image scaling semantics.
        ///
        /// @param sourceWidth the intrinsic width in pixels
        /// @param sourceHeight the intrinsic height in pixels
        /// @param options the JavaFX presentation options
        /// @return the resulting scale plan
        static ScalePlan create(
                int sourceWidth,
                int sourceHeight,
                WebPFXImageOptions options
        ) {
            double requestedWidth = options.getRequestedWidth();
            double requestedHeight = options.getRequestedHeight();

            int targetWidth = sourceWidth;
            int targetHeight = sourceHeight;
            if (options.isPreserveRatio()) {
                if (requestedWidth > 0.0 && requestedHeight > 0.0) {
                    double scale = Math.min(
                            requestedWidth / sourceWidth,
                            requestedHeight / sourceHeight
                    );
                    targetWidth = clampDimension(sourceWidth * scale, "requestedWidth");
                    targetHeight = clampDimension(sourceHeight * scale, "requestedHeight");
                } else if (requestedWidth > 0.0) {
                    double scale = requestedWidth / sourceWidth;
                    targetWidth = clampDimension(requestedWidth, "requestedWidth");
                    targetHeight = clampDimension(sourceHeight * scale, "requestedWidth");
                } else if (requestedHeight > 0.0) {
                    double scale = requestedHeight / sourceHeight;
                    targetWidth = clampDimension(sourceWidth * scale, "requestedHeight");
                    targetHeight = clampDimension(requestedHeight, "requestedHeight");
                }
            } else {
                if (requestedWidth > 0.0) {
                    targetWidth = clampDimension(requestedWidth, "requestedWidth");
                }
                if (requestedHeight > 0.0) {
                    targetHeight = clampDimension(requestedHeight, "requestedHeight");
                }
            }

            return new ScalePlan(
                    sourceWidth,
                    sourceHeight,
                    targetWidth,
                    targetHeight,
                    options.isSmooth()
            );
        }

        /// Rounds and validates one positive target dimension.
        ///
        /// @param value the computed floating-point dimension
        /// @param name the originating parameter name used in exceptions
        /// @return the rounded dimension, clamped to at least one pixel
        /// @throws IllegalArgumentException if `value` exceeds the integer range
        private static int clampDimension(double value, String name) {
            long rounded = Math.round(value);
            if (rounded > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(name + " is too large: " + value);
            }
            return Math.max(1, (int) rounded);
        }

        /// Returns whether pixel scaling is required.
        ///
        /// @return `true` if either target dimension differs from its intrinsic dimension
        boolean scalingRequired() {
            return sourceWidth != targetWidth || sourceHeight != targetHeight;
        }
    }
}
