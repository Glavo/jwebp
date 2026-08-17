// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.glavo.webp.BrowserWebPTestSupport.assertPixelClose;
import static org.glavo.webp.BrowserWebPTestSupport.assertRowsSolidColor;
import static org.glavo.webp.BrowserWebPTestSupport.assertSolidColor;
import static org.glavo.webp.BrowserWebPTestSupport.decodeAndVerifyReader;
import static org.glavo.webp.BrowserWebPTestSupport.openResource;
import static org.glavo.webp.BrowserWebPTestSupport.readPngArgb;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Regression tests backed by Firefox's WebP decoder and reftest fixtures.
///
/// The corresponding resources are selected from Firefox commit
/// `4272397b835a480b1be6cee142d0fa39e166dbc6` by the Gradle build.
@NotNullByDefault
final class FirefoxWebPTestDataTest {

    /// Class-path root populated by the Firefox test-data download task.
    private static final String ROOT = "firefox-webp-test-data/";

    /// Verifies solid lossless images with opaque and non-premultiplied alpha pixels.
    @Test
    void decodesSolidColorImages() throws Exception {
        WebPImage green = decodeAndVerifyReader(ROOT + "green.webp");
        assertEquals(100, green.getWidth());
        assertEquals(100, green.getHeight());
        assertFalse(green.hasAlpha());
        assertSolidColor(green.getFirstFrame(), 0xFF00FF00, 0);

        WebPImage transparent = decodeAndVerifyReader(ROOT + "transparent.webp");
        assertEquals(100, transparent.getWidth());
        assertEquals(100, transparent.getHeight());
        assertTrue(transparent.hasAlpha());
        assertSolidColor(transparent.getFirstFrame(), 0x8000FF00, 0);
    }

    /// Verifies that an ALPH chunk is ignored when the VP8X alpha feature bit is absent.
    @Test
    void ignoresAlphaChunkWithoutFeatureBit() throws Exception {
        WebPImage image = decodeAndVerifyReader(ROOT + "transparent-no-alpha-header.webp");
        assertEquals(100, image.getWidth());
        assertEquals(100, image.getHeight());
        assertFalse(image.hasAlpha());
        assertSolidColor(image.getFirstFrame(), 0xFF000000, 2);
    }

    /// Verifies opaque frame replacement and transparent subframe composition.
    @Test
    void decodesAnimationCompositionFixtures() throws Exception {
        WebPImage replacement = decodeAndVerifyReader(ROOT + "first-frame-green.webp");
        assertTrue(replacement.isAnimated());
        assertFalse(replacement.hasAlpha());
        assertEquals(2, replacement.getFrames().size());
        assertEquals(200, replacement.getLoopDurationMillis());
        assertSolidColor(replacement.getFrames().get(0), 0xFF00FF00, 0);
        assertSolidColor(replacement.getFrames().get(1), 0xFF7F7F7F, 0);

        WebPImage blend = decodeAndVerifyReader(ROOT + "blend.webp");
        assertTrue(blend.isAnimated());
        assertTrue(blend.hasAlpha());
        assertEquals(2, blend.getFrames().size());
        assertEquals(100, blend.getLoopDurationMillis());
        assertEquals(0x00000000, blend.getFrames().get(0).getArgb(0, 0));
        assertEquals(0xFFFF0000, blend.getFrames().get(0).getArgb(50, 50));
        assertEquals(0xFF00FF00, blend.getFrames().get(1).getArgb(0, 0));
        assertEquals(0xFFFF0000, blend.getFrames().get(1).getArgb(50, 50));
    }

    /// Verifies stripe preservation when a Firefox fixture is decoded.
    @Test
    void decodesStripedImage() throws Exception {
        WebPImage source = decode(ROOT + "downscaled.webp");
        assertEquals(100, source.getWidth());
        assertEquals(100, source.getHeight());
        assertRowsSolidColor(source.getFirstFrame(), 0, 25, 0xFF00FF00, 0);
        assertRowsSolidColor(source.getFirstFrame(), 25, 25, 0xFFFF0000, 0);
        assertRowsSolidColor(source.getFirstFrame(), 50, 25, 0xFF00FF00, 0);
        assertRowsSolidColor(source.getFirstFrame(), 75, 25, 0xFFFF0000, 0);
    }

    /// Verifies raw ICC extraction and the missing-ICCP-chunk feature-bit regression.
    @Test
    void handlesColorProfileFixtures() throws Exception {
        WebPImage profiledGreen = decodeAndVerifyReader(ROOT + "green.icc_srgb.webp");
        assertNotNull(profiledGreen.getMetadata().getIccProfile());
        assertSolidColor(profiledGreen.getFirstFrame(), 0xFF00FF00, 0);

        WebPImage missingProfile = decodeAndVerifyReader(ROOT + "icc-bit-no-icc-chunk.webp");
        assertNull(missingProfile.getMetadata().getIccProfile());
        assertNotNull(missingProfile.getMetadata().getExifMetadata());
        assertArrayEquals(readPngArgb(ROOT + "blue.png"), missingProfile.getFirstFrame().getArgbArray());
    }

    /// Verifies large lossy decode and the four Firefox performance fixtures.
    @Test
    void decodesLargeAndPerformanceImages() throws Exception {
        WebPImage large = decode(ROOT + "large.webp");
        assertEquals(1200, large.getWidth());
        assertEquals(660, large.getHeight());
        assertTrue(large.isLossy());

        WebPImage alphaLossless = decode(ROOT + "perf_srgb_alpha_lossless.webp");
        assertPerformanceImage(alphaLossless, true, false, 0x7F00FF00, 0);

        WebPImage alphaLossy = decode(ROOT + "perf_srgb_alpha_lossy.webp");
        assertPerformanceImage(alphaLossy, true, true, 0x7F00FF00, 4);

        WebPImage lossless = decode(ROOT + "perf_srgb_lossless.webp");
        assertPerformanceImage(lossless, false, false, 0xFF00FF00, 0);

        WebPImage lossy = decode(ROOT + "perf_srgb_lossy.webp");
        assertPerformanceImage(lossy, false, true, 0xFF00FF00, 4);
    }

    /// Verifies the common properties and uniform color of a Firefox performance fixture.
    ///
    /// @param image the decoded performance fixture
    /// @param alpha whether the image is expected to carry alpha
    /// @param lossy whether the image is expected to use lossy compression
    /// @param expectedArgb the expected packed non-premultiplied color
    /// @param maxChannelDelta the maximum accepted per-channel difference
    private static void assertPerformanceImage(
            WebPImage image,
            boolean alpha,
            boolean lossy,
            int expectedArgb,
            int maxChannelDelta
    ) {
        assertEquals(1000, image.getWidth());
        assertEquals(1000, image.getHeight());
        assertEquals(alpha, image.hasAlpha());
        assertEquals(lossy, image.isLossy());
        assertNotNull(image.getMetadata().getIccProfile());
        assertSolidColor(image.getFirstFrame(), expectedArgb, maxChannelDelta);
        assertPixelClose(
                expectedArgb,
                image.getFirstFrame().getArgb(999, 999),
                maxChannelDelta,
                "performance image corner"
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
}
