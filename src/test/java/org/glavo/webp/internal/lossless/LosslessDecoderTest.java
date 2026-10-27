// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.codec.ParsedFrameDescriptor;
import org.glavo.webp.internal.codec.WebPSequentialParser;
import org.glavo.webp.internal.io.BufferedInput;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests lossless decoder workspace reuse against independently encoded PNG references.
@NotNullByDefault
final class LosslessDecoderTest {
    /// Verifies reuse across changing palettes, transform planes, dimensions, and entropy groups.
    @Test
    void reusesWorkspacesAcrossDifferentImagesAndOutputStorage() throws Exception {
        ArrayList<Fixture> fixtures = new ArrayList<>();
        for (String name : new String[]{
                "yellow_rose", "gopher-doc.8bpp", "gopher-doc.1bpp", "blue-purple-pink-large",
                "gopher-doc.4bpp", "gopher-doc.with-alpha", "gopher-doc.2bpp", "tux", "blue-purple-pink"
        }) {
            fixtures.add(readFixture(name));
        }
        LosslessDecoder decoder = new LosslessDecoder(new byte[0]);
        for (int pass = 0; pass < 3; pass++) {
            for (int index = 0; index < fixtures.size(); index++) {
                Fixture fixture = fixtures.get(pass == 1 ? fixtures.size() - index - 1 : index);
                ParsedFrameDescriptor frame = fixture.frame();
                decoder.resetInput(frame.imageChunk());
                int[] actual = new int[fixture.pixels().length];
                if (pass == 0) {
                    decoder.decodeFrame(frame.width(), frame.height(), false, actual);
                } else {
                    IntBuffer storage = pass == 1
                            ? IntBuffer.allocate(actual.length + 2)
                            : ByteBuffer.allocateDirect((actual.length + 2) * Integer.BYTES)
                                    .order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                    storage.put(0, 0x12345678);
                    storage.put(actual.length + 1, 0x76543210);
                    storage.position(1).limit(actual.length + 1);
                    decoder.decodeFrame(frame.width(), frame.height(), false, storage);
                    assertEquals(1, storage.position());
                    assertEquals(actual.length + 1, storage.limit());
                    storage.get(1, actual);
                    storage.clear();
                    assertEquals(0x12345678, storage.get(0));
                    assertEquals(0x76543210, storage.get(actual.length + 1));
                }
                assertArrayEquals(fixture.pixels(), actual);
            }
        }
    }

    /// Verifies that reset discards a failed frame's state without discarding reusable storage.
    @Test
    void resetsAfterTruncatedFrameWithPopulatedWorkspaces() throws Exception {
        Fixture large = readFixture("yellow_rose");
        Fixture small = readFixture("gopher-doc.1bpp");
        LosslessDecoder decoder = new LosslessDecoder(large.frame().imageChunk());
        int[] pixels = new int[large.pixels().length];
        decoder.decodeFrame(large.frame().width(), large.frame().height(), false, pixels);
        assertArrayEquals(large.pixels(), pixels);

        byte[] truncated = Arrays.copyOf(large.frame().imageChunk(), large.frame().imageChunk().length / 2);
        decoder.resetInput(truncated);
        assertThrows(WebPException.class, () -> decoder.decodeFrame(
                large.frame().width(), large.frame().height(), false, pixels
        ));

        decoder.resetInput(small.frame().imageChunk());
        int[] recovered = new int[small.pixels().length];
        decoder.decodeFrame(small.frame().width(), small.frame().height(), false, recovered);
        assertArrayEquals(small.pixels(), recovered);
    }

    /// Verifies that a smaller palette cannot expose colors retained from an earlier frame.
    @Test
    void clearsUnusedPaletteEntriesWhenPaletteShrinks() throws WebPException {
        LosslessDecoder decoder = new LosslessDecoder(paletteFrame(32, 31));
        int[] pixel = new int[1];
        decoder.decodeFrame(1, 1, false, pixel);
        assertEquals(0x20202020, pixel[0]);

        decoder.resetInput(paletteFrame(17, 31));
        decoder.decodeFrame(1, 1, false, pixel);
        assertEquals(0, pixel[0]);
    }

