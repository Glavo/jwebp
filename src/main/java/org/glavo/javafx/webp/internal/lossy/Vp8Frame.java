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
package org.glavo.javafx.webp.internal.lossy;

/// Decoded VP8 frame planes.
///
/// The VP8 bitstream stores one full-resolution luma plane plus half-resolution chroma planes.
/// This object keeps those planes until the caller requests RGB or RGBA pixels.
final class Vp8Frame {

    int width;
    int height;
    byte[] yBuffer = new byte[0];
    byte[] uBuffer = new byte[0];
    byte[] vBuffer = new byte[0];
    byte version;
    boolean keyframe;
    boolean forDisplay;
    byte pixelType;
    boolean filterType;
    byte filterLevel;
    byte sharpnessLevel;

    int chromaWidth() {
        return (width + 1) / 2;
    }

    int bufferWidth() {
        int remainder = width % 16;
        return remainder > 0 ? width + (16 - remainder) : width;
    }

    void fillRgb(byte[] buffer, boolean fancyUpsampling) {
        if (fancyUpsampling) {
            LossyYuv.fillRgbBufferFancy(buffer, yBuffer, uBuffer, vBuffer, width, height, bufferWidth(), 3);
        } else {
            LossyYuv.fillRgbBufferSimple(buffer, yBuffer, uBuffer, vBuffer, width, chromaWidth(), bufferWidth(), 3);
        }
    }

    void fillRgba(byte[] buffer, boolean fancyUpsampling) {
        if (fancyUpsampling) {
            LossyYuv.fillRgbBufferFancy(buffer, yBuffer, uBuffer, vBuffer, width, height, bufferWidth(), 4);
        } else {
            LossyYuv.fillRgbBufferSimple(buffer, yBuffer, uBuffer, vBuffer, width, chromaWidth(), bufferWidth(), 4);
        }
    }

    int getRgbBufferSize() {
        return yBuffer.length * 3;
    }
}
