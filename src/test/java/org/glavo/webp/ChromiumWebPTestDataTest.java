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
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.glavo.webp.BrowserWebPTestSupport.assertImageEquals;
import static org.glavo.webp.BrowserWebPTestSupport.assertPixelClose;
import static org.glavo.webp.BrowserWebPTestSupport.assertSolidColor;
import static org.glavo.webp.BrowserWebPTestSupport.decodeAndVerifyReader;
import static org.glavo.webp.BrowserWebPTestSupport.openResource;
import static org.glavo.webp.BrowserWebPTestSupport.readResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Regression tests backed by Chromium's WebP image-decoder fixtures.
///
/// The corresponding resources are selected from Chromium commit
/// `8f4baaae073181e7e0fea1807f8db6ad720dbcb7` by the Gradle build.
@NotNullByDefault
final class ChromiumWebPTestDataTest {

    /// Class-path root populated by the Chromium test-data download task.
    private static final String ROOT = "chromium-webp-test-data/";

    /// Verifies the lossless and lossy three-by-three red fixtures.
    @Test
    void decodesSmallRedImages() throws Exception {
        WebPImage lossless = decodeAndVerifyReader(ROOT + "red3x3-lossless.webp");
        assertEquals(3, lossless.getWidth());
        assertEquals(3, lossless.getHeight());
        assertFalse(lossless.isLossy());
        assertSolidColor(lossless.getFirstFrame(), 0xFFFF0000, 1);

        WebPImage lossy = decodeAndVerifyReader(ROOT + "red3x3-lossy.webp");
        assertEquals(3, lossy.getWidth());
        assertEquals(3, lossy.getHeight());
        assertTrue(lossy.isLossy());
        assertSolidColor(lossy.getFirstFrame(), 0xFFFF0000, 2);
    }

    /// Verifies valid static regression and fuzz-derived fixtures.
    @Test
    void decodesStaticRegressionImages() throws Exception {
        WebPImage alphaRegression = decodeAndVerifyReader(ROOT + "crbug.364830.webp");
        assertEquals(150, alphaRegression.getWidth());
        assertEquals(100, alphaRegression.getHeight());
        assertTrue(alphaRegression.hasAlpha());

        WebPImage sizeRegression = decodeAndVerifyReader(ROOT + "size-failure.b186640109.webp");
        assertEquals(100, sizeRegression.getWidth());
        assertEquals(100, sizeRegression.getHeight());
        assertSolidColor(sizeRegression.getFirstFrame(), 0x00000000, 0);

        assertStaticImage("test.webp", 128, 128, false, true);
        assertStaticImage("test2.webp", 64, 64, true, true);
        assertStaticImage("test3.webp", 64, 64, true, false);
    }

    /// Verifies animation dimensions, timing, looping, and composition modes.
    @Test
    void decodesAnimationFixtures() throws Exception {
        assertAnimation("webp-animated.webp", 11, 29, 0, 1000, 500, 1000);
        assertAnimation("webp-animated-opaque.webp", 94, 87, 0, 1000, 1000, 1000, 1000);
        assertAnimation("webp-animated-no-blend.webp", 94, 87, 0, 1000, 1000, 1000, 1000);
        assertAnimation("webp-animated-large.webp", 500, 500, 0, 30, 30, 30, 30, 30, 30, 30, 30);
        assertAnimation("webp-animated-semitransparent1.webp", 624, 624, 1, 500, 500, 500, 500);
        assertAnimation("webp-animated-semitransparent2.webp", 512, 512, 1, 300, 300);
        assertAnimation("webp-animated-semitransparent3.webp", 624, 624, 1, 500, 500, 500, 500);
        assertAnimation("webp-animated-semitransparent4.webp", 400, 400, 1, 1500, 500, 500, 500);
    }

    /// Verifies the browser fixture carrying both ICC and XMP metadata in an animation.
    @Test
    void extractsAnimatedMetadata() throws Exception {
        WebPImage image = decodeAndVerifyReader(ROOT + "webp-animated-icc-xmp.webp");
        assertEquals(60, image.getWidth());
        assertEquals(29, image.getHeight());
        assertEquals(13, image.getFrames().size());
        assertEquals(32000, image.getLoopCount());
        assertEquals(260, image.getLoopDurationMillis());
        assertNotNull(image.getMetadata().getIccProfile());
        assertNull(image.getMetadata().getExifMetadata());
        assertNotNull(image.getMetadata().getXmpMetadata());
        for (WebPFrame frame : image.getFrames()) {
            assertEquals(20, frame.getDurationMillis());
        }
    }

    /// Verifies raw metadata extraction without applying browser color management.
    @Test
    void extractsStaticColorProfileMetadata() throws Exception {
        WebPImage noProfile = decode(ROOT + "webp-color-no-profile-lossy.webp");
        assertNull(noProfile.getMetadata().getIccProfile());

        WebPImage crashRegression = decode(ROOT + "webp-color-profile-crash.webp");
        assertNotNull(crashRegression.getMetadata().getIccProfile());
        assertNotNull(crashRegression.getMetadata().getExifMetadata());
        assertNotNull(crashRegression.getMetadata().getXmpMetadata());

        WebPImage lossless = decode(ROOT + "webp-color-profile-lossless.webp");
        assertNotNull(lossless.getMetadata().getIccProfile());
        assertFalse(lossless.isLossy());

        WebPImage lossyAlpha = decode(ROOT + "webp-color-profile-lossy-alpha.webp");
        assertNotNull(lossyAlpha.getMetadata().getIccProfile());
        assertTrue(lossyAlpha.hasAlpha());
        assertTrue(lossyAlpha.isLossy());

        WebPImage lossy = decode(ROOT + "webp-color-profile-lossy.webp");
        assertNotNull(lossy.getMetadata().getIccProfile());
        assertFalse(lossy.hasAlpha());
        assertTrue(lossy.isLossy());
    }

