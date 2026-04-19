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

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import org.glavo.webp.internal.Argb;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/// Tests ported from libwebp's public decoder validation strategy.
///
/// libwebp's upstream repository currently keeps most decoder tests in fuzz targets and
/// regression cases under `tests/fuzzer`. This class ports the same intent to JUnit:
/// example-image verification, API-style decode coverage for valid inputs, and malformed-regression
/// inputs that must never trigger unexpected runtime failures.
@NotNullByDefault
final class LibWebpPortedTest {

    private static final String LIBWEBP_EXAMPLE_WEBP = "libwebp/examples/test.webp";
    private static final String LIBWEBP_EXAMPLE_PNG = "libwebp/examples/test_ref.png";

    private static final String SIMPLE_API_BUGANIZER_498966511 =
            "QUxQSAQAAABB/////0xQCAAAAAAAAMlIBgAAACJF7lBXIkFMUEgAAAAAQUxwSAQAAAAECACAVlA4IFQAAAC2AwCdASoBAAIAed5u9gFP8/yjAANMUFMiAmlGAEZqUnNhmnZQIkVPIkuPT007ck9lY3S9biJXc2VjdGlvbl9KVU5RPSJKVU5RIqhZTyxfSfIRIkFOSU0i";
    private static final String ADVANCED_API_BUGANIZER_498966235 =
            "UklGRgx8AABXRUJQVlA4WAoAAAAQAABEAgAADwAAQUxQSDUAAAAE1wAAAAAAAGM45VMAowCrY8kAFwAAAIAAAAAAoCJBRQEAAAA4PEFMUAgAcwIAAAAAAAAAAABBTFBIAAAAAFZQOCAoAAAAlAEAnQEqAwAQAAMsAH7iAABzZQJpb25StVZxwk19IndlYnAicggDABAj9u4C05AAIIpOQAAW10H3toGBIklGRkAiUklGRiImliFWUAo4UmcAADEi3SJJIlhFQlAiAgIi91x4MIODg+HhbCyDXHNlY3RpcW5jSlVOPSJzZWN0aXN0cmVceDlEXHgwMVx4MkEiSlVLUSI=";
    private static final String ANIMATION_API_BUGANIZER_498965803 =
            "QUxQSAAAAAAAAAAAEQAAAAnFBmTBC38AAHdlYnAchQAjQFZQOCAhAAAAdgMAnQEqB4AiAAIwKAD///8DAAAAycnJycnJycnJycnJycnJycnJycnJicnJycnJGBgYGBgYyck=";
    private static final String ANIM_DECODER_BUGANIZER_498967090 =
            "QUxQSAAAAAAAAAAAAwAAAAxFQlBWUDggGAAAADABAJ0BKgIAAQADADQlpAADfgAqzvuUIkFGTSI8MNwimUoCYK6bm5ubugAA";

    @Test
    void decodesLibWebpExampleAgainstReferencePng() throws Exception {
        WebPImage image = WebPDecoder.decodeAll(resource(LIBWEBP_EXAMPLE_WEBP));
        ReferenceImage expected = readPngAsArgb(LIBWEBP_EXAMPLE_PNG);

        assertEquals(1, image.getFrames().size());
        assertFalse(image.isAnimated());
        assertEquals(expected.width(), image.getWidth());
        assertEquals(expected.height(), image.getHeight());
        assertFrameSamplesClose(image.getFrames().get(0), expected, 24);
    }

    @Test
    void simpleApiStyleValidCorpusDecodesAcrossEntryPoints() throws Exception {
        for (String resource : List.of(
                LIBWEBP_EXAMPLE_WEBP,
                "images/gallery1-1.webp",
                "images/gallery2-1_webp_a.webp",
                "images/animated-random_lossy.webp"
        )) {
            byte[] bytes = readResourceBytes(resource);

            WebPImage eager = WebPDecoder.decodeAll(new ByteArrayInputStream(bytes));
            assertFalse(eager.getFrames().isEmpty(), resource);

            WebPImageLoadOptions options = WebPImageLoadOptions.builder()
                    .requestedWidth(96)
                    .requestedHeight(96)
                    .preserveRatio(true)
                    .smooth(true)
                    .build();

            Image firstFrameImage = WebPDecoder.decodeFirstFrameImage(new ByteArrayInputStream(bytes), options);
            assertTrue(firstFrameImage.getWidth() > 0, resource);
            assertTrue(firstFrameImage.getHeight() > 0, resource);

            try (WebPImageReader reader = WebPDecoder.open(new ChunkedInputStream(bytes, 7), options)) {
                List<WebPFrame> frames = new ArrayList<>();
                while (true) {
                    WebPFrame next = reader.readNextFrame();
                    if (next == null) {
                        break;
                    }
                    frames.add(next);
                }
                assertEquals(eager.getFrames().size(), frames.size(), resource);
                assertTrue(reader.isComplete(), resource);
            }
        }
    }

