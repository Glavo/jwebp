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
package org.glavo.webp.internal.lossy;

import org.jetbrains.annotations.NotNullByDefault;

/// VP8 loop filter implementation.
@NotNullByDefault
final class LossyLoopFilter {

    private LossyLoopFilter() {
    }

    static void simpleSegmentVertical(int edgeLimit, byte[] pixels, int point, int stride) {
        if (simpleThresholdVertical(edgeLimit, pixels, point, stride)) {
            commonAdjustVertical(true, pixels, point, stride);
        }
    }

    static void simpleSegmentHorizontal(int edgeLimit, byte[] pixels) {
        if (simpleThresholdHorizontal(edgeLimit, pixels)) {
            commonAdjustHorizontal(true, pixels);
        }
    }

    static void subblockFilterVertical(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels, int point, int stride) {
        if (shouldFilterVertical(interiorLimit, edgeLimit, pixels, point, stride)) {
            boolean hev = highEdgeVarianceVertical(hevThreshold, pixels, point, stride);
            int adjustment = (commonAdjustVertical(hev, pixels, point, stride) + 1) >> 1;
            if (!hev) {
                pixels[point + stride] = signedToUnsigned(unsignedToSigned(pixels[point + stride]) - adjustment);
                pixels[point - 2 * stride] = signedToUnsigned(unsignedToSigned(pixels[point - 2 * stride]) + adjustment);
            }
        }
    }

    static void subblockFilterHorizontal(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels) {
        if (shouldFilterHorizontal(interiorLimit, edgeLimit, pixels)) {
            boolean hev = highEdgeVarianceHorizontal(hevThreshold, pixels);
            int adjustment = (commonAdjustHorizontal(hev, pixels) + 1) >> 1;
            if (!hev) {
                pixels[5] = signedToUnsigned(unsignedToSigned(pixels[5]) - adjustment);
                pixels[2] = signedToUnsigned(unsignedToSigned(pixels[2]) + adjustment);
            }
        }
    }

    static void macroblockFilterVertical(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels, int point, int stride) {
        if (shouldFilterVertical(interiorLimit, edgeLimit, pixels, point, stride)) {
            if (!highEdgeVarianceVertical(hevThreshold, pixels, point, stride)) {
                int p2 = unsignedToSigned(pixels[point - 3 * stride]);
                int p1 = unsignedToSigned(pixels[point - 2 * stride]);
                int p0 = unsignedToSigned(pixels[point - stride]);
                int q0 = unsignedToSigned(pixels[point]);
                int q1 = unsignedToSigned(pixels[point + stride]);
                int q2 = unsignedToSigned(pixels[point + 2 * stride]);

                int w = clampSigned(clampSigned(p1 - q1) + 3 * (q0 - p0));

                int adjustment = clampSigned((27 * w + 63) >> 7);
                pixels[point] = signedToUnsigned(q0 - adjustment);
                pixels[point - stride] = signedToUnsigned(p0 + adjustment);

                adjustment = clampSigned((18 * w + 63) >> 7);
                pixels[point + stride] = signedToUnsigned(q1 - adjustment);
                pixels[point - 2 * stride] = signedToUnsigned(p1 + adjustment);

                adjustment = clampSigned((9 * w + 63) >> 7);
                pixels[point + 2 * stride] = signedToUnsigned(q2 - adjustment);
                pixels[point - 3 * stride] = signedToUnsigned(p2 + adjustment);
            } else {
                commonAdjustVertical(true, pixels, point, stride);
            }
        }
    }

    static void macroblockFilterHorizontal(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels) {
        if (shouldFilterHorizontal(interiorLimit, edgeLimit, pixels)) {
            if (!highEdgeVarianceHorizontal(hevThreshold, pixels)) {
                int p2 = unsignedToSigned(pixels[1]);
                int p1 = unsignedToSigned(pixels[2]);
                int p0 = unsignedToSigned(pixels[3]);
                int q0 = unsignedToSigned(pixels[4]);
                int q1 = unsignedToSigned(pixels[5]);
                int q2 = unsignedToSigned(pixels[6]);

                int w = clampSigned(clampSigned(p1 - q1) + 3 * (q0 - p0));

                int adjustment = clampSigned((27 * w + 63) >> 7);
                pixels[4] = signedToUnsigned(q0 - adjustment);
                pixels[3] = signedToUnsigned(p0 + adjustment);

                adjustment = clampSigned((18 * w + 63) >> 7);
                pixels[5] = signedToUnsigned(q1 - adjustment);
                pixels[2] = signedToUnsigned(p1 + adjustment);

                adjustment = clampSigned((9 * w + 63) >> 7);
                pixels[6] = signedToUnsigned(q2 - adjustment);
                pixels[1] = signedToUnsigned(p2 + adjustment);
            } else {
                commonAdjustHorizontal(true, pixels);
            }
        }
    }

