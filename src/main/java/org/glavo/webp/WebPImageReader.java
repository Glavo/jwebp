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

import org.glavo.webp.internal.codec.ExtendedWebP;
import org.glavo.webp.internal.codec.ParsedFrameDescriptor;
import org.glavo.webp.internal.codec.ParsedWebPImage;
import org.glavo.webp.internal.codec.WebPSequentialParser;
import org.glavo.webp.internal.io.BufferedInput;
import org.glavo.webp.internal.lossy.Vp8Decoder;
import org.glavo.webp.internal.lossless.LosslessDecoder;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/// Forward-only reader for WebP content.
///
/// The reader parses the RIFF container sequentially, buffers only the encoded frame payloads
/// needed for later decode, and decodes full-canvas presentation frames on demand. A reader is
/// stateful and not safe for concurrent use. Create configured readers from [WebPDecoder].
@NotNullByDefault
public final class WebPImageReader implements AutoCloseable {

    /// Packed non-premultiplied transparent black used to clear animation canvas regions.
    private static final int TRANSPARENT = 0x00000000;

    /// Opens a streaming reader for a generic byte stream.
    ///
    /// The stream is consumed during the open step so the reader can retain only the encoded
    /// frame payloads that are required for later decode. Ownership of the supplied stream transfers
    /// to this operation: the returned reader closes it, or it is closed before an open failure is
    /// reported.
    ///
    /// @param source the WebP byte stream
    /// @return a new streaming reader
    /// @throws WebPException if the stream cannot be parsed
    /// @throws NullPointerException if `source` is `null`
    public static WebPImageReader open(InputStream source) throws WebPException {
        return WebPDecoder.DEFAULT.open(source);
    }

    /// Opens a streaming reader for a file.
    ///
    /// @param path the WebP file path
    /// @return a new streaming reader
    /// @throws IOException if the file cannot be opened or read
    /// @throws WebPException if the file cannot be parsed
    /// @throws NullPointerException if `path` is `null`
    public static WebPImageReader open(Path path) throws IOException, WebPException {
        return WebPDecoder.DEFAULT.open(path);
    }

    /// Opens a configured reader for a generic byte stream.
    ///
    /// The input is closed if parsing fails; otherwise ownership is transferred to the returned
    /// reader.
    ///
    /// @param source the WebP byte stream
    /// @param decoder the immutable decoder configuration
    /// @return a new configured reader
    /// @throws WebPException if the stream cannot be parsed or read
    static WebPImageReader open(InputStream source, WebPDecoder decoder) throws WebPException {
        BufferedInput bufferedInput = new BufferedInput.OfInputStream(source);
        try {
            ParsedWebPImage image = WebPSequentialParser.parse(bufferedInput);
            return new WebPImageReader(bufferedInput, image, decoder);
        } catch (IOException | RuntimeException ex) {
            closeAfterOpenFailure(bufferedInput, ex);
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new WebPException("Failed to open WebP stream", ex);
        }
    }

