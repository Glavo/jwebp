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
package org.glavo.webp.javafx;

import org.jetbrains.annotations.NotNullByDefault;

/// Immutable presentation options for [WebPFXImage].
///
/// Scaling affects only the JavaFX presentation and never changes decoded [org.glavo.webp.WebPFrame]
/// or [org.glavo.webp.WebPImage] dimensions. Configuration methods return a new options object and
/// never modify the receiver, so instances may be reused safely across threads.
@NotNullByDefault
public final class WebPFXImageOptions {

    /// Default intrinsic-size, smoothly filtered, auto-playing presentation options.
    public static final WebPFXImageOptions DEFAULT = new WebPFXImageOptions(
            0.0,
            0.0,
            false,
            true,
            true
    );

    /// Requested presentation width, or a non-positive value to leave the width unspecified.
    private final double requestedWidth;

    /// Requested presentation height, or a non-positive value to leave the height unspecified.
    private final double requestedHeight;

    /// Whether scaling preserves the intrinsic aspect ratio.
    private final boolean preserveRatio;

    /// Whether scaling uses bilinear instead of nearest-neighbor filtering.
    private final boolean smooth;

    /// Whether animated images start playing when constructed.
    private final boolean autoPlay;

    /// Creates validated immutable presentation options.
    ///
    /// @param requestedWidth the requested presentation width
    /// @param requestedHeight the requested presentation height
    /// @param preserveRatio whether scaling preserves the intrinsic aspect ratio
    /// @param smooth whether scaling uses bilinear filtering
    /// @param autoPlay whether animated images start playing when constructed
    private WebPFXImageOptions(
            double requestedWidth,
            double requestedHeight,
            boolean preserveRatio,
            boolean smooth,
            boolean autoPlay
    ) {
        this.requestedWidth = requireFinite(requestedWidth, "requestedWidth");
        this.requestedHeight = requireFinite(requestedHeight, "requestedHeight");
        this.preserveRatio = preserveRatio;
        this.smooth = smooth;
        this.autoPlay = autoPlay;
    }

    /// Returns the requested presentation width.
    ///
    /// A value less than or equal to zero leaves the width unspecified. Without aspect-ratio
    /// preservation, an unspecified width uses the intrinsic width. With aspect-ratio preservation,
    /// it may instead be derived from a positive requested height.
    ///
    /// @return the requested width
    public double getRequestedWidth() {
        return requestedWidth;
    }

    /// Returns the requested presentation height.
    ///
    /// A value less than or equal to zero leaves the height unspecified. Without aspect-ratio
    /// preservation, an unspecified height uses the intrinsic height. With aspect-ratio
    /// preservation, it may instead be derived from a positive requested width.
    ///
    /// @return the requested height
    public double getRequestedHeight() {
        return requestedHeight;
    }

    /// Returns whether scaling preserves the intrinsic aspect ratio.
    ///
    /// When enabled, two positive requested dimensions define a bounding box. If only one
    /// requested dimension is positive, the other is derived from the intrinsic aspect ratio.
    ///
    /// @return `true` if scaling preserves the aspect ratio
    public boolean isPreserveRatio() {
        return preserveRatio;
    }

    /// Returns whether scaling uses smooth filtering.
    ///
    /// @return `true` for bilinear filtering, or `false` for nearest-neighbor filtering
    public boolean isSmooth() {
        return smooth;
    }

    /// Returns whether animated images start playing when constructed.
    ///
    /// This setting has no effect on static images or individual frames.
    ///
    /// @return `true` if animated images start automatically
    public boolean isAutoPlay() {
        return autoPlay;
    }

    /// Returns options with the supplied requested dimensions.
    ///
    /// Non-positive values leave the corresponding dimension unspecified. When
    /// [#isPreserveRatio()] is enabled, two positive dimensions define a bounding box, and one
    /// positive dimension determines the other from the intrinsic aspect ratio. Otherwise, each
    /// positive dimension independently replaces its intrinsic counterpart.
    ///
    /// @param requestedWidth the requested width, or a non-positive value to leave it unspecified
    /// @param requestedHeight the requested height, or a non-positive value to leave it unspecified
    /// @return this object if both values are unchanged; otherwise a new options object
    /// @throws IllegalArgumentException if either value is not finite
    public WebPFXImageOptions withRequestedSize(double requestedWidth, double requestedHeight) {
        requireFinite(requestedWidth, "requestedWidth");
        requireFinite(requestedHeight, "requestedHeight");
        if (this.requestedWidth == requestedWidth && this.requestedHeight == requestedHeight) {
            return this;
        }
        return new WebPFXImageOptions(requestedWidth, requestedHeight, preserveRatio, smooth, autoPlay);
    }

    /// Returns options that enable or disable aspect-ratio preservation.
    ///
    /// @param preserveRatio whether scaling preserves the intrinsic aspect ratio
    /// @return this object if the value is unchanged; otherwise a new options object
    public WebPFXImageOptions withPreserveRatio(boolean preserveRatio) {
        return this.preserveRatio == preserveRatio
                ? this
                : new WebPFXImageOptions(requestedWidth, requestedHeight, preserveRatio, smooth, autoPlay);
    }

    /// Returns options that select smooth or nearest-neighbor filtering.
    ///
    /// @param smooth `true` for bilinear filtering, or `false` for nearest-neighbor filtering
    /// @return this object if the value is unchanged; otherwise a new options object
    public WebPFXImageOptions withSmooth(boolean smooth) {
        return this.smooth == smooth
                ? this
                : new WebPFXImageOptions(requestedWidth, requestedHeight, preserveRatio, smooth, autoPlay);
    }

    /// Returns options that enable or disable automatic animation playback.
    ///
    /// @param autoPlay whether animated images start playing when constructed
    /// @return this object if the value is unchanged; otherwise a new options object
    public WebPFXImageOptions withAutoPlay(boolean autoPlay) {
        return this.autoPlay == autoPlay
                ? this
                : new WebPFXImageOptions(requestedWidth, requestedHeight, preserveRatio, smooth, autoPlay);
    }

    /// Returns a finite dimension or rejects the supplied value.
    ///
    /// @param value the requested dimension
    /// @param name the parameter name used in exceptions
    /// @return `value` when finite
    /// @throws IllegalArgumentException if `value` is not finite
    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }
}