    @Test
    void advancedApiStyleScalingIsStableAcrossChunkedInput() throws Exception {
        byte[] bytes = readResourceBytes(LIBWEBP_EXAMPLE_WEBP);

        for (WebPImageLoadOptions options : List.of(
                WebPImageLoadOptions.builder().requestedWidth(96).requestedHeight(0).preserveRatio(true).smooth(true).build(),
                WebPImageLoadOptions.builder().requestedWidth(0).requestedHeight(80).preserveRatio(true).smooth(false).build(),
                WebPImageLoadOptions.builder().requestedWidth(96).requestedHeight(80).preserveRatio(true).smooth(true).build(),
                WebPImageLoadOptions.builder().requestedWidth(96).requestedHeight(80).preserveRatio(false).smooth(false).build()
        )) {
            WebPImage eager = WebPDecoder.decodeAll(new ByteArrayInputStream(bytes), options);
            WebPImage streaming = WebPDecoder.decodeAll(new ChunkedInputStream(bytes, 5), options);

            assertEquals(eager.getWidth(), streaming.getWidth());
            assertEquals(eager.getHeight(), streaming.getHeight());
            assertEquals(eager.getFrames().size(), streaming.getFrames().size());
        assertFrameSamplesClose(streaming.getFrames().get(0), readPixels(eager.getFrames().get(0)), 0);
        }
    }

    @Test
    void animationApiStyleConsumesAnimatedFramesFromChunkedInput() throws Exception {
        assertAnimatedChunkedDecode("images/animated-random_lossy.webp", 4);
        assertAnimatedChunkedDecode("images/animated-random_lossless.webp", 3);
    }

    @Test
    void simpleApiStyleMutatedInputsOnlyFailWithWebPException() throws Exception {
        for (String resource : List.of(LIBWEBP_EXAMPLE_WEBP, "images/gallery2-1_webp_a.webp")) {
            byte[] original = readResourceBytes(resource);
            for (byte[] candidate : buildMutations(original)) {
                assertOnlyExpectedDecodeFailure(candidate);
            }
        }
    }

    @Test
    void simpleApiBuganizer498966511DoesNotCrash() {
        assertOnlyExpectedDecodeFailure(Base64.getDecoder().decode(SIMPLE_API_BUGANIZER_498966511));
    }

    @Test
    void advancedApiBuganizer498966235DoesNotCrash() {
        assertOnlyExpectedDecodeFailure(Base64.getDecoder().decode(ADVANCED_API_BUGANIZER_498966235));
    }

    @Test
    void animationApiBuganizer498965803DoesNotCrash() {
        assertOnlyExpectedDecodeFailure(Base64.getDecoder().decode(ANIMATION_API_BUGANIZER_498965803));
    }

    @Test
    void animDecoderBuganizer498967090DoesNotCrash() {
        assertOnlyExpectedDecodeFailure(Base64.getDecoder().decode(ANIM_DECODER_BUGANIZER_498967090));
    }

    private static void assertAnimatedChunkedDecode(String resourceName, int expectedFrameCount) throws Exception {
        byte[] bytes = readResourceBytes(resourceName);
        try (WebPImageReader reader = WebPDecoder.open(new ChunkedInputStream(bytes, 9))) {
            assertTrue(reader.isAnimated(), resourceName);
            assertEquals(expectedFrameCount, reader.getFrameCount(), resourceName);

            int frames = 0;
            while (true) {
                WebPFrame frame = reader.readNextFrame();
                if (frame == null) {
                    break;
                }
                assertTrue(frame.getDurationMillis() >= 0, resourceName);
                frames++;
            }

            assertEquals(expectedFrameCount, frames, resourceName);
            assertTrue(reader.isComplete(), resourceName);
        }
    }

    private static void assertOnlyExpectedDecodeFailure(byte[] data) {
        WebPImageLoadOptions options = WebPImageLoadOptions.builder()
                .requestedWidth(64)
                .requestedHeight(64)
                .preserveRatio(true)
                .smooth(true)
                .build();

        assertOnlyWebPException(() -> WebPDecoder.decodeAll(new ByteArrayInputStream(data)));
        assertOnlyWebPException(() -> WebPDecoder.decodeAll(new ChunkedInputStream(data, 3), options));
        assertOnlyWebPException(() -> WebPDecoder.decodeFirstFrameImage(new ByteArrayInputStream(data), options));
        assertOnlyWebPException(() -> {
            try (WebPImageReader reader = WebPDecoder.open(new ChunkedInputStream(data, 5), options)) {
                while (reader.readNextFrame() != null) {
                    // Consume until exhausted or a WebPException is raised.
                }
            }
        });
    }

