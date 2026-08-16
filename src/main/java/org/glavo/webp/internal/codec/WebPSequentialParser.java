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
package org.glavo.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;
import org.glavo.webp.WebPMetadata;
import org.glavo.webp.internal.io.BufferedInput;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/// Sequential WebP container parser that buffers encoded frame payloads but does not rely on
/// seeking or desktop APIs.
///
/// The parser consumes a forward-only stream and extracts frame descriptors, metadata and
/// animation timing. Actual pixel decoding is delegated to the VP8L or VP8 codecs.
@NotNullByDefault
public final class WebPSequentialParser {

    private static final int FLAG_ANIMATION = 1 << 1;
    private static final int FLAG_XMP = 1 << 2;
    private static final int FLAG_EXIF = 1 << 3;
    private static final int FLAG_ALPHA = 1 << 4;
    private static final int FLAG_ICC = 1 << 5;

    private WebPSequentialParser() {
    }

    /// Parses a WebP container from a forward-only buffered input.
    ///
    /// @param input the WebP byte stream abstraction
    /// @return the parsed container data
    /// @throws IOException if the stream is truncated or malformed
    public static ParsedWebPImage parse(BufferedInput input) throws IOException {
        FourCC riff = input.readFourCC();
        if (!WebPRiffChunk.RIFF.fourCC().equals(riff)) {
            throw new WebPException("Missing RIFF container header");
        }

        long riffSize = input.readUnsignedIntLE();
        FourCC webp = input.readFourCC();
        if (!WebPRiffChunk.WEBP.fourCC().equals(webp)) {
            throw new WebPException("Missing WEBP signature");
        }

        long remainingBytes = riffSize - 4;
        if (remainingBytes < 8) {
            throw new WebPException("Invalid RIFF size for WebP container");
        }

        ChunkPayload first = readChunk(input);
        remainingBytes -= 8L + first.paddedSize();

        return switch (first.type()) {
            case VP8 -> parseSimpleVp8(first.payload());
            case VP8L -> parseSimpleVp8L(first.payload());
            case VP8X -> parseExtended(input, remainingBytes, first.payload());
            default -> throw new WebPException("Unsupported first WebP chunk: " + first.fourCc());
        };
    }

    private static ParsedWebPImage parseSimpleVp8(byte[] payload) throws WebPException {
        Dimensions dimensions = parseVp8Dimensions(payload);
        return new ParsedWebPImage(
                dimensions.width(),
                dimensions.height(),
                false,
                false,
                true,
                1,
                0,
                WebPMetadata.empty(),
                null,
                List.of(new ParsedFrameDescriptor(
                        0,
                        0,
                        dimensions.width(),
                        dimensions.height(),
                        0,
                        false,
                        true,
                        false,
                        null,
                        payload
                ))
        );
    }

    private static ParsedWebPImage parseSimpleVp8L(byte[] payload) throws WebPException {
        LosslessHeader header = parseVp8LHeader(payload);
        return new ParsedWebPImage(
                header.width(),
                header.height(),
                header.alphaUsed(),
                false,
                false,
                1,
                0,
                WebPMetadata.empty(),
                null,
                List.of(new ParsedFrameDescriptor(
                        0,
                        0,
                        header.width(),
                        header.height(),
                        0,
                        false,
                        true,
                        true,
                        null,
                        payload
                ))
        );
    }

    private static ParsedWebPImage parseExtended(BufferedInput input, long remainingBytes, byte[] payload) throws IOException {
        if (payload.length < 10) {
            throw new WebPException("VP8X chunk is too small");
        }

        BufferedInput.OfByteBuffer header = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(payload));
        int flags = header.readUnsignedByte();
        header.skip(3);
        int canvasWidth = header.readUnsignedInt24LE() + 1;
        int canvasHeight = header.readUnsignedInt24LE() + 1;

        boolean animated = (flags & FLAG_ANIMATION) != 0;
        boolean alpha = (flags & FLAG_ALPHA) != 0;
        boolean lossy = false;
        byte @Nullable [] iccProfile = null;
        byte @Nullable [] exifMetadata = null;
        byte @Nullable [] xmpMetadata = null;
        byte @Nullable [] backgroundColorHint = null;
        int loopCount = 1;
        long loopDurationMillis = 0;
        List<ParsedFrameDescriptor> frames = new ArrayList<>();
        byte @Nullable [] pendingAlphaChunk = null;

