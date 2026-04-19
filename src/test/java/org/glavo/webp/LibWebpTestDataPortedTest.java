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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Ports the decoder-side intent of the downloaded libwebp test-data corpus.
///
/// The upstream shell tests primarily do two things:
/// 1. verify known lossless vectors against stable reference images;
/// 2. verify that multiple differently-encoded bitstreams decode to the same pixels.
///
/// This class mirrors those checks using the Java decoder directly, without invoking `dwebp`.
@NotNullByDefault
final class LibWebpTestDataPortedTest {

    private static final String TEST_DATA_ROOT = "libwebp-test-data/";

    @Test
    void losslessVectorFamily1MatchesGridReference() throws Exception {
        ReferenceImage expected = readPngAsArgb(TEST_DATA_ROOT + "grid.png");

        for (int i = 0; i < 16; i++) {
            String resource = TEST_DATA_ROOT + "lossless_vec_1_" + i + ".webp";
            assertFrameEquals(resource, expected);
        }
    }

    @Test
    void losslessVectorFamily2MatchesPeakReference() throws Exception {
        ReferenceImage expected = readPngAsArgb(TEST_DATA_ROOT + "peak.png");

        for (int i = 0; i < 16; i++) {
            String resource = TEST_DATA_ROOT + "lossless_vec_2_" + i + ".webp";
            assertFrameEquals(resource, expected);
        }
    }

    @Test
    void losslessColorTransformMatchesPamReference() throws Exception {
        ReferenceImage expected = readPamAsRgba(TEST_DATA_ROOT + "lossless_color_transform.pam");
        assertFrameEquals(TEST_DATA_ROOT + "lossless_color_transform.webp", expected);
    }

    @Test
    void allPamManifestEntriesDecodeSuccessfully() throws Exception {
        LinkedHashSet<String> resources = new LinkedHashSet<>();
        for (ManifestEntry entry : readPamManifestEntries()) {
            resources.add(entry.webpFile());
        }

        assertTrue(resources.size() > 50, "expected a substantial libwebp PAM corpus");

        for (String webpFile : resources) {
            WebPImage image = WebPDecoder.decodeAll(resource(TEST_DATA_ROOT + webpFile));
            assertEquals(1, image.getFrames().size(), webpFile);
            assertFalse(image.isAnimated(), webpFile);
            assertTrue(image.getWidth() > 0, webpFile);
            assertTrue(image.getHeight() > 0, webpFile);
        }
    }

    @Test
    void pamMd5EquivalenceGroupsDecodeToSamePixels() throws Exception {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (ManifestEntry entry : readPamManifestEntries()) {
            groups.computeIfAbsent(entry.md5(), ignored -> new ArrayList<>()).add(entry.webpFile());
        }

        int checkedGroups = 0;
        for (List<String> files : groups.values()) {
            if (files.size() < 2) {
                continue;
            }

            int[] expected = decodePixels(TEST_DATA_ROOT + files.get(0));
            for (int i = 1; i < files.size(); i++) {
                String file = files.get(i);
                assertPixelsClose(expected, decodePixels(TEST_DATA_ROOT + file), 8, "decoded PAM-equivalent corpus entry: " + file);
            }
            checkedGroups++;
        }

        assertTrue(checkedGroups >= 10, "expected multiple non-trivial md5 equivalence groups");
    }

    private static void assertFrameEquals(String webpResource, ReferenceImage expected) throws Exception {
        WebPImage image = WebPDecoder.decodeAll(resource(webpResource));
        assertEquals(1, image.getFrames().size(), webpResource);

        WebPFrame frame = image.getFrames().get(0);
        assertEquals(expected.width(), frame.getWidth(), webpResource + " width");
        assertEquals(expected.height(), frame.getHeight(), webpResource + " height");
        assertArrayEquals(expected.argb(), readPixels(frame), webpResource);
    }

    private static int[] decodePixels(String webpResource) throws Exception {
        WebPImage image = WebPDecoder.decodeAll(resource(webpResource));
        assertEquals(1, image.getFrames().size(), webpResource);
        return readPixels(image.getFrames().get(0));
    }