    private static void assertOnlyWebPException(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (throwable instanceof WebPException) {
                return;
            }
            fail("Unexpected exception type: " + throwable.getClass().getName(), throwable);
        }
    }

    private static List<byte[]> buildMutations(byte[] original) {
        List<byte[]> mutations = new ArrayList<>();
        for (int length : List.of(1, 8, 16, 32, original.length / 2, Math.max(1, original.length - 1))) {
            if (length >= original.length) {
                continue;
            }
            byte[] truncated = new byte[length];
            System.arraycopy(original, 0, truncated, 0, length);
            mutations.add(truncated);
        }

        for (int position : List.of(0, 1, 8, original.length / 4, original.length / 2, original.length - 1)) {
            int safePosition = Math.max(0, Math.min(original.length - 1, position));
            byte[] mutated = original.clone();
            mutated[safePosition] ^= (byte) 0x5A;
            mutations.add(mutated);
        }
        return mutations;
    }

    private static void assertFrameSamplesClose(WebPFrame frame, ReferenceImage expected, int maxChannelDelta) {
        assertEquals(expected.width(), frame.getWidth(), "frame width");
        assertEquals(expected.height(), frame.getHeight(), "frame height");
        assertFrameSamplesClose(frame, expected.pixels(), maxChannelDelta);
    }

    private static void assertFrameSamplesClose(WebPFrame frame, int[] expectedArgb, int maxChannelDelta) {
        int[] actual = readPixels(frame);
        assertEquals(expectedArgb.length, actual.length, "pixel buffer size");

        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] xs = sampleCoordinates(width);
        int[] ys = sampleCoordinates(height);

        for (int y : ys) {
            for (int x : xs) {
                int pixelIndex = y * width + x;
                for (int channel = 0; channel < 4; channel++) {
                    int expectedValue = visibleChannelValue(expectedArgb[pixelIndex], channel);
                    int actualValue = visibleChannelValue(actual[pixelIndex], channel);
                    int delta = Math.abs(expectedValue - actualValue);
                    if (delta > maxChannelDelta) {
                        fail("sample pixel mismatch at (" + x + ", " + y + "), channel " + channel
                                + ": expected=" + expectedValue + ", actual=" + actualValue + ", delta=" + delta);
                    }
                }
            }
        }
    }

    private static int[] readPixels(WebPFrame frame) {
        int[] pixels = frame.getArgbArray();
        assertNotNull(pixels);
        return pixels;
    }

    private static int visibleChannelValue(int argb, int channel) {
        int alpha = argb >>> 24;
        if (channel == 3) {
            return alpha;
        }
        int value = switch (channel) {
            case 0 -> Argb.red(argb);
            case 1 -> Argb.green(argb);
            case 2 -> Argb.blue(argb);
            default -> throw new IllegalArgumentException("Invalid channel: " + channel);
        };
        return value * alpha / 255;
    }

    private static int[] sampleCoordinates(int size) {
        if (size <= 1) {
            return new int[]{0};
        }
        if (size == 2) {
            return new int[]{0, 1};
        }
        return new int[]{0, size / 4, size / 2, (size * 3) / 4, size - 1};
    }

    private static ReferenceImage readPngAsArgb(String resourceName) throws IOException {
        try (InputStream input = resource(resourceName)) {
            Image image = new Image(input);
            if (image.isError()) {
                throw new IOException("Failed to decode reference PNG: " + resourceName, image.getException());
            }
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            PixelReader pixelReader = image.getPixelReader();
            if (pixelReader == null) {
                throw new IOException("Reference PNG does not expose pixels: " + resourceName);
            }
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    argb[y * width + x] = pixelReader.getArgb(x, y);
                }
            }
            return new ReferenceImage(width, height, argb);
        }
    }

    private static byte[] readResourceBytes(String resourceName) throws IOException {
        try (InputStream input = resource(resourceName)) {
            return input.readAllBytes();
        }
    }

    private static InputStream resource(String name) {
        InputStream input = LibWebpPortedTest.class.getClassLoader().getResourceAsStream(name);
        if (input == null) {
            fail("missing resource: " + name);
        }
        return input;
    }

    @NotNullByDefault
    private record ReferenceImage(int width, int height, int[] pixels) {
    }

    @NotNullByDefault
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /// Forward-only stream that limits read calls to small chunks and does not support mark/reset.
    @NotNullByDefault
    private static final class ChunkedInputStream extends InputStream {
        private final byte[] data;
        private final int chunkSize;
        private int offset;

        private ChunkedInputStream(byte[] data, int chunkSize) {
            this.data = data;
            this.chunkSize = Math.max(1, chunkSize);
        }

        @Override
        public int read() {
            if (offset >= data.length) {
                return -1;
            }
            return data[offset++] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int off, int len) {
            if (offset >= data.length) {
                return -1;
            }
            int count = Math.min(Math.min(len, chunkSize), data.length - offset);
            System.arraycopy(data, offset, buffer, off, count);
            offset += count;
            return count;
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }
}
