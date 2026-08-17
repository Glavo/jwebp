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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPPixelFormat;
import org.glavo.webp.javafx.WebPFXImageScaler.ScalePlan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// JavaFX image adapter for decoded WebP content.
///
/// Instances are backed by a JavaFX [PixelBuffer] in `INT_ARGB_PRE` representation. A static
/// [WebPFrame] already decoded as [WebPPixelFormat#INT_ARGB_PRE] is used directly when its
/// intrinsic dimensions are requested. Converting the pixel representation or scaling creates a
/// new presentation buffer in the same heap or direct memory location as its source frame.
/// Animated frames are scaled once during construction and copied into one reusable `PixelBuffer`
/// during playback.
///
/// Because the superclass is constructed from a `PixelBuffer`, the inherited
/// [#getPixelWriter()] operation is unsupported.
///
/// Use [#of(WebPFrame)] or [#of(WebPImage)] to create an instance.
@NotNullByDefault
public final class WebPFXImage extends WritableImage {

    /// JavaFX pixel buffer backing this image.
    private final PixelBuffer<IntBuffer> pixelBuffer;

    /// Mutable presentation storage used only by animated images.
    private final @Nullable IntBuffer animationBuffer;

    /// Pixel data and timing retained for animation playback; static images keep this list empty.
    private final @Unmodifiable List<AnimationFrame> animationFrames;

    /// Whether this image has an animated frame sequence.
    private final boolean animated;

    /// Number of animation cycles, or `0` for indefinite playback.
    private final int loopCount;

    /// Lazily created JavaFX animation controller.
    private @Nullable Timeline timeline;

    /// Index of the animation frame most recently written to this image.
    private int renderedFrameIndex = -1;

    /// Creates an intrinsic-size JavaFX image from one decoded frame.
    ///
    /// @param frame the decoded frame to display
    /// @return the JavaFX image
    /// @throws NullPointerException if `frame` is `null`
    public static WebPFXImage of(WebPFrame frame) {
        return of(frame, 0.0, 0.0, false, true);
    }

    /// Creates a JavaFX image from one decoded frame with JavaFX-style scaling parameters.
    ///
    /// A requested dimension less than or equal to zero selects the corresponding intrinsic
    /// dimension. Positive target dimensions are rounded to the nearest integer and clamped to at
    /// least one pixel. If `preserveRatio` is `true`, the requested dimensions form a bounding box
    /// and the source aspect ratio is retained. Otherwise, each positive requested dimension
    /// independently replaces its intrinsic counterpart. Scaling uses bilinear filtering when
    /// `smooth` is `true` and nearest-neighbor filtering otherwise.
    ///
    /// @param frame the decoded frame to display
    /// @param requestedWidth the requested bounding-box width, or a non-positive value to use the
    ///                       intrinsic width
    /// @param requestedHeight the requested bounding-box height, or a non-positive value to use
    ///                        the intrinsic height
    /// @param preserveRatio whether to preserve the intrinsic aspect ratio
    /// @param smooth whether to use bilinear rather than nearest-neighbor filtering
    /// @return the JavaFX image
    /// @throws NullPointerException if `frame` is `null`
    /// @throws IllegalArgumentException if a requested dimension is not finite, rounds beyond the
    ///                                  supported integer range, or produces an unsupported buffer
    ///                                  size
    public static WebPFXImage of(
            WebPFrame frame,
            double requestedWidth,
            double requestedHeight,
            boolean preserveRatio,
            boolean smooth
    ) {
        Objects.requireNonNull(frame, "frame");
        ScalePlan scalePlan = ScalePlan.create(
                frame.getWidth(),
                frame.getHeight(),
                requestedWidth,
                requestedHeight,
                preserveRatio,
                smooth
        );
        return createStaticImage(frame, scalePlan);
    }

    /// Creates an intrinsic-size JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is visible immediately, and animated content starts playing automatically.
    /// Call [#getAnimation()] to control playback.
    ///
    /// @param image the decoded WebP image
    /// @return the JavaFX image
    /// @throws NullPointerException if `image` is `null`
    public static WebPFXImage of(WebPImage image) {
        return of(image, 0.0, 0.0, false, true, true);
    }

    /// Creates an intrinsic-size JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is visible immediately. Animated content starts playing when `autoPlay` is
    /// `true`; otherwise its timeline remains stopped until started through [#getAnimation()].
    ///
    /// @param image the decoded WebP image
    /// @param autoPlay whether to start playing the animation automatically
    /// @return the JavaFX image
    /// @throws NullPointerException if `image` is `null`
    public static WebPFXImage of(WebPImage image, boolean autoPlay) {
        return of(image, 0.0, 0.0, false, true, autoPlay);
    }

