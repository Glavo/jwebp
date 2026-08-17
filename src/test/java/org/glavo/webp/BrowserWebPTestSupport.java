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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// Shared assertions for browser-derived WebP regression fixtures.
@NotNullByDefault
final class BrowserWebPTestSupport {

    /// Prevents instantiation of this utility class.
    private BrowserWebPTestSupport() {
    }

    /// Decodes a fixture eagerly and through the forward-only reader, then compares every
    /// observable image property and decoded frame.
    ///
    /// @param resourceName the class-path resource name
    /// @return the eagerly decoded image
    /// @throws IOException if the fixture cannot be read
    static WebPImage decodeAndVerifyReader(String resourceName) throws IOException {
        byte[] encoded = readResource(resourceName);
        WebPImage eager = WebPImage.read(new ByteArrayInputStream(encoded));

        if (!eager.isAnimated()) {
            WebPImage direct = WebPDecoder.DEFAULT
                    .withDirect(true)
                    .read(new ByteArrayInputStream(encoded));
            assertTrue(direct.getFirstFrame().getPixels().isDirect(), resourceName + " direct storage");
            assertImageEquals(eager, direct, resourceName + " direct decode");
        }

        try (WebPImageReader reader = WebPImageReader.open(new ByteArrayInputStream(encoded))) {
            assertEquals(eager.getWidth(), reader.getWidth(), resourceName + " canvas width");
            assertEquals(eager.getHeight(), reader.getHeight(), resourceName + " canvas height");
            assertEquals(eager.hasAlpha(), reader.hasAlpha(), resourceName + " alpha flag");
            assertEquals(eager.isAnimated(), reader.isAnimated(), resourceName + " animation flag");
            assertEquals(eager.isLossy(), reader.isLossy(), resourceName + " lossy flag");
            assertEquals(eager.getLoopCount(), reader.getLoopCount(), resourceName + " loop count");
            assertEquals(eager.getLoopDurationMillis(), reader.getLoopDurationMillis(), resourceName + " duration");
            assertEquals(eager.getFrames().size(), reader.getFrameCount(), resourceName + " frame count");
            assertMetadataEquals(eager.getMetadata(), reader.getMetadata(), resourceName);

            for (int index = 0; index < eager.getFrames().size(); index++) {
                WebPFrame expected = eager.getFrames().get(index);
                @Nullable WebPFrame actual = reader.readNextFrame();
                assertNotNull(actual, resourceName + " frame " + index);
                assertEquals(expected.getWidth(), actual.getWidth(), resourceName + " frame width " + index);
                assertEquals(expected.getHeight(), actual.getHeight(), resourceName + " frame height " + index);
                assertEquals(
                        expected.getDurationMillis(),
                        actual.getDurationMillis(),
                        resourceName + " frame duration " + index
                );
                assertArrayEquals(expected.getArgbArray(), actual.getArgbArray(), resourceName + " frame " + index);
            }

            assertNull(reader.readNextFrame(), resourceName + " must have no additional frames");
            assertTrue(reader.isComplete(), resourceName + " reader must be complete");
        }

        return eager;
    }

    /// Verifies equality for all observable image properties, metadata, and decoded frames.
    ///
    /// @param expected the expected image
    /// @param actual the actual image
    /// @param message the assertion context
    static void assertImageEquals(WebPImage expected, WebPImage actual, String message) {
        assertEquals(expected.getWidth(), actual.getWidth(), message + " canvas width");
        assertEquals(expected.getHeight(), actual.getHeight(), message + " canvas height");
        assertEquals(expected.hasAlpha(), actual.hasAlpha(), message + " alpha flag");
        assertEquals(expected.isAnimated(), actual.isAnimated(), message + " animation flag");
        assertEquals(expected.isLossy(), actual.isLossy(), message + " lossy flag");
        assertEquals(expected.getLoopCount(), actual.getLoopCount(), message + " loop count");
        assertEquals(expected.getLoopDurationMillis(), actual.getLoopDurationMillis(), message + " duration");
        assertEquals(expected.getFrames().size(), actual.getFrames().size(), message + " frame count");
        assertMetadataEquals(expected.getMetadata(), actual.getMetadata(), message);

        for (int index = 0; index < expected.getFrames().size(); index++) {
            WebPFrame expectedFrame = expected.getFrames().get(index);
            WebPFrame actualFrame = actual.getFrames().get(index);
            assertEquals(expectedFrame.getWidth(), actualFrame.getWidth(), message + " frame width " + index);
            assertEquals(expectedFrame.getHeight(), actualFrame.getHeight(), message + " frame height " + index);
            assertEquals(
                    expectedFrame.getDurationMillis(),
                    actualFrame.getDurationMillis(),
                    message + " frame duration " + index
            );
            assertArrayEquals(expectedFrame.getArgbArray(), actualFrame.getArgbArray(), message + " frame " + index);
        }
    }

