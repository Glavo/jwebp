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
package org.glavo.webp;

import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNullByDefault;

/// Immutable loading options that mirror the scaling-related parameters of the JavaFX
/// [Image] constructors.
///
/// The options only control the dimensions and filtering used for decoded output. They never
/// alter the source WebP metadata or animation timing.
///
/// @param requestedWidth  the requested bounding-box width. If this value is less than or equal to zero, the intrinsic width is used.
/// @param requestedHeight the requested bounding-box height. If this value is less than or equal to zero, the intrinsic height is used.
/// @param preserveRatio   whether the source aspect ratio should be preserved when fitting into the requested width/height bounding box.
/// @param smooth          whether a higher-quality scaling filter should be used.
@NotNullByDefault
public record WebPImageLoadOptions(
        double requestedWidth,
        double requestedHeight,
        boolean preserveRatio,
        boolean smooth) {

    /// Creates a new option with the given requested dimensions.
    ///
    /// @param requestedWidth  the requested bounding-box width. If this value is less than or equal to zero, the intrinsic width is used.
    /// @param requestedHeight the requested bounding-box height. If this value is less than or equal to zero, the intrinsic height is used.
    public WebPImageLoadOptions(double requestedWidth, double requestedHeight) {
        this(requestedWidth, requestedHeight, false, true);
    }

    /// The default options.
    ///
    /// The default configuration requests the intrinsic image size, does not preserve ratio
    /// because no explicit bounding box is provided, and enables smooth filtering.
    public static final WebPImageLoadOptions DEFAULT = new WebPImageLoadOptions(0, 0);

    /// Creates a new builder.
    ///
    /// @return a builder initialized with default option values
    public static Builder builder() {
        return new Builder();
    }

    /// Indicates whether to preserve the aspect ratio of the original image
    /// when scaling to fit the image within the bounding box provided by
    /// `width` and `height`.
    ///
    /// If set to `true`, it affects the dimensions of this `Image`
    /// in the following way:
    ///
    ///   - If only `width` is set, height is scaled to preserve ratio
    ///   - If only `height` is set, width is scaled to preserve ratio
    ///   - If both are set, they both may be scaled to get the best fit in a
    ///     width-by-height rectangle while preserving the original aspect ratio
    ///
    /// The reported `width` and `height` may be different from the
    /// initially set values if they needed to be adjusted to preserve an aspect ratio.
    ///
    /// If unset or set to `false`, it affects the dimensions of this
    /// `ImageView` in the following way:
    ///
    ///   - If only `width` is set, the image's width is scaled to
    ///     match and height is unchanged;
    ///   - If only `height` is set, the image's height is scaled to
    ///     match and height is unchanged;
    ///   - If both are set, the image is scaled to match both.
    ///
    /// @return true if the aspect ratio of the original image is to be
    ///               preserved when scaling to fit the image within the bounding
    ///               box provided by `width` and `height`.
    @Override
    public boolean preserveRatio() {
        return preserveRatio;
    }

    /// Builder for [WebPImageLoadOptions].
    @NotNullByDefault
    public static final class Builder {
        private double requestedWidth;
        private double requestedHeight;
        private boolean preserveRatio;
        private boolean smooth = true;

        private Builder() {
        }

        /// Sets the requested bounding-box width.
        ///
        /// @param requestedWidth the requested width, or a non-positive value to keep the intrinsic
        ///                       width
        /// @return this builder
        public Builder requestedWidth(double requestedWidth) {
            this.requestedWidth = requestedWidth;
            return this;
        }

        /// Sets the requested bounding-box height.
        ///
        /// @param requestedHeight the requested height, or a non-positive value to keep the intrinsic
        ///                        height
        /// @return this builder
        public Builder requestedHeight(double requestedHeight) {
            this.requestedHeight = requestedHeight;
            return this;
        }

        /// Sets whether the source aspect ratio should be preserved.
        ///
        /// @param preserveRatio whether scaling should preserve the original aspect ratio
        /// @return this builder
        public Builder preserveRatio(boolean preserveRatio) {
            this.preserveRatio = preserveRatio;
            return this;
        }

        /// Sets whether the higher-quality scaling filter should be used.
        ///
        /// @param smooth`true` for higher-quality filtering, `false` for faster scaling
        /// @return this builder
        public Builder smooth(boolean smooth) {
            this.smooth = smooth;
            return this;
        }

        /// Creates the immutable options instance.
        ///
        /// @return the configured load options
        public WebPImageLoadOptions build() {
            return new WebPImageLoadOptions(requestedWidth, requestedHeight, preserveRatio, smooth);
        }
    }
}