    private static List<ManifestEntry> readPamManifestEntries() throws IOException {
        List<ManifestEntry> entries = new ArrayList<>();
        String manifest;
        try (InputStream input = resource(TEST_DATA_ROOT + "libwebp_tests.md5")) {
            manifest = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }

        for (String line : manifest.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length != 2 || !parts[1].endsWith(".webp.pam")) {
                continue;
            }
            entries.add(new ManifestEntry(parts[0], parts[1].substring(0, parts[1].length() - 4)));
        }
        return entries;
    }

    private static ReferenceImage readPngAsArgb(String resourceName) throws IOException {
        try (InputStream input = resource(resourceName)) {
            Image image = new Image(input);
            if (image.isError()) {
                throw new IOException("Failed to decode PNG reference: " + resourceName, image.getException());
            }
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            PixelReader pixelReader = image.getPixelReader();
            if (pixelReader == null) {
                throw new IOException("PNG reference does not expose pixels: " + resourceName);
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

    private static ReferenceImage readPamAsRgba(String resourceName) throws IOException {
        byte[] bytes;
        try (InputStream input = resource(resourceName)) {
            bytes = input.readAllBytes();
        }

        int headerEnd = indexOf(bytes, "ENDHDR\n".getBytes(StandardCharsets.US_ASCII));
        if (headerEnd < 0) {
            throw new IOException("Invalid PAM header: missing ENDHDR");
        }

        String header = new String(bytes, 0, headerEnd + "ENDHDR\n".length(), StandardCharsets.US_ASCII);
        int width = -1;
        int height = -1;
        int depth = -1;
        int maxVal = -1;

        for (String line : header.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equals("P7") || trimmed.equals("ENDHDR")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length != 2) {
                continue;
            }
            switch (parts[0]) {
                case "WIDTH" -> width = Integer.parseInt(parts[1]);
                case "HEIGHT" -> height = Integer.parseInt(parts[1]);
                case "DEPTH" -> depth = Integer.parseInt(parts[1]);
                case "MAXVAL" -> maxVal = Integer.parseInt(parts[1]);
                default -> {
                }
            }
        }

        if (width <= 0 || height <= 0 || (depth != 3 && depth != 4) || maxVal != 255) {
            throw new IOException("Unsupported PAM header in " + resourceName);
        }

        int dataOffset = headerEnd + "ENDHDR\n".length();
        int expectedDataLength = width * height * depth;
        if (bytes.length - dataOffset != expectedDataLength) {
            throw new IOException("Unexpected PAM payload length in " + resourceName);
        }

        int[] argb = new int[width * height];
        if (depth == 4) {
            for (int src = dataOffset, pixel = 0; src < bytes.length; src += 4, pixel++) {
                int red = bytes[src] & 0xFF;
                int green = bytes[src + 1] & 0xFF;
                int blue = bytes[src + 2] & 0xFF;
                int alpha = bytes[src + 3] & 0xFF;
                argb[pixel] = Argb.pack(alpha, red, green, blue);
            }
        } else {
            for (int src = dataOffset, pixel = 0; src < bytes.length; src += 3, pixel++) {
                argb[pixel] = Argb.opaque(bytes[src] & 0xFF, bytes[src + 1] & 0xFF, bytes[src + 2] & 0xFF);
            }
        }
        return new ReferenceImage(width, height, argb);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int[] readPixels(WebPFrame frame) {
        return frame.getArgbArray();
    }

    private static void assertPixelsClose(int[] expected, int[] actual, int maxChannelDelta, String message) {
        assertEquals(expected.length, actual.length, message + " size");
        for (int i = 0; i < expected.length; i++) {
            for (int channel = 0; channel < 4; channel++) {
                int expectedValue = visibleChannelValue(expected[i], channel);
                int actualValue = visibleChannelValue(actual[i], channel);
                int delta = Math.abs(expectedValue - actualValue);
                if (delta > maxChannelDelta) {
                    throw new AssertionError(message + " => pixel " + i + ", channel " + channel + " differs: expected=" + expectedValue + ", actual=" + actualValue + ", delta=" + delta);
                }
            }
        }
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

    private static InputStream resource(String path) {
        InputStream input = LibWebpTestDataPortedTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, "Missing test resource: " + path);
        return input;
    }

    @NotNullByDefault
    private record ReferenceImage(int width, int height, int[] argb) {
    }

    @NotNullByDefault
    private record ManifestEntry(String md5, String webpFile) {
    }
}