    /// Reads a class-path resource into memory.
    ///
    /// @param resourceName the class-path resource name
    /// @return the resource bytes
    /// @throws IOException if the resource cannot be read
    static byte[] readResource(String resourceName) throws IOException {
        try (InputStream input = openResource(resourceName)) {
            return input.readAllBytes();
        }
    }

    /// Opens a required class-path resource.
    ///
    /// The caller owns the returned stream and must close it.
    ///
    /// @param resourceName the class-path resource name
    /// @return the open resource stream
    static InputStream openResource(String resourceName) {
        @Nullable InputStream input = BrowserWebPTestSupport.class.getClassLoader().getResourceAsStream(resourceName);
        if (input == null) {
            throw new AssertionError("Missing test resource: " + resourceName);
        }
        return input;
    }

    /// Reads a PNG resource as tightly packed non-premultiplied `ARGB` pixels.
    ///
    /// @param resourceName the class-path resource name
    /// @return the decoded pixels in row-major order
    /// @throws IOException if the PNG cannot be read
    static int[] readPngArgb(String resourceName) throws IOException {
        try (InputStream input = openResource(resourceName)) {
            @Nullable BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new AssertionError("Reference PNG must decode: " + resourceName);
            }
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        }
    }

    /// Verifies that every frame pixel is within a per-channel tolerance of one color.
    ///
    /// @param frame the frame to inspect
    /// @param expectedArgb the expected packed non-premultiplied `ARGB` color
    /// @param maxChannelDelta the maximum accepted difference for each channel
    static void assertSolidColor(WebPFrame frame, int expectedArgb, int maxChannelDelta) {
        assertRowsSolidColor(frame, 0, frame.getHeight(), expectedArgb, maxChannelDelta);
    }

    /// Verifies that a consecutive range of rows is within a per-channel tolerance of one color.
    ///
    /// @param frame the frame to inspect
    /// @param firstRow the first row to inspect
    /// @param rowCount the number of rows to inspect
    /// @param expectedArgb the expected packed non-premultiplied `ARGB` color
    /// @param maxChannelDelta the maximum accepted difference for each channel
    static void assertRowsSolidColor(
            WebPFrame frame,
            int firstRow,
            int rowCount,
            int expectedArgb,
            int maxChannelDelta
    ) {
        assertTrue(firstRow >= 0, "first row");
        assertTrue(rowCount >= 0, "row count");
        assertTrue(firstRow + rowCount <= frame.getHeight(), "row range");

        for (int y = firstRow; y < firstRow + rowCount; y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                assertPixelClose(expectedArgb, frame.getArgb(x, y), maxChannelDelta, "pixel at (" + x + ", " + y + ")");
            }
        }
    }

    /// Verifies that two packed pixels differ by no more than the supplied per-channel tolerance.
    ///
    /// @param expectedArgb the expected packed non-premultiplied `ARGB` value
    /// @param actualArgb the actual packed non-premultiplied `ARGB` value
    /// @param maxChannelDelta the maximum accepted difference for each channel
    /// @param message the assertion context
    static void assertPixelClose(int expectedArgb, int actualArgb, int maxChannelDelta, String message) {
        assertTrue(maxChannelDelta >= 0, "channel tolerance");
        for (int shift = 0; shift <= 24; shift += 8) {
            int expected = (expectedArgb >>> shift) & 0xFF;
            int actual = (actualArgb >>> shift) & 0xFF;
            int delta = Math.abs(expected - actual);
            if (delta > maxChannelDelta) {
                fail(message + ", channel shift " + shift + ": expected=" + expected
                        + ", actual=" + actual + ", delta=" + delta);
            }
        }
    }

    /// Verifies that all raw metadata payloads are equal.
    ///
    /// @param expected the expected metadata
    /// @param actual the actual metadata
    /// @param message the assertion context
    private static void assertMetadataEquals(WebPMetadata expected, WebPMetadata actual, String message) {
        assertNullableArrayEquals(expected.getIccProfile(), actual.getIccProfile(), message + " ICC profile");
        assertNullableArrayEquals(expected.getExifMetadata(), actual.getExifMetadata(), message + " EXIF metadata");
        assertNullableArrayEquals(expected.getXmpMetadata(), actual.getXmpMetadata(), message + " XMP metadata");
    }

    /// Verifies equality for two nullable byte arrays.
    ///
    /// @param expected the expected array, or `null`
    /// @param actual the actual array, or `null`
    /// @param message the assertion context
    private static void assertNullableArrayEquals(
            byte @Nullable [] expected,
            byte @Nullable [] actual,
            String message
    ) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual, message);
        } else {
            assertArrayEquals(expected, actual, message);
        }
    }
}
