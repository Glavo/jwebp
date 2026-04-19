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
package org.glavo.webp.internal;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;

/// Pure packed-`ARGB` scaling routines used by the decoder backend.
///
/// The scaler operates on tightly packed non-premultiplied `ARGB` data and therefore avoids all
/// desktop imaging APIs. The `smooth` path uses bilinear interpolation while the fast path uses
/// nearest-neighbor sampling.
@NotNullByDefault
public final class PixelScaler {

    private PixelScaler() {
    }

    /// Scales an `ARGB` image according to the supplied plan.
    ///
    /// @param source the source `ARGB` pixels
    /// @param sourceWidth the source width
    /// @param sourceHeight the source height
    /// @param scalePlan the scaling configuration
    /// @return the scaled `ARGB` pixels, or a copy of the source if scaling is not required
    @Contract("_, _, _, _ -> new")
    public static int[] scaleArgb(int[] source, int sourceWidth, int sourceHeight, ScalePlan scalePlan) {
        if (sourceWidth == scalePlan.targetWidth() && sourceHeight == scalePlan.targetHeight()) {
            return source.clone();
        }

        return scalePlan.smooth()
                ? scaleBilinear(source, sourceWidth, sourceHeight, scalePlan.targetWidth(), scalePlan.targetHeight())
                : scaleNearest(source, sourceWidth, sourceHeight, scalePlan.targetWidth(), scalePlan.targetHeight());
    }

    private static int[] scaleNearest(int[] source, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        int[] scaled = new int[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = Math.min(sourceHeight - 1, (int) (((long) y * sourceHeight) / targetHeight));
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(sourceWidth - 1, (int) (((long) x * sourceWidth) / targetWidth));
                scaled[y * targetWidth + x] = source[sourceY * sourceWidth + sourceX];
            }
        }
        return scaled;
    }

    private static int[] scaleBilinear(int[] source, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        int[] scaled = new int[targetWidth * targetHeight];
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

                int c00 = source[y0 * sourceWidth + x0];
                int c10 = source[y0 * sourceWidth + x1];
                int c01 = source[y1 * sourceWidth + x0];
                int c11 = source[y1 * sourceWidth + x1];

                int alpha = interpolateChannel(c00 >>> 24, c10 >>> 24, c01 >>> 24, c11 >>> 24, fx, fy);
                int red = interpolateChannel((c00 >>> 16) & 0xFF, (c10 >>> 16) & 0xFF, (c01 >>> 16) & 0xFF, (c11 >>> 16) & 0xFF, fx, fy);
                int green = interpolateChannel((c00 >>> 8) & 0xFF, (c10 >>> 8) & 0xFF, (c01 >>> 8) & 0xFF, (c11 >>> 8) & 0xFF, fx, fy);
                int blue = interpolateChannel(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, fx, fy);

                scaled[y * targetWidth + x] = Argb.pack(alpha, red, green, blue);
            }
        }
        return scaled;
    }

    private static int interpolateChannel(int c00, int c10, int c01, int c11, float fx, float fy) {
        float top = lerp(c00, c10, fx);
        float bottom = lerp(c01, c11, fx);
        return Math.round(lerp(top, bottom, fy));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
