// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.glavo.webp.internal.Argb;
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
import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/// Forward-only reader for WebP content.
///
/// The reader parses the RIFF container sequentially, buffers only the encoded frame payloads
/// needed for later decode, and decodes full-canvas presentation frames on demand. A reader is
/// stateful and not safe for concurrent use. The output pixel representation is selected for each
/// frame read.
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
        Objects.requireNonNull(source, "source");
        BufferedInput bufferedInput = new BufferedInput.OfInputStream(source);
        try {
            ParsedWebPImage image = WebPSequentialParser.parse(bufferedInput);
            return new WebPImageReader(bufferedInput, image);
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

    /// Opens a streaming reader for a file.
    ///
    /// @param path the WebP file path
    /// @return a new streaming reader
    /// @throws IOException if the file cannot be opened or read
    /// @throws WebPException if the file cannot be parsed
    /// @throws NullPointerException if `path` is `null`
    public static WebPImageReader open(Path path) throws IOException, WebPException {
        Objects.requireNonNull(path, "path");
        SeekableByteChannel channel = Files.newByteChannel(path);
        try {
            BufferedInput bufferedInput = new BufferedInput.OfByteChannel(channel);
            ParsedWebPImage image = WebPSequentialParser.parse(bufferedInput);
            return new WebPImageReader(bufferedInput, image);
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

    /// Image canvas width in pixels.
    private final int width;

    /// Image canvas height in pixels.
    private final int height;

    /// Whether any decoded frame may carry alpha.
    private final boolean alpha;

    /// Whether the source contains animation.
    private final boolean animated;

    /// Whether any frame uses lossy VP8 compression.
    private final boolean lossy;

    /// Animation loop count, or zero for indefinite looping.
    private final int loopCount;

    /// Duration of one animation cycle in milliseconds.
    private final long loopDurationMillis;

    /// Encoded descriptors that have not yet been consumed successfully.
    private final @Nullable ParsedFrameDescriptor[] frameDescriptors;

    /// Immutable metadata that owns the parser-exclusive payload arrays.
    private final WebPMetadata metadata;

    /// Stateful VP8 decoder created on demand and reused across lossy frame decodes.
    private @Nullable Vp8Decoder vp8Decoder;

    /// Stateful VP8L decoder created on demand and reused across lossless frame decodes.
    private @Nullable LosslessDecoder losslessDecoder;

    /// Index of the next presentation frame to decode.
    private int nextFrameIndex;

    /// Whether this reader has been closed.
    private boolean closed;

    /// Mutable full-size non-premultiplied animation compositing canvas.
    private int @Nullable [] animationCanvas;

    /// Grow-only scratch pixels reused across animated frame-region decodes.
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
    private WebPImageReader(AutoCloseable ownedInput, ParsedWebPImage image) {
        this.ownedInput = ownedInput;
        this.width = image.sourceWidth();
        this.height = image.sourceHeight();
        this.alpha = image.hasAlpha();
        this.animated = image.animated();
        this.lossy = image.lossy();
        this.loopCount = image.loopCount();
        this.loopDurationMillis = image.loopDurationMillis();
        this.frameDescriptors = image.frames().toArray(ParsedFrameDescriptor[]::new);
        this.metadata = WebPMetadata.fromOwnedPayloads(
                image.iccProfile(),
                image.exifMetadata(),
                image.xmpMetadata()
        );
    }

    /// Returns the image canvas width.
    ///
    /// @return the canvas width in pixels
    public int getWidth() {
        return width;
    }

    /// Returns the image canvas height.
    ///
    /// @return the canvas height in pixels
    public int getHeight() {
        return height;
    }

    /// Returns whether the source image contains transparency.
    ///
    /// @return `true` if any decoded frame may carry alpha
    public boolean hasAlpha() {
        return alpha;
    }

    /// Returns whether the source container is animated.
    ///
    /// @return `true` for animated WebP containers
    public boolean isAnimated() {
        return animated;
    }

    /// Returns whether the source contains lossy VP8 frame data.
    ///
    /// @return `true` if any frame is lossy
    public boolean isLossy() {
        return lossy;
    }

    /// Returns the number of frames declared by the source container.
    ///
    /// Static images return `1`.
    ///
    /// @return the number of presentation frames
    public int getFrameCount() {
        return frameDescriptors.length;
    }

    /// Returns the animation loop count.
    ///
    /// Static images report `1`. A value of `0` means the animation loops forever.
    ///
    /// @return the loop count
    public int getLoopCount() {
        return loopCount;
    }

    /// Returns the total duration of one animation cycle.
    ///
    /// Static images report `0`.
    ///
    /// @return the total cycle duration in milliseconds
    public long getLoopDurationMillis() {
        return loopDurationMillis;
    }

    /// Returns the extracted metadata.
    ///
    /// @return the metadata container
    public WebPMetadata getMetadata() {
        return metadata;
    }

    /// Returns whether all frames have already been consumed.
    ///
    /// @return `true` when no more frames are available
    public boolean isComplete() {
        return nextFrameIndex >= frameDescriptors.length;
    }

    /// Decodes the next frame, if available.
    ///
    /// Each returned animation frame is already composited to the full source canvas. Its pixel
    /// storage is heap-backed and uses [WebPPixelFormat#INT_ARGB].
    ///
    /// @return the next frame, or `null` when the stream is exhausted
    /// @throws WebPException if decoding fails
    public @Nullable WebPFrame readNextFrame() throws WebPException {
        return readNextFrame(WebPPixelFormat.INT_ARGB);
    }

    /// Decodes the next frame into reader-allocated heap storage, if a frame is available.
    ///
    /// Each returned animation frame is already composited to the full source canvas. The returned
    /// frame owns its heap-backed storage in the requested representation.
    ///
    /// @param pixelFormat the stored pixel representation
    /// @return the next frame, or `null` when the stream is exhausted
    /// @throws WebPException if decoding fails
    /// @throws NullPointerException if `pixelFormat` is `null`
    public @Nullable WebPFrame readNextFrame(WebPPixelFormat pixelFormat) throws WebPException {
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        ensureOpen();
        if (nextFrameIndex >= frameDescriptors.length) {
            return null;
        }

        ParsedFrameDescriptor descriptor = Objects.requireNonNull(
                frameDescriptors[nextFrameIndex],
                "Frame descriptor already consumed"
        );
        try {
            int[] frameArgb = decodeFrameArgb(descriptor);
            WebPFrame frame;
            if (animated) {
                compositeAnimatedFrame(descriptor, frameArgb, framePixelCount());
                assert animationCanvas != null;
                int[] pixels;
                boolean opaque = false;
                if (pixelFormat == WebPPixelFormat.INT_ARGB_PRE) {
                    pixels = new int[animationCanvas.length];
                    opaque = Argb.copyPremultiplied(animationCanvas, pixels);
                } else {
                    pixels = animationCanvas.clone();
                }
                frame = frameFromPreparedOwnedPixels(
                        width,
                        height,
                        descriptor.durationMillis(),
                        pixelFormat,
                        pixels,
                        opaque
                );
            } else {
                frame = frameFromOwnedArgb(
                        descriptor.width(),
                        descriptor.height(),
                        descriptor.durationMillis(),
                        frameArgb,
                        pixelFormat
                );
            }

            frameDescriptors[nextFrameIndex] = null;
            nextFrameIndex++;
            return frame;
        } catch (WebPException ex) {
            resetAfterDecodeFailure();
            throw ex;
        }
    }

    /// Decodes the next frame in the requested representation into caller-provided pixel storage.
    ///
    /// The next canvas-width times canvas-height elements beginning at `storage.position()` receive
    /// tightly packed pixels in `pixelFormat`. The storage may be heap-backed or direct and may use
    /// either byte order. On success, its position advances past that region and the returned frame
    /// retains the region without copying. The storage limit is not changed. The caller must not
    /// modify the retained region while the frame remains in use.
    ///
    /// When no frame remains, this method returns `null` without inspecting or changing a non-null
    /// storage buffer's state. If decoding fails, the storage position and reader frame index
    /// remain unchanged, but any pixels already written to the destination region are unspecified.
    ///
    /// @param pixelFormat the stored pixel representation
    /// @param storage the writable pixel storage to retain on successful decode
    /// @return the next frame, or `null` when the stream is exhausted
    /// @throws WebPException if decoding fails
    /// @throws NullPointerException if `pixelFormat` or `storage` is `null`
    /// @throws ReadOnlyBufferException if a frame remains and `storage` is read-only
    /// @throws IllegalArgumentException if a frame remains and the storage has insufficient
    ///                                  remaining elements
    public @Nullable WebPFrame readNextFrame(
            WebPPixelFormat pixelFormat,
            IntBuffer storage
    ) throws WebPException {
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        Objects.requireNonNull(storage, "storage");
        ensureOpen();
        if (nextFrameIndex >= frameDescriptors.length) {
            return null;
        }
        return decodeNextFrame(pixelFormat, storage, framePixelCount());
    }

    /// Decodes one known-to-exist frame into caller-provided storage.
    ///
    /// @param pixelFormat the requested stored pixel representation
    /// @param storage the destination whose current region will be retained by the frame
    /// @param pixelCount the full-canvas pixel count
    /// @return the decoded frame
    /// @throws WebPException if decoding fails
    /// @throws ReadOnlyBufferException if `storage` is read-only
    /// @throws IllegalArgumentException if the storage has insufficient remaining elements
    private WebPFrame decodeNextFrame(
            WebPPixelFormat pixelFormat,
            IntBuffer storage,
            int pixelCount
    ) throws WebPException {
        if (storage.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        if (storage.remaining() < pixelCount) {
            throw new IllegalArgumentException(
                    "Pixel buffer has insufficient remaining elements: "
                            + storage.remaining() + " < " + pixelCount
            );
        }

        int initialPosition = storage.position();
        IntBuffer frameArgb = storage.slice();
        frameArgb.limit(pixelCount);
        ParsedFrameDescriptor descriptor = Objects.requireNonNull(
                frameDescriptors[nextFrameIndex],
                "Frame descriptor already consumed"
        );

        try {
            WebPFrame frame;
            if (animated) {
                int[] decodedArgb = decodeFrameArgb(descriptor);
                compositeAnimatedFrame(descriptor, decodedArgb, pixelCount);
                assert animationCanvas != null;
                boolean opaque = false;
                if (pixelFormat == WebPPixelFormat.INT_ARGB_PRE) {
                    opaque = Argb.copyPremultiplied(animationCanvas, frameArgb);
                } else {
                    frameArgb.put(0, animationCanvas, 0, pixelCount);
                }
                frame = frameFromPreparedCustomPixels(
                        width,
                        height,
                        descriptor.durationMillis(),
                        pixelFormat,
                        frameArgb,
                        opaque
                );
            } else {
                decodeFrameArgb(descriptor, frameArgb);
                frame = frameFromCustomArgb(
                        descriptor.width(),
                        descriptor.height(),
                        descriptor.durationMillis(),
                        pixelFormat,
                        frameArgb,
                        !descriptor.lossless() && descriptor.alphaChunk() == null
                );
            }

            storage.position(initialPosition + pixelCount);
            frameDescriptors[nextFrameIndex] = null;
            nextFrameIndex++;
            return frame;
        } catch (WebPException ex) {
            resetAfterDecodeFailure();
            throw ex;
        }
    }

    /// Creates a heap-backed frame by taking ownership of decoded `ARGB` pixels.
    ///
    /// The array must contain exactly `width * height` pixels. It may be converted in place when
    /// `pixelFormat` requires premultiplication.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds
    /// @param argb tightly packed non-premultiplied pixels to retain
    /// @param pixelFormat the requested stored pixel representation
    /// @return the prepared frame
    static WebPFrame frameFromOwnedArgb(
            int width,
            int height,
            int durationMillis,
            int[] argb,
            WebPPixelFormat pixelFormat
    ) {
        boolean opaque = pixelFormat == WebPPixelFormat.INT_ARGB_PRE && Argb.premultiply(argb);
        return frameFromPreparedOwnedPixels(
                width,
                height,
                durationMillis,
                pixelFormat,
                argb,
                opaque
        );
    }

    /// Creates a heap-backed frame from owned pixels already stored in their final format.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds
    /// @param pixelFormat the stored pixel representation
    /// @param pixels tightly packed pixels to retain
    /// @param opaque whether every stored premultiplied pixel is fully opaque
    /// @return the prepared frame
    private static WebPFrame frameFromPreparedOwnedPixels(
            int width,
            int height,
            int durationMillis,
            WebPPixelFormat pixelFormat,
            int[] pixels,
            boolean opaque
    ) {
        return new WebPFrame(
                width,
                height,
                durationMillis,
                pixelFormat,
                false,
                opaque,
                IntBuffer.wrap(pixels).asReadOnlyBuffer()
        );
    }

    /// Creates a frame by converting and retaining caller-provided pixel storage.
    ///
    /// The buffer must be writable, and its remaining region must contain exactly `width * height`
    /// non-premultiplied pixels. Its position and limit are not changed.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds
    /// @param pixelFormat the requested stored pixel representation
    /// @param pixels the writable pixel region to retain
    /// @param opaque whether every source pixel is known to be fully opaque
    /// @return the prepared frame
    static WebPFrame frameFromCustomArgb(
            int width,
            int height,
            int durationMillis,
            WebPPixelFormat pixelFormat,
            IntBuffer pixels,
            boolean opaque
    ) {
        boolean allOpaque = pixelFormat == WebPPixelFormat.INT_ARGB_PRE && opaque;
        if (pixelFormat == WebPPixelFormat.INT_ARGB_PRE && !opaque) {
            allOpaque = Argb.premultiply(pixels);
        }

        return frameFromPreparedCustomPixels(
                width,
                height,
                durationMillis,
                pixelFormat,
                pixels,
                allOpaque
        );
    }

    /// Creates a frame that retains caller-provided pixels already stored in their final format.
    ///
    /// The buffer's remaining region must contain exactly `width * height` pixels. Its position and
    /// limit are not changed.
    ///
    /// @param width the frame width in pixels
    /// @param height the frame height in pixels
    /// @param durationMillis the display duration in milliseconds
    /// @param pixelFormat the stored pixel representation
    /// @param pixels the prepared pixel region to retain
    /// @param opaque whether every stored premultiplied pixel is fully opaque
    /// @return the prepared frame
    private static WebPFrame frameFromPreparedCustomPixels(
            int width,
            int height,
            int durationMillis,
            WebPPixelFormat pixelFormat,
            IntBuffer pixels,
            boolean opaque
    ) {
        return new WebPFrame(
                width,
                height,
                durationMillis,
                pixelFormat,
                true,
                opaque,
                pixels.slice().asReadOnlyBuffer()
        );
    }

    /// Discards reusable codec state that may contain a partially decoded frame.
    private void resetAfterDecodeFailure() {
        vp8Decoder = null;
        losslessDecoder = null;
    }

    /// Returns the full-canvas pixel count.
    ///
    /// @return the number of pixels required by every presentation frame
    /// @throws WebPException if the canvas dimensions exceed an integer-sized buffer
    private int framePixelCount() throws WebPException {
        try {
            return Math.multiplyExact(width, height);
        } catch (ArithmeticException ex) {
            throw new WebPException("Image dimensions are too large for a pixel buffer", ex);
        }
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
        vp8Decoder = null;
        losslessDecoder = null;
        animationCanvas = null;
        reusableAnimationFrameArgb = null;
        for (int index = nextFrameIndex; index < frameDescriptors.length; index++) {
            frameDescriptors[index] = null;
        }
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
    /// @param pixelCount the full animation-canvas pixel count
    private void compositeAnimatedFrame(ParsedFrameDescriptor descriptor, int[] frameArgb, int pixelCount) {
        if (animationCanvas == null) {
            animationCanvas = new int[pixelCount];
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
                width,
                height,
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
    /// For animations, the returned scratch array may be larger than the current frame region;
    /// only its leading `width * height` elements contain decoded pixels.
    ///
    /// @param descriptor the parsed frame descriptor and encoded payload
    /// @return mutable storage containing the decoded frame-region pixels at index zero
    /// @throws WebPException if VP8, VP8L, or ALPH decoding fails
    private int[] decodeFrameArgb(ParsedFrameDescriptor descriptor) throws WebPException {
        int pixelCount = frameRegionPixelCount(descriptor);
        int[] argb = acquireFrameArgb(pixelCount);
        if (argb.length != pixelCount) {
            IntBuffer output = IntBuffer.wrap(argb);
            output.limit(pixelCount);
            decodeFrameArgb(descriptor, output);
            return argb;
        }

        if (descriptor.lossless()) {
            acquireLosslessDecoder(descriptor.imageChunk()).decodeFrame(
                    descriptor.width(),
                    descriptor.height(),
                    false,
                    argb
            );
            return argb;
        }

        if (descriptor.alphaChunk() != null) {
            byte[] alphaChunk = descriptor.alphaChunk();
            ExtendedWebP.decodeAlpha(
                    alphaChunk,
                    descriptor.width(),
                    descriptor.height(),
                    argb,
                    reusableLosslessDecoderForAlpha(alphaChunk)
            );
            acquireVp8Decoder().decodeRgbPreservingAlpha(descriptor.imageChunk(), argb);
        } else {
            acquireVp8Decoder().decodeArgb(descriptor.imageChunk(), false, argb);
        }
        return argb;
    }

    /// Decodes one raw frame payload directly to tightly packed non-premultiplied `ARGB` pixels.
    ///
    /// @param descriptor the parsed frame descriptor and encoded payload
    /// @param argb the exact-sized destination
    /// @throws WebPException if VP8, VP8L, or ALPH decoding fails
    private void decodeFrameArgb(ParsedFrameDescriptor descriptor, IntBuffer argb) throws WebPException {
        if (descriptor.lossless()) {
            acquireLosslessDecoder(descriptor.imageChunk()).decodeFrame(
                    descriptor.width(),
                    descriptor.height(),
                    false,
                    argb
            );
        } else {
            if (descriptor.alphaChunk() != null) {
                byte[] alphaChunk = descriptor.alphaChunk();
                ExtendedWebP.decodeAlpha(
                        alphaChunk,
                        descriptor.width(),
                        descriptor.height(),
                        argb,
                        reusableLosslessDecoderForAlpha(alphaChunk)
                );
                acquireVp8Decoder().decodeRgbPreservingAlpha(descriptor.imageChunk(), argb);
            } else {
                acquireVp8Decoder().decodeArgb(descriptor.imageChunk(), false, argb);
            }
        }
    }

    /// Returns the validated pixel count of one encoded frame region.
    ///
    /// @param descriptor the parsed frame descriptor
    /// @return the frame-region pixel count
    /// @throws WebPException if the frame dimensions exceed an integer-sized buffer
    private static int frameRegionPixelCount(ParsedFrameDescriptor descriptor) throws WebPException {
        try {
            return Math.multiplyExact(descriptor.width(), descriptor.height());
        } catch (ArithmeticException ex) {
            throw new WebPException("Frame dimensions are too large for a pixel buffer", ex);
        }
    }

    /// Returns the reader-local VP8 decoder, creating it for the first lossy frame.
    ///
    /// @return the reusable VP8 decoder
    private Vp8Decoder acquireVp8Decoder() {
        Vp8Decoder result = vp8Decoder;
        if (result == null) {
            result = new Vp8Decoder();
            vp8Decoder = result;
        }
        return result;
    }

    /// Returns the reader-local VP8L decoder reset to the supplied payload.
    ///
    /// @param input the raw VP8L frame payload
    /// @return the reusable decoder
    private LosslessDecoder acquireLosslessDecoder(byte[] input) {
        LosslessDecoder result = losslessDecoder;
        if (result == null) {
            result = new LosslessDecoder(input);
            losslessDecoder = result;
        } else {
            result.resetInput(input);
        }
        return result;
    }

    /// Returns a reusable decoder only when an ALPH control byte selects VP8L compression.
    ///
    /// The ALPH decoder performs full validation and resets the returned decoder to the compressed
    /// payload range before use.
    ///
    /// @param payload the ALPH chunk payload
    /// @return the reusable decoder, or `null` when no VP8L decoding is required
    private @Nullable LosslessDecoder reusableLosslessDecoderForAlpha(byte[] payload) {
        if (payload.length == 0 || (payload[0] & 0b11) != 1) {
            return null;
        }

        LosslessDecoder result = losslessDecoder;
        if (result == null) {
            result = new LosslessDecoder(payload);
            losslessDecoder = result;
        }
        return result;
    }

    /// Returns frame decode storage, retaining the largest animated scratch allocation seen so far.
    ///
    /// @param length the required pixel count
    /// @return a mutable decode buffer whose length is at least `length`
    private int[] acquireFrameArgb(int length) {
        if (!animated) {
            return new int[length];
        }
        if (reusableAnimationFrameArgb == null || reusableAnimationFrameArgb.length < length) {
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
