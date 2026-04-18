package org.glavo.javafx.webp.internal.codec;

/// Integer alpha blending helpers based on the WebP animation reference implementation.
public final class AlphaBlending {

    private AlphaBlending() {
    }

    /// Blends a single non-premultiplied RGBA source pixel over a destination pixel.
    ///
    /// @param src the source RGBA pixel
    /// @param dst the destination RGBA pixel
    /// @return the blended non-premultiplied RGBA pixel
    public static byte[] blend(byte[] src, byte[] dst) {
        int srcA = src[3] & 0xFF;
        if (srcA == 0) {
            return dst.clone();
        }

        int dstA = dst[3] & 0xFF;
        int dstFactorA = divBy255(dstA * (255 - srcA));
        int blendA = srcA + dstFactorA;
        int scale = (1 << 24) / blendA;

        int r = blendChannel(src[0] & 0xFF, srcA, dst[0] & 0xFF, dstFactorA, scale);
        int g = blendChannel(src[1] & 0xFF, srcA, dst[1] & 0xFF, dstFactorA, scale);
        int b = blendChannel(src[2] & 0xFF, srcA, dst[2] & 0xFF, dstFactorA, scale);
        return new byte[]{(byte) r, (byte) g, (byte) b, (byte) blendA};
    }

    private static int blendChannel(int src, int srcA, int dst, int dstA, int scale) {
        int blended = src * srcA + dst * dstA;
        return (blended * scale) >> 24;
    }

    private static int divBy255(int value) {
        return (((value + 0x80) >> 8) + value + 0x80) >> 8;
    }
}
