package org.glavo.javafx.webp.internal;

import org.glavo.javafx.webp.LoopCount;
import org.glavo.javafx.webp.WebPException;
import org.glavo.javafx.webp.WebPMetadata;

import javax.imageio.stream.ImageInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Forward parser for RIFF WebP container data.
 *
 * <p>The parser extracts only the information needed by the public API: intrinsic size, animation
 * timing, chunk metadata and codec flavor. Decoding of actual pixel data is delegated to the
 * ImageIO WebP reader backend.
 */
public final class WebPContainerParser {

    private static final int FLAG_ANIMATION = 1 << 1;
    private static final int FLAG_XMP = 1 << 2;
    private static final int FLAG_EXIF = 1 << 3;
    private static final int FLAG_ALPHA = 1 << 4;
    private static final int FLAG_ICC = 1 << 5;

    private WebPContainerParser() {
    }

    /**
     * Parses a WebP container from the current beginning of the supplied input stream.
     *
     * @param input the image input stream positioned at the beginning of a WebP RIFF stream
     * @return the parsed container summary
     * @throws IOException if the stream is truncated or malformed
     */
    public static WebPContainerInfo parse(ImageInputStream input) throws IOException {
        input.seek(0);

        String riff = readFourCc(input);
        if (!"RIFF".equals(riff)) {
            throw new WebPException("Missing RIFF container header");
        }

        long riffSize = Integer.toUnsignedLong(readLittleEndianInt(input));
        String webp = readFourCc(input);
        if (!"WEBP".equals(webp)) {
            throw new WebPException("Missing WEBP signature");
        }

        long fileEnd = 8L + riffSize;
        ChunkHeader firstChunk = readChunkHeader(input);
        switch (firstChunk.fourCc()) {
            case "VP8 ":
                return parseSimpleVp8(input, firstChunk);
            case "VP8L":
                return parseSimpleVp8L(input, firstChunk);
            case "VP8X":
                return parseExtended(input, firstChunk, fileEnd);
            default:
                throw new WebPException("Unsupported first WebP chunk: " + firstChunk.fourCc());
        }
    }

    private static WebPContainerInfo parseSimpleVp8(ImageInputStream input, ChunkHeader chunk) throws IOException {
        if (chunk.size() < 10) {
            throw new WebPException("VP8 chunk is too small to contain a valid frame header");
        }

        input.readUnsignedByte();
        input.readUnsignedByte();
        input.readUnsignedByte();

        byte[] signature = new byte[3];
        input.readFully(signature);
        if (signature[0] != (byte) 0x9D || signature[1] != 0x01 || signature[2] != 0x2A) {
            throw new WebPException("Invalid VP8 frame signature");
        }

        int width = readLittleEndianShort(input) & 0x3FFF;
        int height = readLittleEndianShort(input) & 0x3FFF;
        return new WebPContainerInfo(
                width,
                height,
                false,
                false,
                true,
                LoopCount.of(1),
                0,
                WebPMetadata.empty(),
                List.of(new WebPFrameInfo(0))
        );
    }

    private static WebPContainerInfo parseSimpleVp8L(ImageInputStream input, ChunkHeader chunk) throws IOException {
        if (chunk.size() < 5) {
            throw new WebPException("VP8L chunk is too small to contain a valid header");
        }

        int signature = input.readUnsignedByte();
        if (signature != 0x2F) {
            throw new WebPException("Invalid VP8L signature");
        }

        long packed = Integer.toUnsignedLong(readLittleEndianInt(input));
        int width = (int) (packed & 0x3FFF) + 1;
        int height = (int) ((packed >>> 14) & 0x3FFF) + 1;
        boolean alpha = ((packed >>> 28) & 0x1) != 0;
        return new WebPContainerInfo(
                width,
                height,
                alpha,
                false,
                false,
                LoopCount.of(1),
                0,
                WebPMetadata.empty(),
                List.of(new WebPFrameInfo(0))
        );
    }