    /// Encodes a one-pixel VP8L frame with a byte-wide palette and one selected index.
    ///
    /// @param paletteSize the number of encoded palette entries, greater than 16
    /// @param index the palette index stored by the image stream
    /// @return the encoded VP8L payload
    private static byte[] paletteFrame(int paletteSize, int index) {
        StringBuilder bits = new StringBuilder();
        appendBits(bits, 0x2F, 8);
        appendBits(bits, 0, 28); // One-pixel width and height.
        appendBits(bits, 1, 1); // Alpha is used.
        appendBits(bits, 0, 3); // VP8L version.
        appendBits(bits, 1, 1); // A transform follows.
        appendBits(bits, 3, 2); // Color indexing.
        appendBits(bits, paletteSize - 1, 8);
        appendBits(bits, 0, 1); // The palette metadata image has no color cache.
        for (int channel = 0; channel < 5; channel++) {
            appendSingleSymbol(bits, channel == 4 ? 0 : 1);
        }
        // Constant metadata pixels encode channel-wise deltas of one for each palette entry.
        appendBits(bits, 0, 1); // No more transforms.
        appendBits(bits, 0, 1); // The main image has no color cache.
        appendBits(bits, 0, 1); // One Huffman group, with no entropy image.
        for (int symbol : new int[]{index, 0, 0, 255, 0}) {
            appendSingleSymbol(bits, symbol);
        }
        byte[] payload = new byte[(bits.length() + 7) / 8];
        for (int bit = 0; bit < bits.length(); bit++) {
            payload[bit / 8] |= (byte) ((bits.charAt(bit) - '0') << (bit & 7));
        }
        return payload;
    }

    /// Appends a simple single-symbol Huffman tree to a fixture bitstream.
    ///
    /// @param bits the accumulated least-significant-bit-first fields
    /// @param symbol the literal symbol, from zero through 255
    private static void appendSingleSymbol(StringBuilder bits, int symbol) {
        appendBits(bits, 1, 1);
        appendBits(bits, 0, 1);
        appendBits(bits, symbol > 1 ? 1 : 0, 1);
        appendBits(bits, symbol, symbol > 1 ? 8 : 1);
    }

    /// Appends the low bits of a value in VP8L bit order.
    ///
    /// @param bits the destination bit sequence
    /// @param value the field value
    /// @param count the number of bits to append
    private static void appendBits(StringBuilder bits, int value, int count) {
        for (int bit = 0; bit < count; bit++) {
            bits.append((value >>> bit) & 1);
        }
    }

    /// Loads a lossless container and its PNG reference from the pinned Go image corpus.
    ///
    /// @param name the shared resource basename
    /// @return the encoded frame and independent reference pixels
    /// @throws IOException if a resource cannot be read or parsed
    private static Fixture readFixture(String name) throws IOException {
        ParsedFrameDescriptor frame;
        try (InputStream input = openResource(name + ".lossless.webp");
             BufferedInput buffered = new BufferedInput.OfInputStream(input)) {
            frame = WebPSequentialParser.parse(buffered).frames().get(0);
        }
        BufferedImage png;
        try (InputStream input = openResource(name + ".png")) {
            png = Objects.requireNonNull(ImageIO.read(input));
        }
        return new Fixture(frame, png.getRGB(0, 0, png.getWidth(), png.getHeight(), null, 0, png.getWidth()));
    }

    /// Opens one required test resource.
    ///
    /// @param name the resource filename
    /// @return the open stream, which the caller must close
    private static InputStream openResource(String name) {
        return Objects.requireNonNull(LosslessDecoderTest.class.getResourceAsStream("/go-image-webp-test-data/" + name));
    }

    /// Holds a static frame and its reference pixels.
    ///
    /// @param frame the parsed encoded frame
    /// @param pixels immutable non-premultiplied reference pixels
    @NotNullByDefault
    private record Fixture(ParsedFrameDescriptor frame, int @Unmodifiable [] pixels) {
    }
}
