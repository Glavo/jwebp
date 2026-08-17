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
        WebPDecoder direct = premultiplied.withFrameStorage(WebPFrameStorage.DIRECT);

        assertSame(defaults, defaults.withPixelFormat(WebPPixelFormat.INT_ARGB));
        assertSame(defaults, defaults.withFrameStorage(WebPFrameStorage.HEAP));
        assertNotSame(defaults, premultiplied);
        assertNotSame(premultiplied, direct);

        assertEquals(WebPPixelFormat.INT_ARGB, defaults.getPixelFormat());
        assertEquals(WebPFrameStorage.HEAP, defaults.getFrameStorage());
        assertEquals(WebPPixelFormat.INT_ARGB_PRE, premultiplied.getPixelFormat());
        assertEquals(WebPFrameStorage.HEAP, premultiplied.getFrameStorage());
        assertEquals(WebPPixelFormat.INT_ARGB_PRE, direct.getPixelFormat());
        assertEquals(WebPFrameStorage.DIRECT, direct.getFrameStorage());
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
            for (WebPFrameStorage frameStorage : new WebPFrameStorage[]{
                    WebPFrameStorage.HEAP,
                    WebPFrameStorage.DIRECT
            }) {
                WebPDecoder decoder = WebPDecoder.DEFAULT
                        .withPixelFormat(pixelFormat)
                        .withFrameStorage(frameStorage);
                WebPFrame frame = decoder.createFrame(2, 2, 17, argb, true);

                assertEquals(pixelFormat, frame.getPixelFormat());
                assertEquals(frameStorage, frame.getFrameStorage());
                assertEquals(frameStorage == WebPFrameStorage.DIRECT, frame.getPixels().isDirect());
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

    /// Verifies that automatic storage is resolved only after the frame dimensions are known.
    @Test
    void automaticStorageUsesResolvedFrameSize() {
        WebPDecoder decoder = WebPDecoder.DEFAULT.withFrameStorage(WebPFrameStorage.AUTO);

        WebPFrame small = decoder.createFrame(1, 1, 0, new int[1], false);
        WebPFrame large = decoder.createFrame(1024, 1024, 0, new int[1024 * 1024], false);

        assertEquals(WebPFrameStorage.HEAP, small.getFrameStorage());
        assertEquals(WebPFrameStorage.DIRECT, large.getFrameStorage());
        assertFalse(small.getPixels().isDirect());
        assertTrue(large.getPixels().isDirect());
    }

    /// Verifies that configured eager and streaming entry points carry output settings to frames.
    @Test
    void configuredEntryPointsProduceConfiguredFrames() throws Exception {
        WebPDecoder decoder = WebPDecoder.DEFAULT
                .withPixelFormat(WebPPixelFormat.INT_ARGB_PRE)
                .withFrameStorage(WebPFrameStorage.DIRECT);

        WebPImage image = decoder.read(resource("images/gallery2-1_webp_a.webp"));
        WebPFrame eagerFrame = image.getFirstFrame();
        assertEquals(WebPPixelFormat.INT_ARGB_PRE, eagerFrame.getPixelFormat());
        assertEquals(WebPFrameStorage.DIRECT, eagerFrame.getFrameStorage());
        assertTrue(eagerFrame.getPixels().isDirect());

        try (WebPImageReader reader = decoder.open(resource("images/gallery2-1_webp_a.webp"))) {
            WebPFrame streamedFrame = reader.readNextFrame();
            assertNotNull(streamedFrame);
            assertEquals(WebPPixelFormat.INT_ARGB_PRE, streamedFrame.getPixelFormat());
            assertEquals(WebPFrameStorage.DIRECT, streamedFrame.getFrameStorage());
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
