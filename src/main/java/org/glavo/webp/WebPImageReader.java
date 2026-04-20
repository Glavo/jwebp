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

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.internal.PixelScaler;
import org.glavo.webp.internal.ScalePlan;
import org.glavo.webp.internal.codec.ExtendedWebP;
import org.glavo.webp.internal.codec.ParsedFrameDescriptor;
import org.glavo.webp.internal.codec.ParsedWebPImage;
import org.glavo.webp.internal.codec.WebPSequentialParser;
import org.glavo.webp.internal.io.BufferedInput;
import org.glavo.webp.internal.lossy.Vp8Decoder;
import org.glavo.webp.internal.lossless.LosslessDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/// Forward-only reader for WebP content.
///
/// The reader parses the RIFF container sequentially, buffers only the encoded frame payloads
/// needed for later decode, and decodes frames on demand in presentation order. Scaling is applied
/// immediately after each frame is decoded or composited so callers do not need to allocate both
/// source-sized and target-sized frame lists.
@NotNullByDefault
public final class WebPImageReader implements AutoCloseable {

    private static final int TRANSPARENT = 0x00000000;

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
            BufferedInput bufferedInput = new BufferedInput.OfInputStream(source);
            ParsedWebPImage image = WebPSequentialParser.parse(bufferedInput);
            ScalePlan scalePlan = ScalePlan.create(image.sourceWidth(), image.sourceHeight(), options);
            return new WebPImageReader(bufferedInput, image, scalePlan);
        } catch (IOException ex) {
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            throw new WebPException("Failed to open WebP stream", ex);
        }
    }

    /// Opens a streaming reader for a generic byte stream using the default options.
    ///
    /// @param source the WebP byte stream
    /// @return a new streaming reader
    /// @throws WebPException if the stream cannot be parsed or decoded
    public static WebPImageReader open(InputStream source) throws WebPException {
        return open(source, WebPImageLoadOptions.DEFAULT);
    }

    /// Opens a streaming reader for a file.
    ///
    /// @param path the WebP file path
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return a new streaming reader
    /// @throws IOException if the file cannot be opened
    /// @throws WebPException if the file cannot be parsed or decoded
    public static WebPImageReader open(Path path, WebPImageLoadOptions options) throws IOException, WebPException {
        SeekableByteChannel channel = Files.newByteChannel(path);
        try {
            BufferedInput bufferedInput = new BufferedInput.OfByteChannel(channel);
            ParsedWebPImage image = WebPSequentialParser.parse(bufferedInput);
            ScalePlan scalePlan = ScalePlan.create(image.sourceWidth(), image.sourceHeight(), options);
            return new WebPImageReader(bufferedInput, image, scalePlan);
        } catch (IOException | RuntimeException ex) {
            try {
                channel.close();
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

    /// Opens a streaming reader for a file using the default options.
    ///
    /// @param path the WebP file path
    /// @return a new streaming reader
    /// @throws IOException if the file cannot be opened
    /// @throws WebPException if the file cannot be parsed or decoded
    public static WebPImageReader open(Path path) throws IOException, WebPException {
        return open(path, WebPImageLoadOptions.DEFAULT);
    }

    private final AutoCloseable ownedInput;
    private final ParsedWebPImage image;
    private final ScalePlan scalePlan;
    private int nextFrameIndex;
    private boolean closed;

    private int @Nullable [] animationCanvas;
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
    /// Static images report `1`. A value of `0` means the animation loops forever.
    ///
    /// @return the loop count
    public int getLoopCount() {
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
    /// @return the next frame, or `null` when the stream is exhausted
    /// @throws WebPException if decoding fails
    public @Nullable WebPFrame readNextFrame() throws WebPException {
        ensureOpen();
        if (nextFrameIndex >= image.frames().size()) {
            return null;
        }

        ParsedFrameDescriptor descriptor = image.frames().get(nextFrameIndex++);
        int[] frameArgb = decodeFrameArgb(descriptor);
        int[] output;

        if (image.animated()) {
            output = decodeAnimatedFrame(descriptor, frameArgb);
        } else {
            output = PixelScaler.scaleArgb(frameArgb, descriptor.width(), descriptor.height(), scalePlan);
        }

        return new WebPFrame(scalePlan.targetWidth(), scalePlan.targetHeight(), descriptor.durationMillis(), output);
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

    private int[] decodeAnimatedFrame(ParsedFrameDescriptor descriptor, int[] frameArgb) {
        if (animationCanvas == null) {
            animationCanvas = new int[image.sourceWidth() * image.sourceHeight()];
        }

        Integer clearColor = null;
        if (disposeNextFrame) {
            clearColor = TRANSPARENT;
        }

        /*
         * The public API always exposes already-composited ARGB frames. Lossy, lossless and ALPH
         * paths are normalized to packed non-premultiplied ARGB before composition so the canvas
         * can stay in one format end-to-end.
         */
        ExtendedWebP.compositeFrame(
                animationCanvas,
                image.sourceWidth(),
                image.sourceHeight(),
                clearColor,
                frameArgb,
                descriptor.x(),
                descriptor.y(),
                descriptor.width(),
                descriptor.height(),
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

        if (scalePlan.targetWidth() == image.sourceWidth() && scalePlan.targetHeight() == image.sourceHeight()) {
            return animationCanvas.clone();
        }
        return PixelScaler.scaleArgb(animationCanvas, image.sourceWidth(), image.sourceHeight(), scalePlan);
    }

    private int[] decodeFrameArgb(ParsedFrameDescriptor descriptor) throws WebPException {
        if (descriptor.lossless()) {
            int[] argb = new int[descriptor.width() * descriptor.height()];
            new LosslessDecoder(descriptor.imageChunk()).decodeFrame(descriptor.width(), descriptor.height(), false, argb);
            return argb;
        }

        int[] argb = Vp8Decoder.decodeArgb(ByteBuffer.wrap(descriptor.imageChunk()), false);
        if (descriptor.alphaChunk() != null) {
            ExtendedWebP.AlphaChunk alphaChunk = ExtendedWebP.parseAlphaChunk(
                    descriptor.alphaChunk(),
                    descriptor.width(),
                    descriptor.height()
            );
            applyAlphaChunk(argb, descriptor.width(), descriptor.height(), alphaChunk);
        }
        return argb;
    }

    private static void applyAlphaChunk(
            int[] argb,
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
                        argb
                );
                argb[pixelIndex] = Argb.withAlpha(
                        argb[pixelIndex],
                        ((alphaData[pixelIndex] & 0xFF) + predictor) & 0xFF
                );
            }
        }
    }

    private void ensureOpen() throws WebPException {
        if (closed) {
            throw new WebPException("Reader is already closed");
        }
    }
}
