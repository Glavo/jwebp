package org.glavo.javafx.webp;

import javafx.scene.image.WritableImage;
import org.glavo.javafx.webp.internal.FxImages;

import java.nio.ByteBuffer;

/**
 * A decoded frame represented as tightly packed non-premultiplied RGBA pixels.
 *
 * <p>For static images the library returns a single frame with a duration of {@code 0}. For
 * animated images each frame already represents the fully composited canvas for the corresponding
 * presentation step, which makes the object suitable for direct JavaFX display.
 */
public final class WebPFrame {

    private final int width;
    private final int height;
    private final int stride;
    private final int durationMillis;
    private final byte[] pixels;

    /**
     * Creates a frame from decoded RGBA pixels.
     *
     * @param width the frame width in pixels
     * @param height the frame height in pixels
     * @param durationMillis the display duration in milliseconds, or {@code 0} for still images
     * @param pixels tightly packed non-premultiplied RGBA pixels
     */
    public WebPFrame(int width, int height, int durationMillis, byte[] pixels) {
        this.width = width;
        this.height = height;
        this.stride = width * 4;
        this.durationMillis = durationMillis;
        this.pixels = pixels.clone();
    }

    /**
     * Returns the frame width.
     *
     * @return the frame width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the frame height.
     *
     * @return the frame height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the number of bytes between two adjacent rows.
     *
     * <p>The library always stores pixels as tightly packed RGBA data, so the stride is always
     * {@code width * 4}.
     *
     * @return the byte stride of the frame
     */
    public int getStride() {
        return stride;
    }

    /**
     * Returns the frame duration in milliseconds.
     *
     * @return the presentation duration, or {@code 0} for still images
     */
    public int getDurationMillis() {
        return durationMillis;
    }

    /**
     * Returns a read-only view of the underlying RGBA pixel buffer.
     *
     * <p>Each invocation returns a fresh view whose position is set to {@code 0}. The returned
     * buffer is safe to share, but mutating it is not allowed.
     *
     * @return a read-only RGBA pixel buffer
     */
    public ByteBuffer getPixels() {
        return ByteBuffer.wrap(pixels).asReadOnlyBuffer();
    }

    /**
     * Creates a JavaFX {@link WritableImage} from this frame.
     *
     * <p>The conversion premultiplies alpha as required by JavaFX's writable pixel formats.
     *
     * @return a newly allocated JavaFX image
     */
    public WritableImage toWritableImage() {
        return FxImages.toWritableImage(width, height, pixels);
    }
}
