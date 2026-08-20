// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.Argb;
import org.glavo.webp.internal.lossless.LosslessDecoder;

import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

/// Utilities for extended WebP features such as alpha reconstruction and animation composition.
@NotNullByDefault
public final class ExtendedWebP {

    /// Filtering methods defined by the WebP ALPH chunk format.
    @NotNullByDefault
    public enum FilteringMethod {
        NONE,
        HORIZONTAL,
        VERTICAL,
        GRADIENT
    }

    /// Decodes an ALPH payload into the high byte of an integer pixel array.
    ///
    /// Lower 24 bits are discarded. Compressed samples use the destination itself as the nested
    /// VP8L workspace, allowing subsequent VP8 color conversion to preserve the reconstructed alpha
    /// channel without allocating a second full-size pixel array.
    ///
    /// @param payload the ALPH chunk payload
    /// @param width the frame width
    /// @param height the frame height
    /// @param argb the exact-sized writable destination
    /// @throws IllegalArgumentException if the dimensions are invalid or the destination size does
    ///                                  not match them
    /// @throws WebPException if the alpha payload is malformed
    public static void decodeAlpha(byte[] payload, int width, int height, int[] argb) throws WebPException {
        decodeAlpha(payload, width, height, argb, null);
    }

    /// Decodes an ALPH payload while reusing a supplied VP8L decoder for compressed samples.
    ///
    /// The decoder is reset to the compressed portion of `payload` when VP8L compression is used.
    /// It is not accessed for an uncompressed payload. Lower 24 destination bits are discarded.
    ///
    /// @param payload the ALPH chunk payload
    /// @param width the frame width
    /// @param height the frame height
    /// @param argb the exact-sized writable destination
    /// @param losslessDecoder the decoder whose workspaces may be reused, or `null` to allocate one
    /// @throws IllegalArgumentException if the dimensions are invalid or the destination size does
    ///                                  not match them
    /// @throws WebPException if the alpha payload is malformed
    public static void decodeAlpha(
            byte[] payload,
            int width,
            int height,
            int[] argb,
            @Nullable LosslessDecoder losslessDecoder
    ) throws WebPException {
        int expectedLength = pixelCount(width, height);
        if (argb.length != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer length does not match ALPH dimensions: "
                            + argb.length + " != " + expectedLength
            );
        }

