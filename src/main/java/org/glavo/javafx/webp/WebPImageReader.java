package org.glavo.javafx.webp;

import org.glavo.javafx.webp.internal.PixelScaler;
import org.glavo.javafx.webp.internal.ScalePlan;
import org.glavo.javafx.webp.internal.codec.ExtendedWebP;
import org.glavo.javafx.webp.internal.codec.ParsedFrameDescriptor;
import org.glavo.javafx.webp.internal.codec.ParsedWebPImage;
import org.glavo.javafx.webp.internal.codec.WebPSequentialParser;
import org.glavo.javafx.webp.internal.lossy.Vp8Decoder;
import org.glavo.javafx.webp.internal.lossless.LosslessDecoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/// Forward-only reader for WebP content.
///
/// The reader parses the RIFF container sequentially, buffers only the encoded frame payloads
/// needed for later decode, and decodes frames on demand in presentation order. Scaling is applied
/// immediately after each frame is decoded or composited so callers do not need to allocate both
/// source-sized and target-sized frame lists.
public final class WebPImageReader implements AutoCloseable {

    private static final byte[] TRANSPARENT = {0, 0, 0, 0};

    private final AutoCloseable ownedInput;
    private final ParsedWebPImage image;
    private final ScalePlan scalePlan;
    private int nextFrameIndex;
    private boolean closed;

    private byte[] animationCanvas;
    private boolean disposeNextFrame = true;
    private int previousFrameWidth;
    private int previousFrameHeight;
    private int previousFrameX;
    private int previousFrameY;

    private WebPImageReader(AutoCloseable ownedInput, ParsedWebPImage image, ScalePlan scalePlan) {
        this.ownedInput = ownedInput;
        this.image = image;
        this.scalePlan = scalePlan;
    }

    /// Opens a streaming reader for a generic byte stream.
    ///
    /// The stream is consumed during the open step so the reader can retain only the encoded
    /// frame payloads that are required for later decode. The supplied stream remains owned by the
    /// returned reader and is closed when [#close()] is called.
    ///
    /// @param source the WebP byte stream
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return a new streaming reader
    /// @throws WebPException if the stream cannot be parsed or decoded
    public static WebPImageReader open(InputStream source, WebPImageLoadOptions options) throws WebPException {
        try {
            ParsedWebPImage image = WebPSequentialParser.parse(source);
            ScalePlan scalePlan = ScalePlan.create(image.sourceWidth(), image.sourceHeight(), options);
            return new WebPImageReader(source, image, scalePlan);
        } catch (IOException ex) {
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            throw new WebPException("Failed to open WebP stream", ex);
        }
    }

    /// Opens a streaming reader for a file.
    ///
    /// @param path the WebP file path
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return a new streaming reader
    /// @throws IOException if the file cannot be opened
    /// @throws WebPException if the file cannot be parsed or decoded
    public static WebPImageReader open(Path path, WebPImageLoadOptions options) throws IOException, WebPException {
        InputStream input = Files.newInputStream(path);
        try {
            ParsedWebPImage image = WebPSequentialParser.parse(input);
            ScalePlan scalePlan = ScalePlan.create(image.sourceWidth(), image.sourceHeight(), options);
            return new WebPImageReader(input, image, scalePlan);
        } catch (IOException | RuntimeException ex) {
            try {
                input.close();
            } catch (IOException suppressed) {
                ex.addSuppressed(suppressed);
            }
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            if (ex instanceof IOException ioException) {
                throw ioException;
            }
            throw new WebPException("Failed to open WebP file: " + path, ex);
        }
    }

    /// Returns the intrinsic source width.
    ///
    /// @return the source canvas width in pixels
    public int getSourceWidth() {
        return image.sourceWidth();
    }

    /// Returns the intrinsic source height.
    ///
    /// @return the source canvas height in pixels
    public int getSourceHeight() {
        return image.sourceHeight();
    }

    /// Returns the decoded output width after applying load options.
    ///
    /// @return the output width in pixels
    public int getWidth() {
        return scalePlan.targetWidth();
    }

    /// Returns the decoded output height after applying load options.
    ///
    /// @return the output height in pixels
    public int getHeight() {
        return scalePlan.targetHeight();
    }

    /// Returns whether the source image contains transparency.
    ///
    /// @return `true` if any decoded frame may carry alpha
    public boolean hasAlpha() {
        return image.hasAlpha();
    }

    /// Returns whether the source container is animated.
    ///
    /// @return `true` for animated WebP containers
    public boolean isAnimated() {
        return image.animated();
    }

    /// Returns whether the source contains lossy VP8 frame data.
    ///
    /// @return `true` if any frame is lossy
    public boolean isLossy() {
        return image.lossy();
    }

    /// Returns the number of frames declared by the source container.
    ///
    /// Static images return `1`.
    ///
    /// @return the number of presentation frames
    public int getFrameCount() {
        return image.frames().size();
    }

