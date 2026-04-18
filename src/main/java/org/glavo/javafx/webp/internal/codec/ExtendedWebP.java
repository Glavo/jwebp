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
package org.glavo.javafx.webp.internal.codec;

import org.glavo.javafx.webp.WebPException;
import org.glavo.javafx.webp.internal.lossless.LosslessDecoder;

/// Utilities for extended WebP features such as alpha reconstruction and animation composition.
public final class ExtendedWebP {

    /// Filtering methods defined by the WebP ALPH chunk format.
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
                byte[] rgba = new byte[width * height * 4];
                byte[] nestedVp8L = new byte[payload.length - 1];
                System.arraycopy(payload, 1, nestedVp8L, 0, nestedVp8L.length);

                /*
                 * Compressed ALPH chunks embed a VP8L image whose green channel stores one alpha
                 * byte per pixel. The dimensions come from the surrounding VP8/ANMF frame rather
                 * than from a nested VP8L header, so we decode in implicit-dimension mode.
                 */
                new LosslessDecoder(nestedVp8L).decodeFrame(width, height, true, rgba);

                decodedAlpha = new byte[width * height];
                for (int pixel = 0; pixel < decodedAlpha.length; pixel++) {
                    decodedAlpha[pixel] = rgba[pixel * 4 + 1];
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
    /// @param rgbaBuffer the partially reconstructed RGBA frame
    /// @return the predictor byte
    public static int getAlphaPredictor(int x, int y, int width, FilteringMethod filteringMethod, byte[] rgbaBuffer) {
        return switch (filteringMethod) {
            case NONE -> 0;
            case HORIZONTAL -> {
                if (x == 0 && y == 0) {
                    yield 0;
                }
                if (x == 0) {
                    yield rgbaBuffer[(((y - 1) * width) + x) * 4 + 3] & 0xFF;
                }
                yield rgbaBuffer[((y * width) + x - 1) * 4 + 3] & 0xFF;
            }
            case VERTICAL -> {
                if (x == 0 && y == 0) {
                    yield 0;
                }
                if (y == 0) {
                    yield rgbaBuffer[((y * width) + x - 1) * 4 + 3] & 0xFF;
                }
                yield rgbaBuffer[(((y - 1) * width) + x) * 4 + 3] & 0xFF;
            }
            case GRADIENT -> {
                int left;
                int top;
                int topLeft;
                if (x == 0 && y == 0) {
                    left = top = topLeft = 0;
                } else if (x == 0) {
                    int value = rgbaBuffer[(((y - 1) * width) + x) * 4 + 3] & 0xFF;
                    left = top = topLeft = value;
                } else if (y == 0) {
                    int value = rgbaBuffer[((y * width) + x - 1) * 4 + 3] & 0xFF;
                    left = top = topLeft = value;
                } else {
                    left = rgbaBuffer[((y * width) + x - 1) * 4 + 3] & 0xFF;
                    top = rgbaBuffer[(((y - 1) * width) + x) * 4 + 3] & 0xFF;
                    topLeft = rgbaBuffer[((((y - 1) * width) + x - 1) * 4) + 3] & 0xFF;
                }
                yield Math.max(0, Math.min(255, left + top - topLeft));
            }
        };
    }

    /// Composites one decoded frame over an RGBA canvas.
    ///
    /// @param canvas the canvas RGBA pixels, always sized to the full image
    /// @param canvasWidth the canvas width
    /// @param canvasHeight the canvas height
    /// @param clearColor the optional color used to clear the previous frame region
    /// @param frame the frame pixels, encoded as RGB or RGBA depending on `frameHasAlpha`
    /// @param frameX the frame x offset
    /// @param frameY the frame y offset
    /// @param frameWidth the frame width
    /// @param frameHeight the frame height
    /// @param frameHasAlpha whether the frame pixels are RGBA
    /// @param useAlphaBlending whether alpha pixels should blend over the canvas
    /// @param previousFrameWidth the previous frame width
    /// @param previousFrameHeight the previous frame height
    /// @param previousFrameX the previous frame x offset
    /// @param previousFrameY the previous frame y offset
    public static void compositeFrame(
            byte[] canvas,
            int canvasWidth,
            int canvasHeight,
            byte[] clearColor,
            byte[] frame,
            int frameX,
            int frameY,
            int frameWidth,
            int frameHeight,
            boolean frameHasAlpha,
            boolean useAlphaBlending,
            int previousFrameWidth,
            int previousFrameHeight,
            int previousFrameX,
            int previousFrameY
    ) {
        boolean frameIsFullSize = frameX == 0 && frameY == 0 && frameWidth == canvasWidth && frameHeight == canvasHeight;

        if (frameIsFullSize && !useAlphaBlending) {
            if (frameHasAlpha) {
                System.arraycopy(frame, 0, canvas, 0, canvas.length);
            } else {
                for (int src = 0, dst = 0; src < frame.length; src += 3, dst += 4) {
                    canvas[dst] = frame[src];
                    canvas[dst + 1] = frame[src + 1];
                    canvas[dst + 2] = frame[src + 2];
                    canvas[dst + 3] = (byte) 0xFF;
                }
            }
            return;
        }

        if (clearColor != null) {
            if (frameIsFullSize) {
                for (int index = 0; index < canvas.length; index += 4) {
                    System.arraycopy(clearColor, 0, canvas, index, 4);
                }
            } else {
                for (int y = 0; y < previousFrameHeight; y++) {
                    for (int x = 0; x < previousFrameWidth; x++) {
                        int canvasIndex = (((x + previousFrameX) + ((y + previousFrameY) * canvasWidth)) * 4);
                        System.arraycopy(clearColor, 0, canvas, canvasIndex, 4);
                    }
                }
            }
        }

        int width = Math.min(frameWidth, canvasWidth - frameX);
        int height = Math.min(frameHeight, canvasHeight - frameY);

        if (frameHasAlpha && useAlphaBlending) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int frameIndex = ((x + y * frameWidth) * 4);
                    int canvasIndex = (((x + frameX) + ((y + frameY) * canvasWidth)) * 4);
                    byte[] blended = AlphaBlending.blend(
                            new byte[]{frame[frameIndex], frame[frameIndex + 1], frame[frameIndex + 2], frame[frameIndex + 3]},
                            new byte[]{canvas[canvasIndex], canvas[canvasIndex + 1], canvas[canvasIndex + 2], canvas[canvasIndex + 3]}
                    );
                    System.arraycopy(blended, 0, canvas, canvasIndex, 4);
                }
            }
        } else if (frameHasAlpha) {
            for (int y = 0; y < height; y++) {
                int frameIndex = (y * frameWidth) * 4;
                int canvasIndex = (frameX + ((y + frameY) * canvasWidth)) * 4;
                System.arraycopy(frame, frameIndex, canvas, canvasIndex, width * 4);
            }
        } else {
            for (int y = 0; y < height; y++) {
                int frameIndex = (y * frameWidth) * 3;
                int canvasIndex = (frameX + ((y + frameY) * canvasWidth)) * 4;
                for (int x = 0; x < width; x++) {
                    int src = frameIndex + x * 3;
                    int dst = canvasIndex + x * 4;
                    canvas[dst] = frame[src];
                    canvas[dst + 1] = frame[src + 1];
                    canvas[dst + 2] = frame[src + 2];
                    canvas[dst + 3] = (byte) 0xFF;
                }
            }
        }
    }
}
