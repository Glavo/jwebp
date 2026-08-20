// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
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
/// intrinsic dimensions are requested and its pixels are direct. Heap pixels, pixel-format
/// conversion, and scaling are copied into direct presentation storage.
/// Animated frames are scaled once during construction and copied into one reusable `PixelBuffer`
/// during playback.
///
/// Because the superclass is constructed from a `PixelBuffer`, the inherited
/// [#getPixelWriter()] operation is unsupported.
///
/// Use [#of(WebPFrame)] or [#of(WebPImage)] for the default presentation, or pass
/// [WebPFXImageOptions] to configure scaling, filtering, and animation playback.
@NotNullByDefault
public final class WebPFXImage extends WritableImage {

    /// JavaFX pixel buffer backed by direct pixel storage.
    private final PixelBuffer<IntBuffer> pixelBuffer;

    /// Mutable direct presentation storage used only by animated images.
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
        return of(frame, WebPFXImageOptions.DEFAULT);
    }

    /// Creates a JavaFX image from one decoded frame using immutable presentation options.
    ///
    /// The `autoPlay` setting has no effect because a [WebPFrame] is a static presentation frame.
    ///
    /// @param frame the decoded frame to display
    /// @param options the scaling and presentation options
    /// @return the JavaFX image
    /// @throws NullPointerException if `frame` or `options` is `null`
    /// @throws IllegalArgumentException if a requested dimension rounds beyond the supported
    ///                                  integer range or produces an unsupported buffer size
    public static WebPFXImage of(WebPFrame frame, WebPFXImageOptions options) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(options, "options");
        ScalePlan scalePlan = ScalePlan.create(
                frame.getWidth(),
                frame.getHeight(),
                options
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
        return of(image, WebPFXImageOptions.DEFAULT);
    }

    /// Creates a JavaFX image from fully decoded WebP content using immutable presentation options.
    ///
    /// The first frame is visible immediately. Animated content starts playing when
    /// [WebPFXImageOptions#isAutoPlay()] is `true`; otherwise its timeline remains stopped until
    /// started through [#getAnimation()]. Animated frames are scaled once during construction.
    ///
    /// @param image the decoded WebP image
    /// @param options the scaling, filtering, and playback options
    /// @return the JavaFX image
    /// @throws NullPointerException if `image` or `options` is `null`
    /// @throws IllegalArgumentException if a requested dimension rounds beyond the supported
    ///                                  integer range or produces an unsupported buffer size
    public static WebPFXImage of(WebPImage image, WebPFXImageOptions options) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(options, "options");
        ScalePlan scalePlan = ScalePlan.create(
                image.getWidth(),
                image.getHeight(),
                options
        );

        if (!image.isAnimated()) {
            return createStaticImage(image.getFirstFrame(), scalePlan);
        }

        @Unmodifiable List<AnimationFrame> animationFrames = prepareAnimationFrames(
                image.getFrames(),
                scalePlan
        );
        AnimationFrame firstFrame = animationFrames.get(0);
        IntBuffer animationBuffer = WebPFXImageScaler.allocateDirectBuffer(
                scalePlan.targetWidth(),
                scalePlan.targetHeight()
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
                options.isAutoPlay()
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
    /// only target-size direct premultiplied pixels, allowing the original frame objects and their
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

    /// Creates a JavaFX pixel buffer over prepared direct `INT_ARGB_PRE` storage.
    ///
    /// @param width the image width
    /// @param height the image height
    /// @param buffer the position-zero direct pixel storage
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