    /// Verifies that malformed and truncated Chromium fixtures fail with [WebPException].
    @Test
    void rejectsMalformedImages() throws Exception {
        assertRejected("invalid-animated-webp.webp");
        assertRejected("invalid-animated-webp2.webp");
        assertRejected("invalid-animated-webp3.webp");
        assertRejected("invalid-animated-webp4.webp");
        assertRejected("invalid_vp8_vp8x.webp");
        assertRejected("truncated.webp");
        assertRejected("truncated2.webp");
    }

    /// Verifies decoding when every bulk input read returns at most one byte.
    @Test
    void decodesOneByteInputChunks() throws Exception {
        byte[] encoded = readResource(ROOT + "webp-animated.webp");
        WebPImage expected = WebPImage.read(new ByteArrayInputStream(encoded));
        WebPImage actual = WebPImage.read(new OneByteInputStream(encoded));
        assertImageEquals(expected, actual, "one-byte Chromium stream");
        assertPixelClose(
                expected.getFirstFrame().getArgb(0, 0),
                actual.getFirstFrame().getArgb(0, 0),
                0,
                "first animation pixel"
        );
    }

    /// Decodes a static fixture and verifies its expected container properties.
    ///
    /// @param fileName the fixture file name relative to [#ROOT]
    /// @param width the expected width
    /// @param height the expected height
    /// @param alpha whether the image is expected to carry alpha
    /// @param lossy whether the image is expected to use lossy compression
    /// @throws IOException if the fixture cannot be read or decoded
    private static void assertStaticImage(
            String fileName,
            int width,
            int height,
            boolean alpha,
            boolean lossy
    ) throws IOException {
        WebPImage image = decodeAndVerifyReader(ROOT + fileName);
        assertEquals(width, image.getWidth(), fileName + " width");
        assertEquals(height, image.getHeight(), fileName + " height");
        assertEquals(alpha, image.hasAlpha(), fileName + " alpha flag");
        assertEquals(lossy, image.isLossy(), fileName + " lossy flag");
        assertFalse(image.isAnimated(), fileName + " animation flag");
        assertEquals(1, image.getFrames().size(), fileName + " frame count");
    }

    /// Decodes an animation and verifies its canvas, loop count, and frame timing.
    ///
    /// @param fileName the fixture file name relative to [#ROOT]
    /// @param width the expected canvas width
    /// @param height the expected canvas height
    /// @param loopCount the expected raw WebP loop count
    /// @param expectedDurations the expected frame durations in presentation order
    /// @throws IOException if the fixture cannot be read or decoded
    private static void assertAnimation(
            String fileName,
            int width,
            int height,
            int loopCount,
            int @Unmodifiable ... expectedDurations
    ) throws IOException {
        WebPImage image = decode(ROOT + fileName);
        assertEquals(width, image.getWidth(), fileName + " width");
        assertEquals(height, image.getHeight(), fileName + " height");
        assertTrue(image.isAnimated(), fileName + " animation flag");
        assertEquals(loopCount, image.getLoopCount(), fileName + " loop count");
        assertEquals(expectedDurations.length, image.getFrames().size(), fileName + " frame count");

        long expectedLoopDuration = 0;
        for (int index = 0; index < expectedDurations.length; index++) {
            int duration = expectedDurations[index];
            expectedLoopDuration += duration;
            assertEquals(duration, image.getFrames().get(index).getDurationMillis(), fileName + " frame " + index);
        }
        assertEquals(expectedLoopDuration, image.getLoopDurationMillis(), fileName + " loop duration");
    }

    /// Verifies that a fixture is rejected specifically as malformed WebP data.
    ///
    /// @param fileName the fixture file name relative to [#ROOT]
    /// @throws IOException if the fixture resource itself cannot be read
    private static void assertRejected(String fileName) throws IOException {
        byte[] encoded = readResource(ROOT + fileName);
        assertThrows(
                WebPException.class,
                () -> WebPImage.read(new ByteArrayInputStream(encoded)),
                fileName
        );
    }

    /// Decodes one fixture while closing its class-path stream on both success and failure.
    ///
    /// @param resourceName the complete class-path resource name
    /// @return the decoded image
    /// @throws IOException if the fixture cannot be read or decoded
    private static WebPImage decode(String resourceName) throws IOException {
        try (InputStream input = openResource(resourceName)) {
            return WebPImage.read(input);
        }
    }

    /// Input stream that deliberately fragments every bulk read to one byte.
    @NotNullByDefault
    private static final class OneByteInputStream extends ByteArrayInputStream {

        /// Creates a fragmented stream backed by a private copy of the supplied bytes.
        ///
        /// @param input the bytes to expose
        private OneByteInputStream(byte @Unmodifiable [] input) {
            super(input.clone());
        }

        /// Reads at most one byte into the destination array.
        ///
        /// @param destination the destination array
        /// @param offset the destination offset
        /// @param length the requested byte count
        /// @return the number of bytes read, `0` for an empty request, or `-1` at end of input
        @Override
        public synchronized int read(byte[] destination, int offset, int length) {
            return super.read(destination, offset, Math.min(length, 1));
        }
    }
}