    /// Opens a configured reader for a file.
    ///
    /// The channel is closed if parsing fails; otherwise ownership is transferred to the returned
    /// reader.
    ///
    /// @param path the WebP file path
    /// @param decoder the immutable decoder configuration
    /// @return a new configured reader
    /// @throws IOException if the file cannot be opened or read
    /// @throws WebPException if the file cannot be parsed
    static WebPImageReader open(Path path, WebPDecoder decoder) throws IOException, WebPException {
        SeekableByteChannel channel = Files.newByteChannel(path);
        try {
            BufferedInput bufferedInput = new BufferedInput.OfByteChannel(channel);
            ParsedWebPImage image = WebPSequentialParser.parse(bufferedInput);
            return new WebPImageReader(bufferedInput, image, decoder);
        } catch (IOException | RuntimeException ex) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                ex.addSuppressed(suppressed);
            }
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            if (ex instanceof IOException ioException) {
                throw ioException;
            }
            throw new WebPException("Failed to open WebP file: " + path, ex);
        }
    }

    /// Input resource owned and closed by this reader.
    private final AutoCloseable ownedInput;

    /// Parsed container metadata and encoded frame payloads.
    private final ParsedWebPImage image;

    /// Immutable output configuration captured when this reader was opened.
    private final WebPDecoder decoder;

    /// Stateful ALPH decoder whose full-frame VP8L workspace is reused across animation frames.
    private final ExtendedWebP.AlphaDecoder alphaDecoder = new ExtendedWebP.AlphaDecoder();

    /// VP8 color-plane storage reused across animated frame decodes.
    private final Vp8Decoder.DecodeWorkspace vp8Workspace = new Vp8Decoder.DecodeWorkspace();

    /// Index of the next presentation frame to decode.
    private int nextFrameIndex;

    /// Whether this reader has been closed.
    private boolean closed;

    /// Mutable full-size non-premultiplied animation compositing canvas.
    private int @Nullable [] animationCanvas;

    /// Exact-sized scratch pixels reused between compatible animated frame decodes.
    private int @Nullable [] reusableAnimationFrameArgb;

    /// Whether the previous frame region must be cleared before the next composition step.
    private boolean disposeNextFrame = true;

    /// Width of the frame region presented by the previous animation step.
    private int previousFrameWidth;

    /// Height of the frame region presented by the previous animation step.
    private int previousFrameHeight;

    /// Horizontal offset of the previous animation frame region.
    private int previousFrameX;

    /// Vertical offset of the previous animation frame region.
    private int previousFrameY;

    /// Creates a reader over parsed input and takes ownership of that input.
    ///
    /// @param ownedInput the input closed with this reader
    /// @param image the parsed WebP container
    /// @param decoder the immutable output configuration
    private WebPImageReader(AutoCloseable ownedInput, ParsedWebPImage image, WebPDecoder decoder) {
        this.ownedInput = ownedInput;
        this.image = image;
        this.decoder = decoder;
    }

    /// Returns the image canvas width.
    ///
    /// @return the canvas width in pixels
    public int getWidth() {
        return image.sourceWidth();
    }

    /// Returns the image canvas height.
    ///
    /// @return the canvas height in pixels
    public int getHeight() {
        return image.sourceHeight();
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
    /// Each returned animation frame is already composited to the full source canvas. Its pixel
    /// format and buffer location use the defaults of the [WebPDecoder] that created this reader.
    ///
    /// @return the next frame, or `null` when the stream is exhausted
    /// @throws WebPException if decoding fails
    public @Nullable WebPFrame readNextFrame() throws WebPException {
        return readNextFrame(decoder.isDirect());
    }

    /// Decodes the next frame with an explicit buffer-location override.
    ///
    /// The `direct` argument applies only to the frame returned by this invocation. It does not
    /// modify the default used by later calls to [#readNextFrame()]. The pixel format remains the
    /// format configured by the [WebPDecoder] that created this reader.
    ///
    /// @param direct `true` to return a direct pixel buffer, or `false` for a heap buffer
    /// @return the next frame, or `null` when the stream is exhausted
    /// @throws WebPException if decoding fails
    public @Nullable WebPFrame readNextFrame(boolean direct) throws WebPException {
        ensureOpen();
        if (nextFrameIndex >= image.frames().size()) {
            return null;
        }

        ParsedFrameDescriptor descriptor = image.frames().get(nextFrameIndex++);
        if (!image.animated() && direct) {
            return decodeDirectStaticFrame(descriptor);
        }

        int[] frameArgb = decodeFrameArgb(descriptor);
        if (image.animated()) {
            compositeAnimatedFrame(descriptor, frameArgb);
            assert animationCanvas != null;
            return decoder.createFrame(
                    image.sourceWidth(),
                    image.sourceHeight(),
                    descriptor.durationMillis(),
                    animationCanvas,
                    direct,
                    true
            );
        }
        return decoder.createFrame(
                descriptor.width(),
                descriptor.height(),
                descriptor.durationMillis(),
                frameArgb,
                direct,
                false
        );
    }

    /// Decodes a static frame directly into the storage retained by the returned frame.
    ///
    /// @param descriptor the parsed static-frame descriptor
    /// @return the decoded direct frame
    /// @throws WebPException if VP8, VP8L, or ALPH decoding fails
    private WebPFrame decodeDirectStaticFrame(ParsedFrameDescriptor descriptor) throws WebPException {
        int pixelCount;
        try {
            pixelCount = Math.multiplyExact(descriptor.width(), descriptor.height());
        } catch (ArithmeticException ex) {
            throw new WebPException("Frame dimensions are too large", ex);
        }
        IntBuffer frameArgb = WebPFrame.allocateDirectPixels(pixelCount);
        decodeFrameArgb(descriptor, frameArgb);
        return decoder.createFrame(
                descriptor.width(),
                descriptor.height(),
                descriptor.durationMillis(),
                frameArgb,
                !descriptor.lossless() && descriptor.alphaChunk() == null
        );
    }

    /// Closes the owned input resource.
    ///
    /// Repeated calls have no effect. Once closed, this reader cannot decode additional frames.
    ///
    /// @throws IOException if closing the owned input fails
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            ownedInput.close();
        } catch (Exception ex) {
            if (ex instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to close the WebP reader input", ex);
        }
    }

    /// Composites one decoded subframe onto the persistent animation canvas.
    ///
    /// @param descriptor the parsed animation-frame descriptor
    /// @param frameArgb the decoded non-premultiplied subframe pixels
    private void compositeAnimatedFrame(ParsedFrameDescriptor descriptor, int[] frameArgb) {
        if (animationCanvas == null) {
            animationCanvas = new int[image.sourceWidth() * image.sourceHeight()];
        }

        Integer clearColor = null;
        if (disposeNextFrame) {
            clearColor = TRANSPARENT;
        }

        /*
         * Lossy, lossless and ALPH paths are normalized to packed non-premultiplied ARGB before
         * composition. The requested public representation is applied only when the immutable
         * presentation frame is created.
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
    }

    /// Decodes one raw frame payload to tightly packed non-premultiplied `ARGB` pixels.
    ///
    /// @param descriptor the parsed frame descriptor and encoded payload
    /// @return the decoded frame-region pixels
    /// @throws WebPException if VP8, VP8L, or ALPH decoding fails
    private int[] decodeFrameArgb(ParsedFrameDescriptor descriptor) throws WebPException {
        int[] argb = acquireFrameArgb(descriptor.width() * descriptor.height());
        if (descriptor.lossless()) {
            new LosslessDecoder(descriptor.imageChunk()).decodeFrame(descriptor.width(), descriptor.height(), false, argb);
            return argb;
        }

        Vp8Decoder.decodeArgb(ByteBuffer.wrap(descriptor.imageChunk()), false, argb, vp8Workspace);
        if (descriptor.alphaChunk() != null) {
            alphaDecoder.apply(
                    descriptor.alphaChunk(),
                    descriptor.width(),
                    descriptor.height(),
                    argb
            );
        }
        return argb;
    }

    /// Decodes one raw frame payload directly to tightly packed non-premultiplied `ARGB` pixels.
    ///
    /// @param descriptor the parsed frame descriptor and encoded payload
    /// @param argb the exact-sized direct destination
    /// @throws WebPException if VP8, VP8L, or ALPH decoding fails
    private void decodeFrameArgb(ParsedFrameDescriptor descriptor, IntBuffer argb) throws WebPException {
        if (descriptor.lossless()) {
            new LosslessDecoder(descriptor.imageChunk()).decodeFrame(
                    descriptor.width(),
                    descriptor.height(),
                    false,
                    argb
            );
        } else {
            Vp8Decoder.decodeArgb(
                    ByteBuffer.wrap(descriptor.imageChunk()),
                    false,
                    argb,
                    vp8Workspace
            );
            if (descriptor.alphaChunk() != null) {
                alphaDecoder.apply(
                        descriptor.alphaChunk(),
                        descriptor.width(),
                        descriptor.height(),
                        argb
                );
            }
        }
    }

    /// Returns an exact-sized frame buffer, reusing it when animated frames share dimensions.
    ///
    /// @param length the required pixel count
    /// @return an exact-sized mutable decode buffer
    private int[] acquireFrameArgb(int length) {
        if (!image.animated()) {
            return new int[length];
        }
        if (reusableAnimationFrameArgb == null || reusableAnimationFrameArgb.length != length) {
            reusableAnimationFrameArgb = new int[length];
        }
        return reusableAnimationFrameArgb;
    }

    /// Verifies that frame decoding is still permitted.
    ///
    /// @throws WebPException if this reader has been closed
    private void ensureOpen() throws WebPException {
        if (closed) {
            throw new WebPException("Reader is already closed");
        }
    }

    /// Closes input retained during a failed open and attaches any close failure to the cause.
    ///
    /// @param input the input whose ownership was not transferred to a reader
    /// @param cause the original open failure
    private static void closeAfterOpenFailure(BufferedInput input, Throwable cause) {
        try {
            input.close();
        } catch (IOException suppressed) {
            cause.addSuppressed(suppressed);
        }
    }
}
