package org.glavo.javafx.webp.internal;

/// Pure byte-array RGBA scaling routines used by the decoder backend.
///
/// The scaler operates on tightly packed non-premultiplied RGBA data and therefore avoids all
/// desktop imaging APIs. The `smooth` path uses bilinear interpolation while the fast path
/// uses nearest-neighbor sampling.
public final class PixelScaler {

    private PixelScaler() {
    }

    /// Scales an RGBA image according to the supplied plan.
    ///
    /// @param source the source RGBA pixels
    /// @param sourceWidth the source width
    /// @param sourceHeight the source height
    /// @param scalePlan the scaling configuration
    /// @return the scaled RGBA pixels, or a copy of the source if scaling is not required
    public static byte[] scaleRgba(byte[] source, int sourceWidth, int sourceHeight, ScalePlan scalePlan) {
        if (sourceWidth == scalePlan.targetWidth() && sourceHeight == scalePlan.targetHeight()) {
            return source.clone();
        }

        return scalePlan.smooth()
                ? scaleBilinear(source, sourceWidth, sourceHeight, scalePlan.targetWidth(), scalePlan.targetHeight())
                : scaleNearest(source, sourceWidth, sourceHeight, scalePlan.targetWidth(), scalePlan.targetHeight());
    }

    private static byte[] scaleNearest(byte[] source, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        byte[] scaled = new byte[targetWidth * targetHeight * 4];
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = Math.min(sourceHeight - 1, (int) (((long) y * sourceHeight) / targetHeight));
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(sourceWidth - 1, (int) (((long) x * sourceWidth) / targetWidth));
                int src = (sourceY * sourceWidth + sourceX) * 4;
                int dst = (y * targetWidth + x) * 4;
                scaled[dst] = source[src];
                scaled[dst + 1] = source[src + 1];
                scaled[dst + 2] = source[src + 2];
                scaled[dst + 3] = source[src + 3];
            }
        }
        return scaled;
    }

    private static byte[] scaleBilinear(byte[] source, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        byte[] scaled = new byte[targetWidth * targetHeight * 4];
        float xScale = targetWidth > 1 ? (float) (sourceWidth - 1) / (targetWidth - 1) : 0f;
        float yScale = targetHeight > 1 ? (float) (sourceHeight - 1) / (targetHeight - 1) : 0f;

        for (int y = 0; y < targetHeight; y++) {
            float sourceY = y * yScale;
            int y0 = Math.min(sourceHeight - 1, (int) sourceY);
            int y1 = Math.min(sourceHeight - 1, y0 + 1);
            float fy = sourceY - y0;

            for (int x = 0; x < targetWidth; x++) {
                float sourceX = x * xScale;
                int x0 = Math.min(sourceWidth - 1, (int) sourceX);
                int x1 = Math.min(sourceWidth - 1, x0 + 1);
                float fx = sourceX - x0;

                int dst = (y * targetWidth + x) * 4;
                int c00 = (y0 * sourceWidth + x0) * 4;
                int c10 = (y0 * sourceWidth + x1) * 4;
                int c01 = (y1 * sourceWidth + x0) * 4;
                int c11 = (y1 * sourceWidth + x1) * 4;

                for (int channel = 0; channel < 4; channel++) {
                    float top = lerp(source[c00 + channel] & 0xFF, source[c10 + channel] & 0xFF, fx);
                    float bottom = lerp(source[c01 + channel] & 0xFF, source[c11 + channel] & 0xFF, fx);
                    scaled[dst + channel] = (byte) Math.round(lerp(top, bottom, fy));
                }
            }
        }
        return scaled;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
