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

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPImageLoadOptions;

/// Calculates the decoded output dimensions from JavaFX-style image loading options.
@NotNullByDefault
public record ScalePlan(
        int sourceWidth,
        int sourceHeight,
        int targetWidth,
        int targetHeight,
        boolean smooth
) {

    /// Computes a scale plan that mirrors the documented semantics of JavaFX `Image`.
    ///
    /// The calculations intentionally happen once per image so that both eager and streaming
    /// decoding share the same target dimensions.
    ///
    /// @param sourceWidth the intrinsic width
    /// @param sourceHeight the intrinsic height
    /// @param options the user-supplied load options
    /// @return the resulting scale plan
    public static ScalePlan create(int sourceWidth, int sourceHeight, WebPImageLoadOptions options) {
        int targetWidth = sourceWidth;
        int targetHeight = sourceHeight;

        double requestedWidth = options.requestedWidth();
        double requestedHeight = options.requestedHeight();

        if (options.preserveRatio()) {
            if (requestedWidth > 0 && requestedHeight > 0) {
                double scale = Math.min(requestedWidth / sourceWidth, requestedHeight / sourceHeight);
                targetWidth = clampDimension(sourceWidth * scale);
                targetHeight = clampDimension(sourceHeight * scale);
            } else if (requestedWidth > 0) {
                double scale = requestedWidth / sourceWidth;
                targetWidth = clampDimension(requestedWidth);
                targetHeight = clampDimension(sourceHeight * scale);
            } else if (requestedHeight > 0) {
                double scale = requestedHeight / sourceHeight;
                targetWidth = clampDimension(sourceWidth * scale);
                targetHeight = clampDimension(requestedHeight);
            }
        } else {
            if (requestedWidth > 0) {
                targetWidth = clampDimension(requestedWidth);
            }
            if (requestedHeight > 0) {
                targetHeight = clampDimension(requestedHeight);
            }
        }

        return new ScalePlan(sourceWidth, sourceHeight, targetWidth, targetHeight, options.smooth());
    }

    private static int clampDimension(double value) {
        return Math.max(1, (int) Math.round(value));
    }
}