        int parameters = parseAlphaParameters(payload, expectedLength);
        int compression = parameters & 0b11;
        FilteringMethod filteringMethod = filteringMethod((parameters >>> 2) & 0b11);
        if (compression == 1) {
            LosslessDecoder decoder = prepareAlphaDecoder(payload, losslessDecoder);
            decoder.decodeFrame(width, height, true, argb);
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                int sample = compression == 0
                        ? payload[pixelIndex + 1] & 0xFF
                        : Argb.green(argb[pixelIndex]);
                int predictor = getAlphaPredictor(x, y, width, filteringMethod, argb);
                argb[pixelIndex] = ((sample + predictor) & 0xFF) << 24;
            }
        }
    }

    /// Decodes an ALPH payload into the high byte of an integer buffer.
    ///
    /// Lower 24 bits are discarded. The destination itself is used for compressed VP8L samples.
    /// Its position and limit are not changed.
    ///
    /// @param payload the ALPH chunk payload
    /// @param width the frame width
    /// @param height the frame height
    /// @param argb the exact-sized writable destination region
    /// @throws IllegalArgumentException if the dimensions are invalid or the destination size does
    ///                                  not match them
    /// @throws ReadOnlyBufferException if the destination is read-only
    /// @throws WebPException if the alpha payload is malformed
    public static void decodeAlpha(byte[] payload, int width, int height, IntBuffer argb) throws WebPException {
        decodeAlpha(payload, width, height, argb, null);
    }

    /// Decodes an ALPH payload into an integer buffer while reusing a supplied VP8L decoder.
    ///
    /// The decoder is reset to the compressed portion of `payload` when VP8L compression is used.
    /// It is not accessed for an uncompressed payload. The destination position and limit are not
    /// changed.
    ///
    /// @param payload the ALPH chunk payload
    /// @param width the frame width
    /// @param height the frame height
    /// @param argb the exact-sized writable destination region
    /// @param losslessDecoder the decoder whose workspaces may be reused, or `null` to allocate one
    /// @throws IllegalArgumentException if the dimensions are invalid or the destination size does
    ///                                  not match them
    /// @throws ReadOnlyBufferException if the destination is read-only
    /// @throws WebPException if the alpha payload is malformed
    public static void decodeAlpha(
            byte[] payload,
            int width,
            int height,
            IntBuffer argb,
            @Nullable LosslessDecoder losslessDecoder
    ) throws WebPException {
        if (argb.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        int expectedLength = pixelCount(width, height);
        if (argb.remaining() != expectedLength) {
            throw new IllegalArgumentException(
                    "ARGB buffer size does not match ALPH dimensions: "
                            + argb.remaining() + " != " + expectedLength
            );
        }

        int parameters = parseAlphaParameters(payload, expectedLength);
        int compression = parameters & 0b11;
        FilteringMethod filteringMethod = filteringMethod((parameters >>> 2) & 0b11);
        IntBuffer output = argb.position() == 0 && argb.limit() == argb.capacity()
                ? argb
                : argb.slice();
        if (compression == 1) {
            LosslessDecoder decoder = prepareAlphaDecoder(payload, losslessDecoder);
            decoder.decodeFrame(width, height, true, output);
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                int sample = compression == 0
                        ? payload[pixelIndex + 1] & 0xFF
                        : Argb.green(output.get(pixelIndex));
                int predictor = getAlphaPredictor(x, y, width, filteringMethod, output);
                output.put(pixelIndex, ((sample + predictor) & 0xFF) << 24);
            }
        }
    }

    /// Returns a decoder reset to the compressed ALPH payload bytes.
    ///
    /// @param payload the ALPH payload including its control byte
    /// @param reusableDecoder the decoder to reuse, or `null` to allocate one
    /// @return the prepared decoder
    private static LosslessDecoder prepareAlphaDecoder(
            byte[] payload,
            @Nullable LosslessDecoder reusableDecoder
    ) {
        if (reusableDecoder == null) {
            return new LosslessDecoder(payload, 1, payload.length - 1);
        }
        reusableDecoder.resetInput(payload, 1, payload.length - 1);
        return reusableDecoder;
    }

    /// Returns the validated number of pixels for ALPH dimensions.
    ///
    /// @param width the frame width
    /// @param height the frame height
    /// @return `width * height`
    /// @throws IllegalArgumentException if either dimension is non-positive or their product
    ///                                  exceeds the integer range
    private static int pixelCount(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("ALPH dimensions must be positive: " + width + "x" + height);
        }
        try {
            return Math.multiplyExact(width, height);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("ALPH dimensions are too large: " + width + "x" + height, ex);
        }
    }

    /// Parses and validates the ALPH control byte and raw payload size.
    ///
    /// @param payload the ALPH payload
    /// @param expectedLength the required number of alpha samples
    /// @return the validated control byte
    /// @throws WebPException if the payload is malformed or uses unsupported compression
    private static int parseAlphaParameters(byte[] payload, int expectedLength) throws WebPException {
        if (payload.length < 1) {
            throw new WebPException("ALPH chunk is too small");
        }

        int infoByte = payload[0] & 0xFF;
        int preprocessing = (infoByte >>> 4) & 0b11;
        int compression = infoByte & 0b11;
        if (preprocessing > 1) {
            throw new WebPException("Invalid ALPH preprocessing value: " + preprocessing);
        }
        if (compression == 0 && payload.length - 1 != expectedLength) {
            throw new WebPException("ALPH chunk size does not match the frame dimensions");
        }
        if (compression > 1) {
            throw new WebPException("Unsupported ALPH compression method: " + compression);
        }
        return infoByte;
    }

    /// Resolves an ALPH filtering code.
    ///
    /// @param filtering the two-bit filtering code
    /// @return the corresponding filtering method
    private static FilteringMethod filteringMethod(int filtering) {
        return switch (filtering) {
            case 0 -> FilteringMethod.NONE;
            case 1 -> FilteringMethod.HORIZONTAL;
            case 2 -> FilteringMethod.VERTICAL;
            case 3 -> FilteringMethod.GRADIENT;
            default -> throw new AssertionError("Invalid ALPH filtering value: " + filtering);
        };
    }

    private ExtendedWebP() {
    }

    /// Returns the alpha predictor value for the given pixel.
    ///
    /// @param x the x coordinate
    /// @param y the y coordinate
    /// @param width the frame width
    /// @param filteringMethod the ALPH predictor mode
    /// @param argbBuffer the partially reconstructed `ARGB` frame
    /// @return the predictor byte
    public static int getAlphaPredictor(int x, int y, int width, FilteringMethod filteringMethod, int[] argbBuffer) {
        return switch (filteringMethod) {
            case NONE -> 0;
            case HORIZONTAL -> {
                if (x == 0 && y == 0) {
                    yield 0;
                }
                if (x == 0) {
                    yield Argb.alpha(argbBuffer[((y - 1) * width) + x]);
                }
                yield Argb.alpha(argbBuffer[(y * width) + x - 1]);
            }
            case VERTICAL -> {
                if (x == 0 && y == 0) {
                    yield 0;
                }
                if (y == 0) {
                    yield Argb.alpha(argbBuffer[(y * width) + x - 1]);
                }
                yield Argb.alpha(argbBuffer[((y - 1) * width) + x]);
            }
            case GRADIENT -> {
                int left;
                int top;
                int topLeft;
                if (x == 0 && y == 0) {
                    left = top = topLeft = 0;
                } else if (x == 0) {
                    int value = Argb.alpha(argbBuffer[((y - 1) * width) + x]);
                    left = top = topLeft = value;
                } else if (y == 0) {
                    int value = Argb.alpha(argbBuffer[(y * width) + x - 1]);
                    left = top = topLeft = value;
                } else {
                    left = Argb.alpha(argbBuffer[(y * width) + x - 1]);
                    top = Argb.alpha(argbBuffer[((y - 1) * width) + x]);
                    topLeft = Argb.alpha(argbBuffer[((y - 1) * width) + x - 1]);
                }
                yield Math.max(0, Math.min(255, left + top - topLeft));
            }
        };
    }

    /// Returns the alpha predictor value from a partially reconstructed integer buffer.
    ///
    /// @param x the x coordinate
    /// @param y the y coordinate
    /// @param width the frame width
    /// @param filteringMethod the ALPH predictor mode
    /// @param argbBuffer the position-zero partially reconstructed frame
    /// @return the predictor byte
    private static int getAlphaPredictor(
            int x,
            int y,
            int width,
            FilteringMethod filteringMethod,
            IntBuffer argbBuffer
    ) {
        return switch (filteringMethod) {
            case NONE -> 0;
            case HORIZONTAL -> {
                if (x == 0 && y == 0) {
                    yield 0;
                }
                if (x == 0) {
                    yield Argb.alpha(argbBuffer.get((y - 1) * width));
                }
                yield Argb.alpha(argbBuffer.get(y * width + x - 1));
            }
            case VERTICAL -> {
                if (x == 0 && y == 0) {
                    yield 0;
                }
                if (y == 0) {
                    yield Argb.alpha(argbBuffer.get(x - 1));
                }
                yield Argb.alpha(argbBuffer.get((y - 1) * width + x));
            }
            case GRADIENT -> {
                int left;
                int top;
                int topLeft;
                if (x == 0 && y == 0) {
                    left = top = topLeft = 0;
                } else if (x == 0) {
                    int value = Argb.alpha(argbBuffer.get((y - 1) * width));
                    left = top = topLeft = value;
                } else if (y == 0) {
                    int value = Argb.alpha(argbBuffer.get(x - 1));
                    left = top = topLeft = value;
                } else {
                    left = Argb.alpha(argbBuffer.get(y * width + x - 1));
                    top = Argb.alpha(argbBuffer.get((y - 1) * width + x));
                    topLeft = Argb.alpha(argbBuffer.get((y - 1) * width + x - 1));
                }
                yield Math.max(0, Math.min(255, left + top - topLeft));
            }
        };
    }

    /// Composites one decoded frame over an `ARGB` canvas.
    ///
    /// @param canvas the canvas `ARGB` pixels, always sized to the full image
    /// @param canvasWidth the canvas width
    /// @param canvasHeight the canvas height
    /// @param clearColor the optional color used to clear the previous frame region
    /// @param frame the frame pixels as packed `ARGB`
    /// @param frameX the frame x offset
    /// @param frameY the frame y offset
    /// @param frameWidth the frame width
    /// @param frameHeight the frame height
    /// @param useAlphaBlending whether alpha pixels should blend over the canvas
    /// @param previousFrameWidth the previous frame width
    /// @param previousFrameHeight the previous frame height
    /// @param previousFrameX the previous frame x offset
    /// @param previousFrameY the previous frame y offset
    public static void compositeFrame(
            int[] canvas,
            int canvasWidth,
            int canvasHeight,
            @Nullable Integer clearColor,
            int[] frame,
            int frameX,
            int frameY,
            int frameWidth,
            int frameHeight,
            boolean useAlphaBlending,
            int previousFrameWidth,
            int previousFrameHeight,
            int previousFrameX,
            int previousFrameY
    ) {
        boolean frameIsFullSize = frameX == 0 && frameY == 0 && frameWidth == canvasWidth && frameHeight == canvasHeight;

        if (frameIsFullSize && !useAlphaBlending) {
            System.arraycopy(frame, 0, canvas, 0, canvas.length);
            return;
        }

        if (clearColor != null) {
            if (frameIsFullSize) {
                Arrays.fill(canvas, clearColor);
            } else {
                for (int y = 0; y < previousFrameHeight; y++) {
                    for (int x = 0; x < previousFrameWidth; x++) {
                        int canvasIndex = (x + previousFrameX) + ((y + previousFrameY) * canvasWidth);
                        canvas[canvasIndex] = clearColor;
                    }
                }
            }
        }

        int width = Math.min(frameWidth, canvasWidth - frameX);
        int height = Math.min(frameHeight, canvasHeight - frameY);

        if (useAlphaBlending) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int frameIndex = x + y * frameWidth;
                    int canvasIndex = (x + frameX) + ((y + frameY) * canvasWidth);
                    canvas[canvasIndex] = AlphaBlending.blend(frame[frameIndex], canvas[canvasIndex]);
                }
            }
        } else {
            for (int y = 0; y < height; y++) {
                int frameIndex = y * frameWidth;
                int canvasIndex = frameX + ((y + frameY) * canvasWidth);
                System.arraycopy(frame, frameIndex, canvas, canvasIndex, width);
            }
        }
    }
}