    private static int commonAdjustVertical(boolean useOuterTaps, byte[] pixels, int point, int stride) {
        int p1 = unsignedToSigned(pixels[point - 2 * stride]);
        int p0 = unsignedToSigned(pixels[point - stride]);
        int q0 = unsignedToSigned(pixels[point]);
        int q1 = unsignedToSigned(pixels[point + stride]);

        int outer = useOuterTaps ? clampSigned(p1 - q1) : 0;
        int a = clampSigned(outer + 3 * (q0 - p0));
        int b = clampSigned(a + 3) >> 3;
        a = clampSigned(a + 4) >> 3;

        pixels[point] = signedToUnsigned(q0 - a);
        pixels[point - stride] = signedToUnsigned(p0 + b);
        return a;
    }

    private static int commonAdjustHorizontal(boolean useOuterTaps, byte[] pixels) {
        int p1 = unsignedToSigned(pixels[2]);
        int p0 = unsignedToSigned(pixels[3]);
        int q0 = unsignedToSigned(pixels[4]);
        int q1 = unsignedToSigned(pixels[5]);

        int outer = useOuterTaps ? clampSigned(p1 - q1) : 0;
        int a = clampSigned(outer + 3 * (q0 - p0));
        int b = clampSigned(a + 3) >> 3;
        a = clampSigned(a + 4) >> 3;

        pixels[4] = signedToUnsigned(q0 - a);
        pixels[3] = signedToUnsigned(p0 + b);
        return a;
    }

    private static boolean simpleThresholdVertical(int filterLimit, byte[] pixels, int point, int stride) {
        return diff(pixels[point - stride], pixels[point]) * 2
                + diff(pixels[point - 2 * stride], pixels[point + stride]) / 2
                <= filterLimit;
    }

    private static boolean simpleThresholdHorizontal(int filterLimit, byte[] pixels) {
        return diff(pixels[3], pixels[4]) * 2 + diff(pixels[2], pixels[5]) / 2 <= filterLimit;
    }

    private static boolean shouldFilterVertical(int interiorLimit, int edgeLimit, byte[] pixels, int point, int stride) {
        return simpleThresholdVertical(edgeLimit, pixels, point, stride)
                && diff(pixels[point - 4 * stride], pixels[point - 3 * stride]) <= interiorLimit
                && diff(pixels[point - 3 * stride], pixels[point - 2 * stride]) <= interiorLimit
                && diff(pixels[point - 2 * stride], pixels[point - stride]) <= interiorLimit
                && diff(pixels[point + 3 * stride], pixels[point + 2 * stride]) <= interiorLimit
                && diff(pixels[point + 2 * stride], pixels[point + stride]) <= interiorLimit
                && diff(pixels[point + stride], pixels[point]) <= interiorLimit;
    }

    private static boolean shouldFilterHorizontal(int interiorLimit, int edgeLimit, byte[] pixels) {
        return simpleThresholdHorizontal(edgeLimit, pixels)
                && diff(pixels[0], pixels[1]) <= interiorLimit
                && diff(pixels[1], pixels[2]) <= interiorLimit
                && diff(pixels[2], pixels[3]) <= interiorLimit
                && diff(pixels[7], pixels[6]) <= interiorLimit
                && diff(pixels[6], pixels[5]) <= interiorLimit
                && diff(pixels[5], pixels[4]) <= interiorLimit;
    }

    private static boolean highEdgeVarianceVertical(int threshold, byte[] pixels, int point, int stride) {
        return diff(pixels[point - 2 * stride], pixels[point - stride]) > threshold
                || diff(pixels[point + stride], pixels[point]) > threshold;
    }

    private static boolean highEdgeVarianceHorizontal(int threshold, byte[] pixels) {
        return diff(pixels[2], pixels[3]) > threshold || diff(pixels[5], pixels[4]) > threshold;
    }

    private static int clampSigned(int value) {
        return Math.max(-128, Math.min(127, value));
    }

    private static int unsignedToSigned(byte value) {
        return (value & 0xFF) - 128;
    }

    private static byte signedToUnsigned(int value) {
        return (byte) (clampSigned(value) + 128);
    }

    private static int diff(byte left, byte right) {
        return Math.abs((left & 0xFF) - (right & 0xFF));
    }
}
