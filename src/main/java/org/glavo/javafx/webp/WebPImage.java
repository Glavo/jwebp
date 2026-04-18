package org.glavo.javafx.webp;

import java.util.List;
import java.util.Optional;

/**
 * Fully decoded WebP content.
 *
 * <p>This type is the eager counterpart of {@link WebPImageReader}. It materializes all decoded
 * frames and exposes the associated metadata and animation timing information in immutable form.
 */
public final class WebPImage {

    private final int sourceWidth;
    private final int sourceHeight;
    private final int width;
    private final int height;
    private final boolean alpha;
    private final boolean animated;
    private final boolean lossy;
    private final LoopCount loopCount;
    private final long loopDurationMillis;
    private final WebPMetadata metadata;
    private final List<WebPFrame> frames;

    /**
     * Creates a fully decoded WebP image.
     *
     * @param sourceWidth the source canvas width
     * @param sourceHeight the source canvas height
     * @param width the decoded output width after applying load options
     * @param height the decoded output height after applying load options
     * @param alpha whether any frame carries transparency
     * @param animated whether the source contains animation
     * @param lossy whether any decoded frame uses lossy VP8 compression
     * @param loopCount the animation loop count
     * @param loopDurationMillis the total duration of one animation cycle
     * @param metadata the extracted metadata
     * @param frames the decoded frames in presentation order
     */
    public WebPImage(
            int sourceWidth,
            int sourceHeight,
            int width,
            int height,
            boolean alpha,
            boolean animated,
            boolean lossy,
            LoopCount loopCount,
            long loopDurationMillis,
            WebPMetadata metadata,
            List<WebPFrame> frames
    ) {
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.width = width;
        this.height = height;
        this.alpha = alpha;
        this.animated = animated;
        this.lossy = lossy;
        this.loopCount = loopCount;
        this.loopDurationMillis = loopDurationMillis;
        this.metadata = metadata;
        this.frames = List.copyOf(frames);
    }

    /**
     * Returns the source canvas width before scaling.
     *
     * @return the intrinsic canvas width
     */
    public int getSourceWidth() {
        return sourceWidth;
    }

    /**
     * Returns the source canvas height before scaling.
     *
     * @return the intrinsic canvas height
     */
    public int getSourceHeight() {
        return sourceHeight;
    }

    /**
     * Returns the decoded output width after applying load options.
     *
     * @return the output width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the decoded output height after applying load options.
     *
     * @return the output height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns whether the image contains transparency.
     *
     * @return {@code true} if at least one pixel may carry alpha
     */
    public boolean hasAlpha() {
        return alpha;
    }

    /**
     * Returns whether the source container is animated.
     *
     * @return {@code true} for animated WebP containers
     */
    public boolean isAnimated() {
        return animated;
    }

    /**
     * Returns whether any decoded frame uses lossy VP8 compression.
     *
     * @return {@code true} if the image contains lossy frame data
     */
    public boolean isLossy() {
        return lossy;
    }

    /**
     * Returns the loop count declared by the source animation.
     *
     * <p>Static images report a finite loop count of {@code 1}.
     *
     * @return the loop count
     */
    public LoopCount getLoopCount() {
        return loopCount;
    }

    /**
     * Returns the total duration of one full animation cycle.
     *
     * <p>Static images report {@code 0}.
     *
     * @return the total cycle duration in milliseconds
     */
    public long getLoopDurationMillis() {
        return loopDurationMillis;
    }

    /**
     * Returns the extracted metadata.
     *
     * @return the metadata container, never {@code null}
     */
    public WebPMetadata getMetadata() {
        return metadata;
    }

    /**
     * Returns all decoded frames in presentation order.
     *
     * @return an immutable frame list
     */
    public List<WebPFrame> getFrames() {
        return frames;
    }

    /**
     * Returns the first frame, if present.
     *
     * @return the first frame for still images or animations
     */
    public Optional<WebPFrame> getFirstFrame() {
        return frames.isEmpty() ? Optional.empty() : Optional.of(frames.get(0));
    }
}
