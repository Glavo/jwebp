// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;
import org.glavo.webp.WebPMetadata;
import org.glavo.webp.internal.io.BufferedInput;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
        int riff = input.readFourCC();
        if (FourCC.RIFF != riff) {
            throw new WebPException("Missing RIFF container header");
        }

        long riffSize = input.readUnsignedIntLE();
        int webp = input.readFourCC();
        if (FourCC.WEBP != webp) {
            throw new WebPException("Missing WEBP signature");
        }

        long remainingBytes = riffSize - 4;
        if (remainingBytes < 8) {
            throw new WebPException("Invalid RIFF size for WebP container");
        }

        ChunkPayload first = readChunk(input);
        remainingBytes -= 8L + first.paddedSize();

        return switch (first.type()) {
            case FourCC.VP8 -> parseSimpleVp8(first.payload());
            case FourCC.VP8L -> parseSimpleVp8L(first.payload());
            case FourCC.VP8X -> parseExtended(input, remainingBytes, first.payload());
            default -> throw new WebPException("Unsupported first WebP chunk: " + FourCC.toString(first.type()));
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

        int flags = Byte.toUnsignedInt(payload[0]);
        int canvasWidth = readUnsignedInt24LE(payload, 4) + 1;
        int canvasHeight = readUnsignedInt24LE(payload, 7) + 1;

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
            if (remainingBytes < 8) {
                throw new WebPException("Truncated WebP chunk header");
            }

            int type = input.readFourCC();
            long chunkSize = input.readUnsignedIntLE();
            if (chunkSize > Integer.MAX_VALUE) {
                throw new WebPException("Chunk is too large to buffer in memory: " + chunkSize);
            }

            long paddedChunkSize = paddedSize(chunkSize);
            if (paddedChunkSize > remainingBytes - 8) {
                throw new WebPException("WebP chunk extends beyond the RIFF container");
            }
            remainingBytes -= 8L + paddedChunkSize;

            if (type == FourCC.ANMF) {
                ParsedFrameDescriptor descriptor = parseAnimationFrame(
                        input,
                        (int) chunkSize,
                        canvasWidth,
                        canvasHeight
                );
                if ((chunkSize & 1L) != 0L) {
                    input.skip(1);
                }
                frames.add(descriptor);
                loopDurationMillis += descriptor.durationMillis();
                lossy |= !descriptor.lossless() || descriptor.alphaChunk() != null;
                continue;
            }

            byte[] chunkPayload = input.readByteArray((int) chunkSize);
            if ((chunkSize & 1L) != 0L) {
                input.skip(1);
            }

            switch (type) {
                case FourCC.VP8X ->
                        throw new WebPException("VP8X chunk must be the first chunk in the WebP container");
                case FourCC.ICCP -> iccProfile = chunkPayload;
                case FourCC.EXIF -> exifMetadata = chunkPayload;
                case FourCC.XMP -> xmpMetadata = chunkPayload;
                case FourCC.ANIM -> {
                    if (chunkPayload.length < 6) {
                        throw new WebPException("ANIM chunk is too small");
                    }
                    backgroundColorHint = Arrays.copyOf(chunkPayload, 4);
                    loopCount = readUnsignedShortLE(chunkPayload, 4);
                }
                case FourCC.ALPH -> {
                    if (alpha) {
                        pendingAlphaChunk = chunkPayload;
                    }
                }
                case FourCC.VP8 -> {
                    Dimensions dimensions = parseVp8Dimensions(chunkPayload);
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
                            chunkPayload
                    ));
                    pendingAlphaChunk = null;
                    lossy = true;
                }
                case FourCC.VP8L -> {
                    LosslessHeader losslessHeader = parseVp8LHeader(chunkPayload);
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
                            chunkPayload
                    ));
                    pendingAlphaChunk = null;
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

    /// Parses an ANMF payload directly from the container without retaining an outer payload copy.
    ///
    /// @param frame the container input positioned at the first ANMF payload byte
    /// @param payloadSize the ANMF payload size, excluding outer RIFF padding
    /// @param canvasWidth the animation canvas width
    /// @param canvasHeight the animation canvas height
    /// @return the parsed frame descriptor
    /// @throws IOException if the payload is truncated or malformed
    private static ParsedFrameDescriptor parseAnimationFrame(
            BufferedInput frame,
            int payloadSize,
            int canvasWidth,
            int canvasHeight
    ) throws IOException {
        if (payloadSize < 16) {
            throw new WebPException("ANMF chunk is too small");
        }

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
        long remainingBytes = payloadSize - 16L;
        while (remainingBytes > 0) {
            if (remainingBytes < 8) {
                throw new WebPException("Truncated animated frame chunk header");
            }

            int type = frame.readFourCC();
            long chunkSize = frame.readUnsignedIntLE();
            if (chunkSize > Integer.MAX_VALUE) {
                throw new WebPException("Animated frame chunk is too large to buffer");
            }

            long paddedChunkSize = paddedSize(chunkSize);
            if (paddedChunkSize > remainingBytes - 8) {
                throw new WebPException("Animated frame chunk extends beyond the ANMF payload");
            }
            remainingBytes -= 8L + paddedChunkSize;

            byte[] chunkPayload = frame.readByteArray((int) chunkSize);
            if ((chunkSize & 1L) != 0L) {
                frame.skip(1);
            }

            switch (type) {
                case FourCC.ALPH -> alphaChunk = chunkPayload;
                case FourCC.VP8 -> {
                    Dimensions dimensions = parseVp8Dimensions(chunkPayload);
                    if (dimensions.width() != frameWidth || dimensions.height() != frameHeight) {
                        throw new WebPException("Animated VP8 frame dimensions do not match the ANMF header");
                    }
                    imageChunk = chunkPayload;
                    lossless = false;
                }
                case FourCC.VP8L -> {
                    LosslessHeader header = parseVp8LHeader(chunkPayload);
                    if (header.width() != frameWidth || header.height() != frameHeight) {
                        throw new WebPException("Animated VP8L frame dimensions do not match the ANMF header");
                    }
                    imageChunk = chunkPayload;
                    lossless = true;
                }
                default -> {
                }
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

    /// Returns a RIFF payload size including its optional alignment byte.
    ///
    /// @param size the declared payload size
    /// @return the payload size rounded up to an even byte count
    private static long paddedSize(long size) {
        return (size & 1L) == 0L ? size : size + 1L;
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

        int tag = readUnsignedInt24LE(payload, 0);
        if ((tag & 1) != 0) {
            throw new WebPException("Only VP8 keyframes are supported");
        }

        if (Byte.toUnsignedInt(payload[3]) != 0x9D
                || Byte.toUnsignedInt(payload[4]) != 0x01
                || Byte.toUnsignedInt(payload[5]) != 0x2A) {
            throw new WebPException("Invalid VP8 frame signature");
        }

        int width = readUnsignedShortLE(payload, 6) & 0x3FFF;
        int height = readUnsignedShortLE(payload, 8) & 0x3FFF;
        return new Dimensions(width, height);
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

        int signature = Byte.toUnsignedInt(payload[0]);
        if (signature != 0x2F) {
            throw new WebPException("Invalid VP8L signature");
        }
        long bits = readUnsignedIntLE(payload, 1);
        int width = (int) (bits & 0x3FFF) + 1;
        int height = (int) ((bits >>> 14) & 0x3FFF) + 1;
        boolean alphaUsed = ((bits >>> 28) & 1) != 0;
        int version = (int) ((bits >>> 29) & 0x7);
        if (version != 0) {
            throw new WebPException("Unsupported VP8L version: " + version);
        }
        return new LosslessHeader(width, height, alphaUsed);
    }

    /// Reads an unsigned little-endian 16-bit integer from a validated array range.
    ///
    /// @param data the source bytes
    /// @param offset the first source byte
    /// @return the decoded unsigned value
    private static int readUnsignedShortLE(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8);
    }

    /// Reads an unsigned little-endian 24-bit integer from a validated array range.
    ///
    /// @param data the source bytes
    /// @param offset the first source byte
    /// @return the decoded unsigned value
    private static int readUnsignedInt24LE(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16);
    }

    /// Reads an unsigned little-endian 32-bit integer from a validated array range.
    ///
    /// @param data the source bytes
    /// @param offset the first source byte
    /// @return the decoded unsigned value
    private static long readUnsignedIntLE(byte[] data, int offset) {
        return Integer.toUnsignedLong(
                Byte.toUnsignedInt(data[offset])
                        | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                        | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                        | (data[offset + 3] << 24)
        );
    }

    private static ChunkPayload readChunk(BufferedInput input) throws IOException {
        int fourCc = input.readFourCC();
        long size = input.readUnsignedIntLE();
        if (size > Integer.MAX_VALUE) {
            throw new WebPException("Chunk is too large to buffer in memory: " + size);
        }
        byte[] payload = input.readByteArray((int) size);
        if ((size & 1L) != 0L) {
            input.skip(1);
        }
        return new ChunkPayload(fourCc, payload, size);
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

    /// A buffered RIFF chunk and its declared unpadded size.
    ///
    /// @param type the packed FourCC chunk identifier
    /// @param payload the chunk payload without its alignment byte
    /// @param size the declared payload size in bytes
    @NotNullByDefault
    private record ChunkPayload(int type, byte[] payload, long size) {
        /// Returns the payload size including its optional alignment byte.
        ///
        /// @return the payload size rounded up to an even byte count
        long paddedSize() {
            return WebPSequentialParser.paddedSize(size);
        }
    }
}