    private static WebPContainerInfo parseExtended(ImageInputStream input, ChunkHeader chunk, long fileEnd) throws IOException {
        if (chunk.size() < 10) {
            throw new WebPException("VP8X chunk is too small");
        }

        int flags = input.readUnsignedByte();
        skipFully(input, 3);
        int width = readUInt24(input) + 1;
        int height = readUInt24(input) + 1;

        boolean alpha = (flags & FLAG_ALPHA) != 0;
        boolean animated = (flags & FLAG_ANIMATION) != 0;
        boolean lossy = false;
        LoopCount loopCount = LoopCount.of(1);
        long loopDurationMillis = 0;
        byte[] icc = null;
        byte[] exif = null;
        byte[] xmp = null;
        List<WebPFrameInfo> frames = new ArrayList<>();

        skipPadding(input, chunk.size());

        while (input.getStreamPosition() + 8 <= fileEnd) {
            ChunkHeader current = readChunkHeader(input);
            long dataStart = input.getStreamPosition();

            switch (current.fourCc()) {
                case "ICCP" -> icc = readChunkBytes(input, current.size());
                case "EXIF" -> exif = readChunkBytes(input, current.size());
                case "XMP " -> xmp = readChunkBytes(input, current.size());
                case "VP8 " -> {
                    lossy = true;
                    skipFully(input, current.size());
                    if (frames.isEmpty()) {
                        frames.add(new WebPFrameInfo(0));
                    }
                }
                case "VP8L" -> {
                    skipFully(input, current.size());
                    if (frames.isEmpty()) {
                        frames.add(new WebPFrameInfo(0));
                    }
                }
                case "ANIM" -> {
                    if (current.size() < 6) {
                        throw new WebPException("ANIM chunk is too small");
                    }
                    skipFully(input, 4);
                    int rawLoopCount = readLittleEndianShort(input);
                    loopCount = rawLoopCount == 0 ? LoopCount.forever() : LoopCount.of(rawLoopCount);
                    skipFully(input, current.size() - 6L);
                }
                case "ANMF" -> {
                    if (current.size() < 16) {
                        throw new WebPException("ANMF chunk is too small");
                    }

                    skipFully(input, 12);
                    int duration = readUInt24(input);
                    int frameFlags = input.readUnsignedByte();
                    frames.add(new WebPFrameInfo(duration));
                    loopDurationMillis += duration;

                    long framePayloadSize = current.size() - 16L;
                    long framePayloadStart = input.getStreamPosition();
                    while (input.getStreamPosition() + 8 <= framePayloadStart + framePayloadSize) {
                        ChunkHeader subChunk = readChunkHeader(input);
                        if ("VP8 ".equals(subChunk.fourCc()) || "ALPH".equals(subChunk.fourCc())) {
                            lossy = true;
                        }
                        skipFully(input, subChunk.paddedSize());
                    }
                }
                default -> skipFully(input, current.size());
            }

            long consumed = input.getStreamPosition() - dataStart;
            long remaining = current.paddedSize() - consumed;
            if (remaining > 0) {
                skipFully(input, remaining);
            }
        }

        if (frames.isEmpty()) {
            frames = List.of(new WebPFrameInfo(0));
        }

        // Flags are advisory, but if the container advertises metadata presence we preserve the raw
        // chunks that were actually found instead of synthesizing values.
        if ((flags & FLAG_ICC) == 0) {
            icc = null;
        }
        if ((flags & FLAG_EXIF) == 0) {
            exif = null;
        }
        if ((flags & FLAG_XMP) == 0) {
            xmp = null;
        }

        return new WebPContainerInfo(
                width,
                height,
                alpha,
                animated,
                lossy,
                loopCount,
                animated ? loopDurationMillis : 0,
                new WebPMetadata(icc, exif, xmp),
                List.copyOf(frames)
        );
    }

    private static ChunkHeader readChunkHeader(ImageInputStream input) throws IOException {
        String fourCc = readFourCc(input);
        long size = Integer.toUnsignedLong(readLittleEndianInt(input));
        return new ChunkHeader(fourCc, size);
    }

    private static byte[] readChunkBytes(ImageInputStream input, long size) throws IOException {
        if (size > Integer.MAX_VALUE) {
            throw new WebPException("Chunk is too large to buffer in memory: " + size);
        }
        byte[] data = new byte[(int) size];
        input.readFully(data);
        return data;
    }

    private static String readFourCc(ImageInputStream input) throws IOException {
        byte[] bytes = new byte[4];
        input.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int readLittleEndianShort(ImageInputStream input) throws IOException {
        int b0 = input.readUnsignedByte();
        int b1 = input.readUnsignedByte();
        return b0 | (b1 << 8);
    }

    private static int readLittleEndianInt(ImageInputStream input) throws IOException {
        int b0 = input.readUnsignedByte();
        int b1 = input.readUnsignedByte();
        int b2 = input.readUnsignedByte();
        int b3 = input.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int readUInt24(ImageInputStream input) throws IOException {
        int b0 = input.readUnsignedByte();
        int b1 = input.readUnsignedByte();
        int b2 = input.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16);
    }

    private static void skipPadding(ImageInputStream input, long originalChunkSize) throws IOException {
        if ((originalChunkSize & 1L) != 0L) {
            skipFully(input, 1);
        }
    }

    private static void skipFully(ImageInputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            int skipped = input.skipBytes((int) Math.min(Integer.MAX_VALUE, remaining));
            if (skipped <= 0) {
                throw new EOFException("Unexpected end of stream while skipping " + bytes + " bytes");
            }
            remaining -= skipped;
        }
    }

    private record ChunkHeader(String fourCc, long size) {
        long paddedSize() {
            return (size & 1L) == 0L ? size : size + 1L;
        }
    }
}