        while (remainingBytes > 0) {
            ChunkPayload chunk = readChunk(input);
            remainingBytes -= 8L + chunk.paddedSize();

            switch (chunk.type()) {
                case VP8X -> throw new WebPException("VP8X chunk must be the first chunk in the WebP container");
                case ICCP -> iccProfile = chunk.payload();
                case EXIF -> exifMetadata = chunk.payload();
                case XMP -> xmpMetadata = chunk.payload();
                case ANIM -> {
                    if (chunk.payload().length < 6) {
                        throw new WebPException("ANIM chunk is too small");
                    }
                    BufferedInput.OfByteBuffer reader = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(chunk.payload()));
                    backgroundColorHint = reader.readByteArray(4);
                    loopCount = reader.readUnsignedShortLE();
                }
                case ALPH -> {
                    if (alpha) {
                        pendingAlphaChunk = chunk.payload();
                    }
                }
                case VP8 -> {
                    Dimensions dimensions = parseVp8Dimensions(chunk.payload());
                    frames.add(new ParsedFrameDescriptor(
                            0,
                            0,
                            dimensions.width(),
                            dimensions.height(),
                            0,
                            false,
                            true,
                            false,
                            pendingAlphaChunk,
                            chunk.payload()
                    ));
                    pendingAlphaChunk = null;
                    lossy = true;
                }
                case VP8L -> {
                    LosslessHeader losslessHeader = parseVp8LHeader(chunk.payload());
                    frames.add(new ParsedFrameDescriptor(
                            0,
                            0,
                            losslessHeader.width(),
                            losslessHeader.height(),
                            0,
                            false,
                            true,
                            true,
                            null,
                            chunk.payload()
                    ));
                    pendingAlphaChunk = null;
                }
                case ANMF -> {
                    ParsedFrameDescriptor descriptor = parseAnimationFrame(chunk.payload(), canvasWidth, canvasHeight);
                    frames.add(descriptor);
                    loopDurationMillis += descriptor.durationMillis();
                    lossy |= !descriptor.lossless() || descriptor.alphaChunk() != null;
                }
                default -> {
                }
            }
        }

        if (frames.isEmpty()) {
            throw new WebPException("WebP container did not contain any decodable frame chunks");
        }

        if (!animated) {
            loopDurationMillis = 0;
        }

        if ((flags & FLAG_ICC) == 0) {
            iccProfile = null;
        }
        if ((flags & FLAG_EXIF) == 0) {
            exifMetadata = null;
        }
        if ((flags & FLAG_XMP) == 0) {
            xmpMetadata = null;
        }

        return new ParsedWebPImage(
                canvasWidth,
                canvasHeight,
                alpha,
                animated,
                lossy,
                loopCount,
                loopDurationMillis,
                new WebPMetadata(iccProfile, exifMetadata, xmpMetadata),
                backgroundColorHint,
                List.copyOf(frames)
        );
    }

    private static ParsedFrameDescriptor parseAnimationFrame(byte[] payload, int canvasWidth, int canvasHeight) throws IOException {
        if (payload.length < 16) {
            throw new WebPException("ANMF chunk is too small");
        }

        var frame = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(payload));
        int frameX = frame.readUnsignedInt24LE() * 2;
        int frameY = frame.readUnsignedInt24LE() * 2;
        int frameWidth = frame.readUnsignedInt24LE() + 1;
        int frameHeight = frame.readUnsignedInt24LE() + 1;
        int durationMillis = frame.readUnsignedInt24LE();
        int frameInfo = frame.readUnsignedByte();
        boolean useAlphaBlending = (frameInfo & 0b10) == 0;
        boolean disposeToBackground = (frameInfo & 0b1) != 0;

        if (frameWidth > 16384 || frameHeight > 16384) {
            throw new WebPException("Animated frame dimensions are too large");
        }
        if (frameX + frameWidth > canvasWidth || frameY + frameHeight > canvasHeight) {
            throw new WebPException("Animated frame lies outside the canvas");
        }

        byte @Nullable [] alphaChunk = null;
        byte @Nullable [] imageChunk = null;
        boolean lossless = false;
        while (frame.remaining() > 0) {
            FourCC fourCc = frame.readFourCC();
            WebPRiffChunk type = WebPRiffChunk.fromFourCC(fourCc);
            long chunkSize = frame.readUnsignedIntLE();
            if (chunkSize > Integer.MAX_VALUE) {
                throw new WebPException("Animated frame chunk is too large to buffer");
            }
            byte[] chunkPayload = frame.readByteArray((int) chunkSize);
            if ((chunkSize & 1L) != 0L && frame.remaining() > 0) {
                frame.skip(1);
            }

            if (type == WebPRiffChunk.ALPH) {
                alphaChunk = chunkPayload;
            } else if (type == WebPRiffChunk.VP8) {
                Dimensions dimensions = parseVp8Dimensions(chunkPayload);
                if (dimensions.width() != frameWidth || dimensions.height() != frameHeight) {
                    throw new WebPException("Animated VP8 frame dimensions do not match the ANMF header");
                }
                imageChunk = chunkPayload;
                lossless = false;
            } else if (type == WebPRiffChunk.VP8L) {
                LosslessHeader header = parseVp8LHeader(chunkPayload);
                if (header.width() != frameWidth || header.height() != frameHeight) {
                    throw new WebPException("Animated VP8L frame dimensions do not match the ANMF header");
                }
                imageChunk = chunkPayload;
                lossless = true;
            }
        }

        if (imageChunk == null) {
            throw new WebPException("ANMF chunk is missing VP8/VP8L image data");
        }

        return new ParsedFrameDescriptor(
                frameX,
                frameY,
                frameWidth,
                frameHeight,
                durationMillis,
                useAlphaBlending,
                disposeToBackground,
                lossless,
                alphaChunk,
                imageChunk
        );
    }

    /// Parses dimensions from a VP8 keyframe chunk.
    ///
    /// @param payload the raw VP8 chunk payload
    /// @return the decoded frame dimensions
    /// @throws WebPException if the payload is truncated or malformed
    public static Dimensions parseVp8Dimensions(byte[] payload) throws WebPException {
        if (payload.length < 10) {
            throw new WebPException("VP8 chunk is too small to contain a frame header");
        }

        try {
            BufferedInput.OfByteBuffer reader = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(payload));
            int tag = reader.readUnsignedInt24LE();
            if ((tag & 1) != 0) {
                throw new WebPException("Only VP8 keyframes are supported");
            }

            int signature0 = reader.readUnsignedByte();
            int signature1 = reader.readUnsignedByte();
            int signature2 = reader.readUnsignedByte();
            if (signature0 != 0x9D || signature1 != 0x01 || signature2 != 0x2A) {
                throw new WebPException("Invalid VP8 frame signature");
            }

            int width = reader.readUnsignedShortLE() & 0x3FFF;
            int height = reader.readUnsignedShortLE() & 0x3FFF;
            return new Dimensions(width, height);
        } catch (IOException ex) {
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            throw new WebPException("Failed to parse VP8 frame dimensions", ex);
        }
    }

    /// Parses the VP8L chunk header.
    ///
    /// @param payload the raw VP8L chunk payload
    /// @return the decoded VP8L header
    /// @throws WebPException if the payload is truncated or malformed
    public static LosslessHeader parseVp8LHeader(byte[] payload) throws WebPException {
        if (payload.length < 5) {
            throw new WebPException("VP8L chunk is too small to contain a frame header");
        }

        try {
            BufferedInput.OfByteBuffer reader = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(payload));
            int signature = reader.readUnsignedByte();
            if (signature != 0x2F) {
                throw new WebPException("Invalid VP8L signature");
            }
            long bits = reader.readUnsignedIntLE();
            int width = (int) (bits & 0x3FFF) + 1;
            int height = (int) ((bits >>> 14) & 0x3FFF) + 1;
            boolean alphaUsed = ((bits >>> 28) & 1) != 0;
            int version = (int) ((bits >>> 29) & 0x7);
            if (version != 0) {
                throw new WebPException("Unsupported VP8L version: " + version);
            }
            return new LosslessHeader(width, height, alphaUsed);
        } catch (IOException ex) {
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            throw new WebPException("Failed to parse VP8L frame header", ex);
        }
    }

    private static ChunkPayload readChunk(BufferedInput input) throws IOException {
        FourCC fourCc = input.readFourCC();
        long size = input.readUnsignedIntLE();
        if (size > Integer.MAX_VALUE) {
            throw new WebPException("Chunk is too large to buffer in memory: " + size);
        }
        byte[] payload = input.readByteArray((int) size);
        if ((size & 1L) != 0L) {
            input.skip(1);
        }
        return new ChunkPayload(fourCc, WebPRiffChunk.fromFourCC(fourCc), payload, size);
    }

    /// Parsed VP8 dimensions.
    ///
    /// @param width  the frame width
    /// @param height the frame height
    @NotNullByDefault
    public record Dimensions(int width, int height) {
    }

    /// Parsed VP8L header data.
    ///
    /// @param width     the frame width
    /// @param height    the frame height
    /// @param alphaUsed whether the VP8L bitstream declares an alpha channel
    @NotNullByDefault
    public record LosslessHeader(int width, int height, boolean alphaUsed) {
    }

    @NotNullByDefault
    private record ChunkPayload(FourCC fourCc, WebPRiffChunk type, byte[] payload, long size) {
        long paddedSize() {
            return (size & 1L) == 0L ? size : size + 1L;
        }
    }
}
