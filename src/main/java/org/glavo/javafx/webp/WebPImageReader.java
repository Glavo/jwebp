package org.glavo.javafx.webp;

import org.glavo.javafx.webp.internal.BufferedImages;
import org.glavo.javafx.webp.internal.ScalePlan;
import org.glavo.javafx.webp.internal.WebPContainerInfo;
import org.glavo.javafx.webp.internal.WebPContainerParser;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

/**
 * Forward-only reader for WebP content.
 *
 * <p>The reader exposes already-decoded frames in presentation order. It keeps only the state
 * required to decode the next frame and therefore avoids materializing the entire animation unless
 * the caller explicitly collects all frames.
 */
public final class WebPImageReader implements AutoCloseable {

    static {
        ImageIO.scanForPlugins();
    }

    private final ImageInputStream input;
    private final ImageReader reader;
    private final WebPContainerInfo info;
    private final ScalePlan scalePlan;
    private int nextFrameIndex;
    private boolean closed;

    private WebPImageReader(ImageInputStream input, ImageReader reader, WebPContainerInfo info, ScalePlan scalePlan) {
        this.input = input;
        this.reader = reader;
        this.info = info;
        this.scalePlan = scalePlan;
    }

    /**
     * Opens a streaming reader for a generic byte stream.
     *
     * @param source the WebP byte stream
     * @param options scaling options that mirror JavaFX {@code Image} loading parameters
     * @return a new streaming reader
     * @throws WebPException if the stream cannot be parsed or decoded
     */
    public static WebPImageReader open(InputStream source, WebPImageLoadOptions options) throws WebPException {
        try {
            ImageInputStream input = ImageIO.createImageInputStream(source);
            if (input == null) {
                throw new WebPException("Could not create ImageInputStream for source stream");
            }
            return open(input, options);
        } catch (IOException ex) {
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            throw new WebPException("Failed to open WebP stream", ex);
        }
    }

    /**
     * Opens a streaming reader for a file.
     *
     * @param path the WebP file path
     * @param options scaling options that mirror JavaFX {@code Image} loading parameters
     * @return a new streaming reader
     * @throws IOException if the file cannot be opened
     * @throws WebPException if the file cannot be parsed or decoded
     */
    public static WebPImageReader open(Path path, WebPImageLoadOptions options) throws IOException, WebPException {
        ImageInputStream input = ImageIO.createImageInputStream(path.toFile());
        if (input == null) {
            throw new IOException("Could not create ImageInputStream for file: " + path);
        }
        return open(input, options);
    }

    private static WebPImageReader open(ImageInputStream input, WebPImageLoadOptions options) throws WebPException {
        try {
            WebPContainerInfo info = WebPContainerParser.parse(input);
            ScalePlan scalePlan = ScalePlan.create(info.sourceWidth(), info.sourceHeight(), options);
            input.seek(0);

            ImageReader reader = createReader();
            reader.setInput(input, false, false);

            return new WebPImageReader(input, reader, info, scalePlan);
        } catch (IOException | RuntimeException ex) {
            closeQuietly(input);
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            throw new WebPException("Failed to open WebP decoder", ex);
        }
    }

    private static ImageReader createReader() throws WebPException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType("image/webp");
        if (!readers.hasNext()) {
            readers = ImageIO.getImageReadersByFormatName("webp");
        }
        if (!readers.hasNext()) {
            throw new WebPException("No ImageIO WebP reader is available. Ensure the WebP plugin dependency is present at runtime.");
        }
        return readers.next();
    }

    /**
     * Returns the intrinsic source width.
     *
     * @return the source canvas width in pixels
     */
    public int getSourceWidth() {
        return info.sourceWidth();
    }

    /**
     * Returns the intrinsic source height.
     *
     * @return the source canvas height in pixels
     */
    public int getSourceHeight() {
        return info.sourceHeight();
    }

    /**
     * Returns the decoded output width after applying load options.
     *
     * @return the output width in pixels
     */
    public int getWidth() {
        return scalePlan.targetWidth();
    }

    /**
     * Returns the decoded output height after applying load options.
     *
     * @return the output height in pixels
     */
    public int getHeight() {
        return scalePlan.targetHeight();
    }

    /**
     * Returns whether the source image contains transparency.
     *
     * @return {@code true} if any decoded frame may carry alpha
     */
    public boolean hasAlpha() {
        return info.hasAlpha();
    }

    /**
     * Returns whether the source container is animated.
     *
     * @return {@code true} for animated WebP containers
     */
    public boolean isAnimated() {
        return info.animated();
    }

    /**
     * Returns whether the source contains lossy VP8 frame data.
     *
     * @return {@code true} if any frame is lossy
     */
    public boolean isLossy() {
        return info.lossy();
    }

    /**
     * Returns the number of frames declared by the source container.
     *
     * <p>Static images return {@code 1}.
     *
     * @return the number of presentation frames
     */
    public int getFrameCount() {
        return info.frames().size();
    }

    /**
     * Returns the animation loop count.
     *
     * <p>Static images report a loop count of {@code 1}.
     *
     * @return the loop count
     */
    public LoopCount getLoopCount() {
        return info.loopCount();
    }

    /**
     * Returns the total duration of one animation cycle.
     *
     * <p>Static images report {@code 0}.
     *
     * @return the total cycle duration in milliseconds
     */
    public long getLoopDurationMillis() {
        return info.loopDurationMillis();
    }

    /**
     * Returns the extracted metadata.
     *
     * @return the metadata container
     */
    public WebPMetadata getMetadata() {
        return info.metadata();
    }

    /**
     * Returns whether all frames have already been consumed.
     *
     * @return {@code true} when no more frames are available
     */
    public boolean isComplete() {
        return nextFrameIndex >= info.frames().size();
    }

    /**
     * Decodes the next frame, if available.
     *
     * <p>The method reads a single frame from the underlying decoder, scales it according to
     * {@link WebPImageLoadOptions}, converts it to tightly packed RGBA pixels, and advances the
     * reader to the next presentation step.
     *
     * @return the next frame, or {@link Optional#empty()} when the stream is exhausted
     * @throws WebPException if decoding fails
     */
    public Optional<WebPFrame> readNextFrame() throws WebPException {
        ensureOpen();
        if (nextFrameIndex >= info.frames().size()) {
            return Optional.empty();
        }

        try {
            BufferedImage frame = reader.read(nextFrameIndex);
            if (frame == null) {
                throw new WebPException("ImageIO returned a null frame for index " + nextFrameIndex);
            }

            // Animated WebP readers generally return a fully composited canvas-sized frame. If the
            // provider returns a smaller intermediate image, we still normalize and scale the data
            // that was returned instead of attempting to guess undisclosed composition rules.
            BufferedImage scaled = BufferedImages.scale(frame, scalePlan);
            byte[] rgba = BufferedImages.toRgba(scaled);
            int durationMillis = info.frames().get(nextFrameIndex).durationMillis();
            nextFrameIndex++;
            return Optional.of(new WebPFrame(scalePlan.targetWidth(), scalePlan.targetHeight(), durationMillis, rgba));
        } catch (IIOException ex) {
            throw new WebPException("Failed to decode WebP frame " + nextFrameIndex, ex);
        } catch (IOException ex) {
            throw new WebPException("I/O failure while decoding WebP frame " + nextFrameIndex, ex);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            reader.dispose();
        } finally {
            input.close();
        }
    }

    private void ensureOpen() throws WebPException {
        if (closed) {
            throw new WebPException("Reader is already closed");
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