    /// Returns the animation loop count.
    ///
    /// Static images report a loop count of `1`.
    ///
    /// @return the loop count
    public LoopCount getLoopCount() {
        return image.loopCount();
    }

    /// Returns the total duration of one animation cycle.
    ///
    /// Static images report `0`.
    ///
    /// @return the total cycle duration in milliseconds
    public long getLoopDurationMillis() {
        return image.loopDurationMillis();
    }

    /// Returns the extracted metadata.
    ///
    /// @return the metadata container
    public WebPMetadata getMetadata() {
        return image.metadata();
    }

    /// Returns whether all frames have already been consumed.
    ///
    /// @return `true` when no more frames are available
    public boolean isComplete() {
        return nextFrameIndex >= image.frames().size();
    }

    /// Decodes the next frame, if available.
    ///
    /// Each returned frame is already composited to the full canvas for animated images and
    /// already scaled according to the load options supplied when the reader was opened.
    ///
    /// @return the next frame, or [Optional#empty()] when the stream is exhausted
    /// @throws WebPException if decoding fails
    public Optional<WebPFrame> readNextFrame() throws WebPException {
        ensureOpen();
        if (nextFrameIndex >= image.frames().size()) {
            return Optional.empty();
        }

        ParsedFrameDescriptor descriptor = image.frames().get(nextFrameIndex++);
        byte[] frameRgba = decodeFrameRgba(descriptor);
        byte[] output;

        if (image.animated()) {
            output = decodeAnimatedFrame(descriptor, frameRgba);
        } else {
            output = PixelScaler.scaleRgba(frameRgba, descriptor.width(), descriptor.height(), scalePlan);
        }

        return Optional.of(new WebPFrame(scalePlan.targetWidth(), scalePlan.targetHeight(), descriptor.durationMillis(), output));
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (ownedInput != null) {
            try {
                ownedInput.close();
            } catch (Exception ex) {
                if (ex instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException("Failed to close the WebP reader input", ex);
            }
        }
    }

    private byte[] decodeAnimatedFrame(ParsedFrameDescriptor descriptor, byte[] frameRgba) {
        if (animationCanvas == null) {
            animationCanvas = new byte[image.sourceWidth() * image.sourceHeight() * 4];
        }

        byte[] clearColor = null;
        if (disposeNextFrame) {
            clearColor = TRANSPARENT;
        }

        /*
         * The public API always exposes already-composited RGBA frames. Even opaque VP8 frames are
         * normalized to RGBA before composition so the animation canvas can remain a single format.
         */
        ExtendedWebP.compositeFrame(
                animationCanvas,
                image.sourceWidth(),
                image.sourceHeight(),
                clearColor,
                frameRgba,
                descriptor.x(),
                descriptor.y(),
                descriptor.width(),
                descriptor.height(),
                true,
                descriptor.useAlphaBlending(),
                previousFrameWidth,
                previousFrameHeight,
                previousFrameX,
                previousFrameY
        );

        previousFrameWidth = descriptor.width();
        previousFrameHeight = descriptor.height();
        previousFrameX = descriptor.x();
        previousFrameY = descriptor.y();
        disposeNextFrame = descriptor.disposeToBackground();

        return PixelScaler.scaleRgba(animationCanvas, image.sourceWidth(), image.sourceHeight(), scalePlan);
    }

    private byte[] decodeFrameRgba(ParsedFrameDescriptor descriptor) throws WebPException {
        if (descriptor.lossless()) {
            byte[] rgba = new byte[descriptor.width() * descriptor.height() * 4];
            new LosslessDecoder(descriptor.imageChunk()).decodeFrame(descriptor.width(), descriptor.height(), false, rgba);
            return rgba;
        }

        byte[] rgba = Vp8Decoder.decodeRgba(new ByteArrayInputStream(descriptor.imageChunk()), false);
        if (descriptor.alphaChunk() != null) {
            ExtendedWebP.AlphaChunk alphaChunk = ExtendedWebP.parseAlphaChunk(
                    descriptor.alphaChunk(),
                    descriptor.width(),
                    descriptor.height()
            );
            applyAlphaChunk(rgba, descriptor.width(), descriptor.height(), alphaChunk);
        }
        return rgba;
    }

    private static void applyAlphaChunk(
            byte[] rgba,
            int width,
            int height,
            ExtendedWebP.AlphaChunk alphaChunk
    ) {
        byte[] alphaData = alphaChunk.data();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                int predictor = ExtendedWebP.getAlphaPredictor(
                        x,
                        y,
                        width,
                        alphaChunk.filteringMethod(),
                        rgba
                );
                rgba[pixelIndex * 4 + 3] = (byte) (((alphaData[pixelIndex] & 0xFF) + predictor) & 0xFF);
            }
        }
    }

    private void ensureOpen() throws WebPException {
        if (closed) {
            throw new WebPException("Reader is already closed");
        }
    }
}
