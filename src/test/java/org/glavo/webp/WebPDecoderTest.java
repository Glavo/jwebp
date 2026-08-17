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
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    /// Verifies that a per-frame override does not modify the reader's default buffer location.
    @Test
    void readerCanOverrideDirectForOneFrame() throws Exception {
        WebPDecoder decoder = WebPDecoder.DEFAULT.withPixelFormat(WebPPixelFormat.INT_ARGB_PRE);
        WebPImage expected = decoder.read(resource("images/animated-random_lossless.webp"));

        try (WebPImageReader reader = decoder.open(resource("images/animated-random_lossless.webp"))) {
            WebPFrame first = reader.readNextFrame(true);
            WebPFrame second = reader.readNextFrame(false);
            WebPFrame third = reader.readNextFrame();

            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(third);
            assertTrue(first.getPixels().isDirect());
            assertFalse(second.getPixels().isDirect());
            assertFalse(third.getPixels().isDirect());
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, first.getPixelFormat());
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, second.getPixelFormat());
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, third.getPixelFormat());
            assertArrayEquals(expected.getFrames().get(0).getArgbArray(), first.getArgbArray());
            assertArrayEquals(expected.getFrames().get(1).getArgbArray(), second.getArgbArray());
            assertArrayEquals(expected.getFrames().get(2).getArgbArray(), third.getArgbArray());
            assertTrue(reader.isComplete());
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

        try (WebPImageReader reader = decoder.open(resource("images/gallery2-1_webp_a.webp"))) {
            WebPFrame streamedFrame = reader.readNextFrame();
            assertNotNull(streamedFrame);
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, streamedFrame.getPixelFormat());
            assertArrayEquals(eagerFrame.getArgbArray(), streamedFrame.getArgbArray());
            assertEquals(1, reader.getFrameCount());
            assertTrue(reader.isComplete());
        }
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
