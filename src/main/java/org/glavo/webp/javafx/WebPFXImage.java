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
import org.glavo.webp.WebPException;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPImageReader;
import org.glavo.webp.WebPPixelFormat;
import org.glavo.webp.internal.Argb;
import org.glavo.webp.javafx.WebPFXImageScaler.ScalePlan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// JavaFX image adapter for decoded WebP content.
///
/// Instances are backed by a JavaFX [PixelBuffer] in `INT_ARGB_PRE` representation. Static and
/// animated pixels are prepared during construction. Animation playback copies each presentation
/// step into one reusable `PixelBuffer`.
///
/// Because the superclass is constructed from a `PixelBuffer`, the inherited
/// [#getPixelWriter()] operation is unsupported.
///
/// Use [#read(Path)] or [#read(InputStream)] to decode a source directly for JavaFX presentation.
/// Use [#of(WebPFrame)] or [#of(WebPImage)] to adapt already decoded content. Presentation options
/// configure scaling, filtering, and animation playback.
@NotNullByDefault
public final class WebPFXImage extends WritableImage {

    /// JavaFX pixel buffer backed by adaptive pixel storage.
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

    /// Reads a WebP stream into an intrinsic-size JavaFX image.
    ///
    /// Ownership of `input` transfers to this method. The stream is closed before this method
    /// returns, including when decoding or JavaFX image construction fails.
    ///
    /// @param input the WebP byte stream
    /// @return the decoded JavaFX image
    /// @throws WebPException if parsing, decoding, or closing fails
    /// @throws NullPointerException if `input` is `null`
    public static WebPFXImage read(InputStream input) throws WebPException {
        return read(input, WebPFXImageOptions.DEFAULT);
    }

