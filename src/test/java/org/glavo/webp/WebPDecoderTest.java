// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests immutable decoder configuration and frame output representations.
@NotNullByDefault
final class WebPDecoderTest {

    /// Verifies that `with` methods preserve the receiver and reuse unchanged configurations.
    @Test
    void withMethodsCreateImmutableConfigurations() {
        WebPDecoder defaults = WebPDecoder.DEFAULT;
        WebPDecoder premultiplied = defaults.withPixelFormat(WebPPixelFormat.INT_ARGB_PRE);
        WebPDecoder direct = premultiplied.withDirect(true);

        assertSame(defaults, defaults.withPixelFormat(WebPPixelFormat.INT_ARGB));
        assertSame(defaults, defaults.withDirect(false));
        assertNotSame(defaults, premultiplied);
        assertNotSame(premultiplied, direct);

        assertEquals(WebPPixelFormat.INT_ARGB, defaults.getPixelFormat());
        assertFalse(defaults.isDirect());
        assertEquals(WebPPixelFormat.INT_ARGB_PRE, premultiplied.getPixelFormat());
        assertFalse(premultiplied.isDirect());
        assertEquals(WebPPixelFormat.INT_ARGB_PRE, direct.getPixelFormat());
        assertTrue(direct.isDirect());
    }

    /// Verifies every explicit format and storage combination without involving codec tolerances.
    @Test
    void createsConfiguredFrameRepresentations() {
        int[] argb = {
                0x0001_0203,
                0x80FF_8040,
                0xFF11_2233,
                0x4000_FF80
        };

        for (WebPPixelFormat pixelFormat : WebPPixelFormat.values()) {
            for (boolean direct : new boolean[]{false, true}) {
                WebPDecoder decoder = WebPDecoder.DEFAULT
                        .withPixelFormat(pixelFormat)
                        .withDirect(direct);
                WebPFrame frame = decoder.createFrame(2, 2, 17, argb, true);

                assertEquals(pixelFormat, frame.getPixelFormat());
                assertEquals(direct, frame.getPixels().isDirect());
                assertFalse(frame.usesCustomPixelBuffer());
                assertTrue(frame.getPixels().isReadOnly());
                assertEquals(0, frame.getPixels().position());
                assertEquals(argb.length, frame.getPixels().remaining());

                int[] expectedStored = argb.clone();
                int[] expectedArgb = argb.clone();
                if (pixelFormat == WebPPixelFormat.INT_ARGB_PRE) {
                    for (int index = 0; index < argb.length; index++) {
                        expectedStored[index] = Argb.premultiply(argb[index]);
                        expectedArgb[index] = Argb.unpremultiply(expectedStored[index]);
                    }
                }

                IntBuffer stored = frame.getPixels();
                int[] actualStored = new int[stored.remaining()];
                stored.get(actualStored);
                assertArrayEquals(expectedStored, actualStored);
                assertArrayEquals(expectedArgb, frame.getArgbArray());
                assertEquals(expectedArgb[3], frame.getArgb(1, 1));
            }
        }
    }