    /// Creates a scaled JavaFX image from fully decoded WebP content.
    ///
    /// The scaling parameters have the same order and roles as those of the corresponding JavaFX
    /// `Image` constructors. The first frame is visible immediately, and animated content starts
    /// playing automatically. A non-positive requested dimension uses its intrinsic counterpart;
    /// positive target dimensions are rounded to the nearest integer and clamped to at least one
    /// pixel. Animated frames are scaled once during construction.
    ///
    /// @param image the decoded WebP image
    /// @param requestedWidth the requested bounding-box width, or a non-positive value to use the
    ///                       intrinsic width
    /// @param requestedHeight the requested bounding-box height, or a non-positive value to use
    ///                        the intrinsic height
    /// @param preserveRatio whether to preserve the intrinsic aspect ratio
    /// @param smooth whether to use bilinear rather than nearest-neighbor filtering
    /// @return the JavaFX image
    /// @throws NullPointerException if `image` is `null`
    /// @throws IllegalArgumentException if a requested dimension is not finite, rounds beyond the
    ///                                  supported integer range, or produces an unsupported buffer
    ///                                  size
    public static WebPFXImage of(
            WebPImage image,
            double requestedWidth,
            double requestedHeight,
            boolean preserveRatio,
            boolean smooth
    ) {
        return of(image, requestedWidth, requestedHeight, preserveRatio, smooth, true);
    }

    /// Creates a scaled JavaFX image from fully decoded WebP content.
    ///
    /// The scaling parameters have the same order and roles as those of the corresponding JavaFX
    /// `Image` constructors. The first frame is visible immediately. Animated content starts
    /// playing when `autoPlay` is `true`; otherwise its timeline remains stopped until started
    /// through [#getAnimation()]. Dimension and filtering behavior is identical to
    /// [#of(WebPImage, double, double, boolean, boolean)]. Animated frames are scaled once during
    /// construction.
    ///
    /// @param image the decoded WebP image
    /// @param requestedWidth the requested bounding-box width, or a non-positive value to use the
    ///                       intrinsic width
    /// @param requestedHeight the requested bounding-box height, or a non-positive value to use
    ///                        the intrinsic height
    /// @param preserveRatio whether to preserve the intrinsic aspect ratio
    /// @param smooth whether to use bilinear rather than nearest-neighbor filtering
    /// @param autoPlay whether to start playing the animation automatically
    /// @return the JavaFX image
    /// @throws NullPointerException if `image` is `null`
    /// @throws IllegalArgumentException if a requested dimension is not finite, rounds beyond the
    ///                                  supported integer range, or produces an unsupported buffer
    ///                                  size
    public static WebPFXImage of(
            WebPImage image,
            double requestedWidth,
            double requestedHeight,
            boolean preserveRatio,
            boolean smooth,
            boolean autoPlay
    ) {
        Objects.requireNonNull(image, "image");
        ScalePlan scalePlan = ScalePlan.create(
                image.getWidth(),
                image.getHeight(),
                requestedWidth,
                requestedHeight,
                preserveRatio,
                smooth
        );

        if (!image.isAnimated()) {
            return createStaticImage(image.getFirstFrame(), scalePlan);
        }

        @Unmodifiable List<AnimationFrame> animationFrames = prepareAnimationFrames(
                image.getFrames(),
                scalePlan
        );
        AnimationFrame firstFrame = animationFrames.get(0);
        IntBuffer animationBuffer = WebPFXImageScaler.allocateBuffer(
                scalePlan.targetWidth(),
                scalePlan.targetHeight(),
                firstFrame.pixels().isDirect()
        );
        WebPFXImageScaler.copyAsArgbPre(firstFrame.pixels(), firstFrame.pixelFormat(), animationBuffer);
        PixelBuffer<IntBuffer> pixelBuffer = createPixelBuffer(
                scalePlan.targetWidth(),
                scalePlan.targetHeight(),
                animationBuffer
        );
        return new WebPFXImage(
                pixelBuffer,
                animationBuffer,
                animationFrames,
                image.getLoopCount(),
                autoPlay
        );
    }

    /// Completes construction of a static image after its pixel buffer has been prepared.
    ///
    /// @param pixelBuffer the prepared JavaFX storage
    private WebPFXImage(PixelBuffer<IntBuffer> pixelBuffer) {
        super(pixelBuffer);
        this.pixelBuffer = pixelBuffer;
        this.animationBuffer = null;
        this.animationFrames = List.of();
        this.animated = false;
        this.loopCount = 1;
    }

    /// Completes construction of an animated image after its frames have been prepared.
    ///
    /// @param pixelBuffer the JavaFX presentation storage containing the first frame
    /// @param animationBuffer the mutable backing buffer used for subsequent frames
    /// @param animationFrames the retained animation pixels and timing information
    /// @param loopCount the number of animation cycles, or `0` for indefinite playback
    /// @param autoPlay whether to start animation playback
    private WebPFXImage(
            PixelBuffer<IntBuffer> pixelBuffer,
            IntBuffer animationBuffer,
            @Unmodifiable List<AnimationFrame> animationFrames,
            int loopCount,
            boolean autoPlay
    ) {
        super(pixelBuffer);
        this.pixelBuffer = pixelBuffer;
        this.animationBuffer = animationBuffer;
        this.animationFrames = List.copyOf(animationFrames);
        this.animated = true;
        this.loopCount = loopCount;
        this.renderedFrameIndex = 0;

        if (autoPlay) {
            getAnimation().play();
        }
    }

