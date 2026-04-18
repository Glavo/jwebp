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
