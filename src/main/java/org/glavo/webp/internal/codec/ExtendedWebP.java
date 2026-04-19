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
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.Argb;
import org.glavo.webp.internal.lossless.LosslessDecoder;

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

    /// Parsed ALPH chunk payload.
    ///
    /// @param filteringMethod the alpha predictor mode
    /// @param data decoded alpha bytes, one per pixel
    @NotNullByDefault
    public record AlphaChunk(FilteringMethod filteringMethod, byte[] data) {
    }

    private ExtendedWebP() {
    }

    /// Parses an ALPH chunk payload and returns the decoded alpha data.
    ///
    /// The alpha chunk may either store raw bytes or a nested lossless WebP image. The current
    /// backend supports the compressed representation expected in real WebP files and delegates the
    /// nested lossless decoding to the VP8L decoder.
    ///
    /// @param payload the ALPH chunk payload
    /// @param width the target width
    /// @param height the target height
    /// @return the parsed alpha chunk
    /// @throws WebPException if the alpha payload is malformed
    public static AlphaChunk parseAlphaChunk(byte[] payload, int width, int height) throws WebPException {
        if (payload.length < 1) {
            throw new WebPException("ALPH chunk is too small");
        }

        int infoByte = payload[0] & 0xFF;
        int preprocessing = (infoByte >>> 4) & 0b11;
        int filtering = (infoByte >>> 2) & 0b11;
        int compression = infoByte & 0b11;

        if (preprocessing > 1) {
            throw new WebPException("Invalid ALPH preprocessing value: " + preprocessing);
        }

        FilteringMethod filteringMethod = switch (filtering) {
            case 0 -> FilteringMethod.NONE;
            case 1 -> FilteringMethod.HORIZONTAL;
            case 2 -> FilteringMethod.VERTICAL;
            case 3 -> FilteringMethod.GRADIENT;
            default -> throw new WebPException("Invalid ALPH filtering value: " + filtering);
        };

        byte[] decodedAlpha;
        switch (compression) {
            case 0 -> {
                int expectedLength = width * height;
                if (payload.length - 1 != expectedLength) {
                    throw new WebPException("ALPH chunk size does not match the frame dimensions");
                }
                decodedAlpha = new byte[expectedLength];
                System.arraycopy(payload, 1, decodedAlpha, 0, expectedLength);
            }
            case 1 -> {
                int[] argb = new int[width * height];
                byte[] nestedVp8L = new byte[payload.length - 1];
                System.arraycopy(payload, 1, nestedVp8L, 0, nestedVp8L.length);

                /*
                 * Compressed ALPH chunks embed a VP8L image whose green channel stores one alpha
                 * byte per pixel. The dimensions come from the surrounding VP8/ANMF frame rather
                 * than from a nested VP8L header, so we decode in implicit-dimension mode.
                 */
                new LosslessDecoder(nestedVp8L).decodeFrame(width, height, true, argb);

                decodedAlpha = new byte[width * height];
                for (int pixel = 0; pixel < decodedAlpha.length; pixel++) {
                    decodedAlpha[pixel] = (byte) Argb.green(argb[pixel]);
                }
            }
            default -> throw new WebPException("Unsupported ALPH compression method: " + compression);
        }

        return new AlphaChunk(filteringMethod, decodedAlpha);
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