    /// Returns whether this image is animated.
    ///
    /// @return `true` if this image contains multiple presentation frames
    public boolean isAnimated() {
        return animated;
    }

    /// Returns the JavaFX timeline that drives this image's animation.
    ///
    /// If this image is not [animated][#isAnimated()], `null` is returned. The timeline is created
    /// lazily and reused by subsequent invocations.
    ///
    /// @return the animation controller, or `null` for a static image
    public @Nullable Timeline getAnimation() {
        if (!animated) {
            return null;
        }
        if (timeline == null) {
            timeline = new Timeline();
            timeline.setCycleCount(loopCount == 0 ? Animation.INDEFINITE : loopCount);
            timeline.getKeyFrames().setAll(createKeyFrames());
        }
        return timeline;
    }

    /// Writes an animation frame unless it is already displayed.
    ///
    /// @param frameIndex the animation frame index
    private void renderFrame(int frameIndex) {
        if (frameIndex == renderedFrameIndex) {
            return;
        }
        AnimationFrame frame = animationFrames.get(frameIndex);
        IntBuffer target = animationBuffer;
        if (target == null) {
            throw new IllegalStateException("Animated image has no mutable presentation buffer");
        }
        pixelBuffer.updateBuffer(ignored -> {
            WebPFXImageScaler.copyAsArgbPre(frame.pixels(), frame.pixelFormat(), target);
            return null;
        });
        renderedFrameIndex = frameIndex;
    }

    /// Creates timeline entries for all retained animation frames.
    ///
    /// @return the ordered animation keyframes including the terminal duration marker
    private KeyFrame[] createKeyFrames() {
        KeyFrame[] keyFrames = new KeyFrame[animationFrames.size() + 1];
        long currentStartMillis = 0L;
        for (int index = 0; index < animationFrames.size(); index++) {
            final int frameIndex = index;
            keyFrames[index] = new KeyFrame(
                    Duration.millis(currentStartMillis),
                    event -> renderFrame(frameIndex)
            );
            currentStartMillis += Math.max(1, animationFrames.get(index).durationMillis());
        }

        // The terminal marker keeps the last frame visible for its full duration.
        keyFrames[animationFrames.size()] = new KeyFrame(Duration.millis(currentStartMillis));
        return keyFrames;
    }

    /// Creates a static image using the supplied scaling plan.
    ///
    /// @param frame the source frame
    /// @param scalePlan the target dimensions and filtering mode
    /// @return the prepared static JavaFX image
    private static WebPFXImage createStaticImage(WebPFrame frame, ScalePlan scalePlan) {
        @UnmodifiableView IntBuffer pixels = WebPFXImageScaler.prepareStaticPixels(frame, scalePlan);
        PixelBuffer<IntBuffer> pixelBuffer = createPixelBuffer(
                scalePlan.targetWidth(),
                scalePlan.targetHeight(),
                pixels
        );
        return new WebPFXImage(pixelBuffer);
    }

    /// Prepares the frame data retained by an animated image.
    ///
    /// Intrinsic-size frames retain views of their existing pixel storage. Scaled frames retain
    /// only the target-size premultiplied pixels, allowing the original frame objects and their
    /// full-size storage to be reclaimed when the caller no longer references them.
    ///
    /// @param sourceFrames the decoded presentation frames
    /// @param scalePlan the target dimensions and filtering mode
    /// @return an immutable list of retained animation data
    private static @Unmodifiable List<AnimationFrame> prepareAnimationFrames(
            @Unmodifiable List<WebPFrame> sourceFrames,
            ScalePlan scalePlan
    ) {
        ArrayList<AnimationFrame> preparedFrames = new ArrayList<>(sourceFrames.size());
        for (WebPFrame frame : sourceFrames) {
            if (scalePlan.scalingRequired()) {
                preparedFrames.add(new AnimationFrame(
                        WebPFXImageScaler.scaleAsArgbPre(frame, scalePlan),
                        WebPPixelFormat.INT_ARGB_PRE,
                        frame.getDurationMillis()
                ));
            } else {
                preparedFrames.add(new AnimationFrame(
                        frame.getPixels(),
                        frame.getPixelFormat(),
                        frame.getDurationMillis()
                ));
            }
        }
        return List.copyOf(preparedFrames);
    }

    /// Creates a JavaFX pixel buffer over prepared `INT_ARGB_PRE` storage.
    ///
    /// @param width the image width
    /// @param height the image height
    /// @param buffer the position-zero pixel storage
    /// @return the JavaFX pixel buffer
    private static PixelBuffer<IntBuffer> createPixelBuffer(int width, int height, IntBuffer buffer) {
        return new PixelBuffer<>(width, height, buffer, PixelFormat.getIntArgbPreInstance());
    }

    /// Retained pixel storage and timing for one animation presentation frame.
    ///
    /// @param pixels the position-zero, read-only packed pixel view
    /// @param pixelFormat the representation used by `pixels`
    /// @param durationMillis the presentation duration in milliseconds
    @NotNullByDefault
    private record AnimationFrame(
            @UnmodifiableView IntBuffer pixels,
            WebPPixelFormat pixelFormat,
            int durationMillis
    ) {
    }
}