    /// Verifies that caller-provided heap and direct buffers are retained for individual frames.
    @Test
    void readerCanUseCustomPixelBuffers() throws Exception {
        WebPDecoder decoder = WebPDecoder.DEFAULT.withPixelFormat(WebPPixelFormat.INT_ARGB_PRE);
        WebPImage expected = decoder.read(resource("images/animated-random_lossless.webp"));

        try (WebPImageReader reader = decoder.open(resource("images/animated-random_lossless.webp"))) {
            int pixelCount = Math.multiplyExact(reader.getWidth(), reader.getHeight());
            ByteOrder nonNativeOrder = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
                    ? ByteOrder.LITTLE_ENDIAN
                    : ByteOrder.BIG_ENDIAN;
            IntBuffer directStorage = ByteBuffer
                    .allocateDirect(Math.multiplyExact(pixelCount + 2, Integer.BYTES))
                    .order(nonNativeOrder)
                    .asIntBuffer();
            directStorage.position(1);
            IntBuffer heapStorage = IntBuffer.allocate(pixelCount + 2);
            heapStorage.position(1);

            WebPFrame first = reader.readNextFrame(directStorage);
            WebPFrame second = reader.readNextFrame(heapStorage);
            WebPFrame third = reader.readNextFrame();

            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(third);
            assertTrue(first.getPixels().isDirect());
            assertFalse(second.getPixels().isDirect());
            assertFalse(third.getPixels().isDirect());
            assertTrue(first.usesCustomPixelBuffer());
            assertTrue(second.usesCustomPixelBuffer());
            assertFalse(third.usesCustomPixelBuffer());
            assertEquals(pixelCount + 1, directStorage.position());
            assertEquals(pixelCount + 1, heapStorage.position());
            assertEquals(nonNativeOrder, first.getPixels().order());
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, first.getPixelFormat());
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, second.getPixelFormat());
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, third.getPixelFormat());
            assertArrayEquals(expected.getFrames().get(0).getArgbArray(), first.getArgbArray());
            assertArrayEquals(expected.getFrames().get(1).getArgbArray(), second.getArgbArray());
            assertArrayEquals(expected.getFrames().get(2).getArgbArray(), third.getArgbArray());
            assertTrue(reader.isComplete());
        }
    }

    /// Verifies custom-buffer validation, progress, and exhausted-reader behavior.
    @Test
    void customPixelBufferValidationDoesNotConsumeAFrame() throws Exception {
        WebPDecoder decoder = WebPDecoder.DEFAULT;
        WebPImage expected = decoder.read(resource("images/gallery1-1.webp"));

        try (WebPImageReader reader = decoder.open(resource("images/gallery1-1.webp"))) {
            int pixelCount = Math.multiplyExact(reader.getWidth(), reader.getHeight());
            IntBuffer readOnly = IntBuffer.allocate(pixelCount).asReadOnlyBuffer();

            assertThrows(NullPointerException.class, () -> reader.readNextFrame(null));
            assertThrows(ReadOnlyBufferException.class, () -> reader.readNextFrame(readOnly));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> reader.readNextFrame(IntBuffer.allocate(pixelCount - 1))
            );
            assertFalse(reader.isComplete());

            ByteOrder nonNativeOrder = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
                    ? ByteOrder.LITTLE_ENDIAN
                    : ByteOrder.BIG_ENDIAN;
            IntBuffer storage = ByteBuffer
                    .allocateDirect(Math.multiplyExact(pixelCount + 2, Integer.BYTES))
                    .order(nonNativeOrder)
                    .asIntBuffer();
            storage.position(1);
            storage.limit(pixelCount + 1);
            WebPFrame frame = reader.readNextFrame(storage);

            assertNotNull(frame);
            assertTrue(frame.usesCustomPixelBuffer());
            assertEquals(pixelCount + 1, storage.position());
            assertEquals(pixelCount + 1, storage.limit());
            assertEquals(nonNativeOrder, frame.getPixels().order());
            assertArrayEquals(expected.getFirstFrame().getArgbArray(), frame.getArgbArray());
            assertTrue(reader.isComplete());
            assertNull(reader.readNextFrame(readOnly));
        }
    }

    /// Verifies that configured eager and streaming entry points carry output settings to frames.
    @Test
    void configuredEntryPointsProduceConfiguredFrames() throws Exception {
        WebPDecoder decoder = WebPDecoder.DEFAULT
                .withPixelFormat(WebPPixelFormat.INT_ARGB_PRE)
                .withDirect(true);

        WebPImage image = decoder.read(resource("images/gallery2-1_webp_a.webp"));
        WebPFrame eagerFrame = image.getFirstFrame();
        assertEquals(WebPPixelFormat.INT_ARGB_PRE, eagerFrame.getPixelFormat());
        assertTrue(eagerFrame.getPixels().isDirect());
        assertFalse(eagerFrame.usesCustomPixelBuffer());

        try (WebPImageReader reader = decoder.open(resource("images/gallery2-1_webp_a.webp"))) {
            WebPFrame streamedFrame = reader.readNextFrame();
            assertNotNull(streamedFrame);
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, streamedFrame.getPixelFormat());
            assertFalse(streamedFrame.usesCustomPixelBuffer());
            assertArrayEquals(eagerFrame.getArgbArray(), streamedFrame.getArgbArray());
            assertEquals(1, reader.getFrameCount());
            assertTrue(reader.isComplete());
        }
    }

    /// Verifies static direct decoding across opaque VP8, VP8 with alpha, and VP8L payloads.
    @Test
    void staticDirectFramesMatchHeapFramesAcrossCodecs() throws Exception {
        for (String path : new String[]{
                "images/gallery1-1.webp",
                "images/gallery2-1_webp_a.webp",
                "images/gallery2-1_webp_ll.webp"
        }) {
            for (WebPPixelFormat pixelFormat : WebPPixelFormat.values()) {
                WebPDecoder heapDecoder = WebPDecoder.DEFAULT.withPixelFormat(pixelFormat);
                WebPDecoder directDecoder = heapDecoder.withDirect(true);

                WebPFrame heap = heapDecoder.read(resource(path)).getFirstFrame();
                WebPFrame direct = directDecoder.read(resource(path)).getFirstFrame();

                assertFalse(heap.getPixels().isDirect(), path);
                assertTrue(direct.getPixels().isDirect(), path);
                assertEquals(pixelFormat, direct.getPixelFormat(), path);
                assertArrayEquals(storedPixels(heap), storedPixels(direct), path + ", " + pixelFormat);
            }
        }
    }

    /// Copies stored frame pixels without changing the frame-owned view.
    ///
    /// @param frame the frame whose stored representation is copied
    /// @return the stored packed pixels
    private static int[] storedPixels(WebPFrame frame) {
        IntBuffer pixels = frame.getPixels();
        int[] values = new int[pixels.remaining()];
        pixels.get(values);
        return values;
    }

    /// Opens one classpath resource and fails clearly if test resources are incomplete.
    ///
    /// @param path the classpath-relative resource path
    /// @return the opened resource stream
    private static InputStream resource(String path) {
        InputStream input = WebPDecoderTest.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalArgumentException("Missing test resource: " + path);
        }
        return input;
    }
}