    /// Reads a WebP stream into a JavaFX image using immutable presentation options.
    ///
    /// Once both arguments have been validated, ownership of `input` transfers to this method. The
    /// stream is closed before this method returns, including when decoding or JavaFX image
    /// construction fails.
    ///
    /// @param input the WebP byte stream
    /// @param options the scaling, filtering, and playback options
    /// @return the decoded JavaFX image
    /// @throws WebPException if parsing, decoding, or closing fails
    /// @throws NullPointerException if `input` or `options` is `null`
    public static WebPFXImage read(
            InputStream input,
            WebPFXImageOptions options
    ) throws WebPException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(input, "input");
        try (WebPImageReader reader = WebPImageReader.open(input)) {
            return read(reader, options);
        } catch (IOException ex) {
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            throw new WebPException("Failed to decode WebP stream", ex);
        }
    }

    /// Reads a WebP file into an intrinsic-size JavaFX image.
    ///
    /// @param path the WebP file path
    /// @return the decoded JavaFX image
    /// @throws WebPException if the file cannot be opened, parsed, decoded, or closed
    /// @throws NullPointerException if `path` is `null`
    public static WebPFXImage read(Path path) throws WebPException {
        return read(path, WebPFXImageOptions.DEFAULT);
    }

    /// Reads a WebP file into a JavaFX image using immutable presentation options.
    ///
    /// @param path the WebP file path
    /// @param options the scaling, filtering, and playback options
    /// @return the decoded JavaFX image
    /// @throws WebPException if the file cannot be opened, parsed, decoded, or closed
    /// @throws NullPointerException if `path` or `options` is `null`
    public static WebPFXImage read(Path path, WebPFXImageOptions options) throws WebPException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        try (WebPImageReader reader = WebPImageReader.open(path)) {
            return read(reader, options);
        } catch (WebPException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new WebPException("Failed to decode WebP file: " + path, ex);
        }
    }

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

        PreparedAnimation preparedAnimation = prepareAnimation(
                image.getFrames(),
                scalePlan
        );
        return createAnimatedImage(
                preparedAnimation,
                scalePlan.targetWidth(),
                scalePlan.targetHeight(),
                image.getLoopCount(),
                options.isAutoPlay()
        );
    }

    /// Decodes an opened reader directly into JavaFX presentation storage.
    ///
    /// The caller remains responsible for closing `reader`.
    ///
    /// @param reader the opened WebP reader
    /// @param options the scaling, filtering, and playback options
    /// @return the decoded JavaFX image
    /// @throws WebPException if frame decoding fails
    private static WebPFXImage read(
            WebPImageReader reader,
            WebPFXImageOptions options
    ) throws WebPException {
        ScalePlan scalePlan = ScalePlan.create(
                reader.getWidth(),
                reader.getHeight(),
                options
        );
        if (!reader.isAnimated()) {
            return readStaticImage(reader, scalePlan);
        }

        PreparedAnimation preparedAnimation = readAnimation(reader, scalePlan);
        return createAnimatedImage(
                preparedAnimation,
                scalePlan.targetWidth(),
                scalePlan.targetHeight(),
                reader.getLoopCount(),
                options.isAutoPlay()
        );
    }

    /// Decodes one static frame using the cheapest storage path for its scale plan.
    ///
    /// Intrinsic-size pixels are decoded directly into the final JavaFX backing storage. Scaled
    /// images use a temporary frame and retain only their scaled pixels.
    ///
    /// @param reader the opened static-image reader
    /// @param scalePlan the target dimensions and filtering mode
    /// @return the decoded static JavaFX image
    /// @throws WebPException if frame decoding fails
    private static WebPFXImage readStaticImage(
            WebPImageReader reader,
            ScalePlan scalePlan
    ) throws WebPException {
        if (!scalePlan.scalingRequired()) {
            IntBuffer pixels = WebPFXImageStorage.allocate(
                    scalePlan.targetWidth(),
                    scalePlan.targetHeight()
            );
            requireFrame(reader.readNextFrame(WebPPixelFormat.INT_ARGB_PRE, pixels));
            pixels.rewind();
            return new WebPFXImage(createPixelBuffer(
                    scalePlan.targetWidth(),
                    scalePlan.targetHeight(),
                    pixels
            ));
        }

        IntBuffer sourcePixels = WebPFXImageStorage.allocate(
                reader.getWidth(),
                reader.getHeight()
        );
        WebPFrame frame = requireFrame(reader.readNextFrame(WebPPixelFormat.INT_ARGB, sourcePixels));
        return createStaticImage(frame, scalePlan);
    }

    /// Decodes animation frames directly into packed JavaFX preparation storage.
    ///
    /// Scaled animations reuse one adaptively allocated canvas-sized destination while each frame
    /// is immediately scaled into its retained target slice. Intrinsic-size animations decode their
    /// premultiplied pixels directly into retained slices.
    ///
    /// @param reader the opened animated-image reader
    /// @param scalePlan the target dimensions and filtering mode
    /// @return the retained frames and mutable presentation region
    /// @throws WebPException if any frame cannot be decoded
    private static PreparedAnimation readAnimation(
            WebPImageReader reader,
            ScalePlan scalePlan
    ) throws WebPException {
        int targetPixelCount;
        int regionCount;
        try {
            targetPixelCount = Math.multiplyExact(scalePlan.targetWidth(), scalePlan.targetHeight());
            regionCount = Math.addExact(reader.getFrameCount(), 1);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Animation dimensions are too large", ex);
        }

        IntBuffer[] regions = WebPFXImageStorage.allocateRegions(regionCount, targetPixelCount);
        ArrayList<AnimationFrame> frames = new ArrayList<>(reader.getFrameCount());
        if (scalePlan.scalingRequired()) {
            int sourcePixelCount;
            try {
                sourcePixelCount = Math.multiplyExact(reader.getWidth(), reader.getHeight());
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Animation canvas is too large", ex);
            }
            IntBuffer sourcePixels = WebPFXImageStorage.allocatePixels(sourcePixelCount);
            for (int frameIndex = 0; frameIndex < reader.getFrameCount(); frameIndex++) {
                sourcePixels.clear();
                WebPFrame frame = requireFrame(reader.readNextFrame(WebPPixelFormat.INT_ARGB, sourcePixels));
                IntBuffer targetPixels = regions[frameIndex];
                WebPFXImageScaler.scaleAsArgbPre(frame, scalePlan, targetPixels);
                frames.add(new AnimationFrame(
                        targetPixels,
                        frame.getDurationMillis()
                ));
            }
        } else {
            for (int frameIndex = 0; frameIndex < reader.getFrameCount(); frameIndex++) {
                IntBuffer framePixels = regions[frameIndex];
                WebPFrame frame = requireFrame(reader.readNextFrame(
                        WebPPixelFormat.INT_ARGB_PRE,
                        framePixels
                ));
                framePixels.rewind();
                frames.add(new AnimationFrame(
                        framePixels,
                        frame.getDurationMillis()
                ));
            }
        }
        return new PreparedAnimation(
                List.copyOf(frames),
                regions[reader.getFrameCount()]
        );
    }

    /// Returns a decoded frame or reports an inconsistent exhausted reader.
    ///
    /// @param frame the result of a frame read expected to succeed
    /// @return the non-null decoded frame
    /// @throws WebPException if the reader unexpectedly has no frame
    private static WebPFrame requireFrame(@Nullable WebPFrame frame) throws WebPException {
        if (frame == null) {
            throw new WebPException("WebP reader ended before all declared frames were decoded");
        }
        return frame;
    }

    /// Creates an animated JavaFX image from prepared frame and presentation storage.
    ///
    /// @param preparedAnimation retained frame data and mutable presentation pixels
    /// @param width the presentation width in pixels
    /// @param height the presentation height in pixels
    /// @param loopCount the animation loop count, or `0` for indefinite playback
    /// @param autoPlay whether to start playback during construction
    /// @return the animated JavaFX image
    private static WebPFXImage createAnimatedImage(
            PreparedAnimation preparedAnimation,
            int width,
            int height,
            int loopCount,
            boolean autoPlay
    ) {
        @Unmodifiable List<AnimationFrame> animationFrames = preparedAnimation.frames();
        AnimationFrame firstFrame = animationFrames.get(0);
        IntBuffer animationBuffer = preparedAnimation.presentationPixels();
        WebPFXImageScaler.copyAsArgbPre(
                firstFrame.pixels(),
                WebPPixelFormat.INT_ARGB_PRE,
                animationBuffer
        );
        PixelBuffer<IntBuffer> pixelBuffer = createPixelBuffer(width, height, animationBuffer);
        return new WebPFXImage(
                pixelBuffer,
                animationBuffer,
                animationFrames,
                loopCount,
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
            WebPFXImageScaler.copyAsArgbPre(
                    frame.pixels(),
                    WebPPixelFormat.INT_ARGB_PRE,
                    target
            );
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

    /// Prepares retained frame data and mutable presentation storage for an animated image.
    ///
    /// Intrinsic-size frames whose stored bits are already valid `INT_ARGB_PRE` pixels are retained
    /// directly when their storage location is compatible with the adaptive animation policy.
    /// Other frames are converted once into packed target-size storage. A separate mutable region
    /// backs JavaFX presentation.
    ///
    /// @param sourceFrames the decoded presentation frames
    /// @param scalePlan the target dimensions and filtering mode
    /// @return the retained frames and mutable JavaFX presentation pixels
    private static PreparedAnimation prepareAnimation(
            @Unmodifiable List<WebPFrame> sourceFrames,
            ScalePlan scalePlan
    ) {
        int pixelCount;
        int regionCount;
        try {
            pixelCount = Math.multiplyExact(scalePlan.targetWidth(), scalePlan.targetHeight());
            regionCount = Math.addExact(sourceFrames.size(), 1);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Animation dimensions are too large", ex);
        }

        if (!scalePlan.scalingRequired()) {
            return prepareIntrinsicAnimation(
                    sourceFrames,
                    pixelCount,
                    regionCount
            );
        }

        IntBuffer[] regions = WebPFXImageStorage.allocateRegions(regionCount, pixelCount);
        ArrayList<AnimationFrame> preparedFrames = new ArrayList<>(sourceFrames.size());
        for (int frameIndex = 0; frameIndex < sourceFrames.size(); frameIndex++) {
            WebPFrame frame = sourceFrames.get(frameIndex);
            IntBuffer framePixels = regions[frameIndex];
            WebPFXImageScaler.scaleAsArgbPre(frame, scalePlan, framePixels);
            preparedFrames.add(new AnimationFrame(framePixels, frame.getDurationMillis()));
        }
        return new PreparedAnimation(
                List.copyOf(preparedFrames),
                regions[sourceFrames.size()]
        );
    }

    /// Prepares intrinsic-size frames while retaining each compatible source region directly.
    ///
    /// Incompatible frames and the mutable presentation region share packed adaptive storage. When
    /// the combined animation size selects direct storage, heap-backed source frames are migrated
    /// while compatible direct frames remain reusable.
    ///
    /// @param sourceFrames the decoded presentation frames
    /// @param pixelCount the number of pixels in each target frame
    /// @param regionCount the frame count plus one mutable presentation region
    /// @return retained frame data and mutable presentation storage
    private static PreparedAnimation prepareIntrinsicAnimation(
            @Unmodifiable List<WebPFrame> sourceFrames,
            int pixelCount,
            int regionCount
    ) {
        boolean directPreferred = WebPFXImageStorage.prefersDirect(pixelCount, regionCount);
        ArrayList<@UnmodifiableView IntBuffer> sourcePixels = new ArrayList<>(sourceFrames.size());
        boolean[] reusable = new boolean[sourceFrames.size()];
        int convertedFrameCount = 0;
        for (int frameIndex = 0; frameIndex < sourceFrames.size(); frameIndex++) {
            WebPFrame frame = sourceFrames.get(frameIndex);
            @UnmodifiableView IntBuffer pixels = frame.getPixels();
            sourcePixels.add(pixels);
            reusable[frameIndex] = (!directPreferred || pixels.isDirect())
                    && (frame.getPixelFormat() == WebPPixelFormat.INT_ARGB_PRE
                        || Argb.countOpaquePrefix(pixels) == pixelCount);
            if (!reusable[frameIndex]) {
                convertedFrameCount++;
            }
        }

        IntBuffer[] convertedRegions = WebPFXImageStorage.allocateRegions(
                convertedFrameCount + 1,
                pixelCount,
                directPreferred
        );
        ArrayList<AnimationFrame> frames = new ArrayList<>(sourceFrames.size());
        int convertedFrameIndex = 0;
        for (int frameIndex = 0; frameIndex < sourceFrames.size(); frameIndex++) {
            WebPFrame frame = sourceFrames.get(frameIndex);
            IntBuffer framePixels = sourcePixels.get(frameIndex);
            if (!reusable[frameIndex]) {
                framePixels = convertedRegions[convertedFrameIndex++];
                WebPFXImageScaler.copyAsArgbPre(
                        sourcePixels.get(frameIndex),
                        frame.getPixelFormat(),
                        framePixels
                );
            }
            frames.add(new AnimationFrame(framePixels, frame.getDurationMillis()));
        }
        assert convertedFrameIndex == convertedFrameCount;

        return new PreparedAnimation(
                List.copyOf(frames),
                convertedRegions[convertedFrameCount]
        );
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
    /// @param pixels the position-zero packed pixel view retained without subsequent modification
    /// @param durationMillis the presentation duration in milliseconds
    @NotNullByDefault
    private record AnimationFrame(
            @UnmodifiableView IntBuffer pixels,
            int durationMillis
    ) {
    }

    /// Prepared immutable animation frames and the mutable JavaFX presentation region.
    ///
    /// @param frames retained frame pixels and timing information
    /// @param presentationPixels writable target-size `INT_ARGB_PRE` pixels
    @NotNullByDefault
    private record PreparedAnimation(
            @Unmodifiable List<AnimationFrame> frames,
            IntBuffer presentationPixels
    ) {
    }
}
