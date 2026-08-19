// Copyright 2014 The Go Authors. All rights reserved.
// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: BSD-3-Clause
package org.glavo.webp;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Decoder tests adapted from the WebP tests in `golang.org/x/image`.
///
/// The resources are selected from Go image commit
/// `3ebddc7c54bd879f8d84d11db82892726f5192fd`.
@NotNullByDefault
final class GoImageWebPTestDataTest {

    /// Class-path root populated by the Go image test-data download task.
    private static final String ROOT = "go-image-webp-test-data/";

    /// Lossless inputs and their exact PNG reference images.
    private static final @Unmodifiable List<LosslessCase> LOSSLESS_CASES = List.of(
            new LosslessCase("blue-purple-pink.lossless.webp", "blue-purple-pink.png"),
            new LosslessCase("blue-purple-pink-large.lossless.webp", "blue-purple-pink-large.png"),
            new LosslessCase("gopher-doc.1bpp.lossless.webp", "gopher-doc.1bpp.png"),
            new LosslessCase("gopher-doc.2bpp.lossless.webp", "gopher-doc.2bpp.png"),
            new LosslessCase("gopher-doc.4bpp.lossless.webp", "gopher-doc.4bpp.png"),
            new LosslessCase("gopher-doc.8bpp.lossless.webp", "gopher-doc.8bpp.png"),
            new LosslessCase("gopher-doc.with-alpha.lossless.webp", "gopher-doc.with-alpha.png"),
            new LosslessCase("tux.lossless.webp", "tux.png"),
            new LosslessCase("yellow_rose.lossless.webp", "yellow_rose.png"),
            new LosslessCase("gopher-doc.skip-hgroup.lossless.webp", "gopher-doc.8bpp.png")
    );

    /// Verifies that VP8L palette, alpha, and remapped-Huffman-group fixtures match their PNGs.
    @Test
    void losslessImagesMatchReferencePngs() throws Exception {
        for (LosslessCase testCase : LOSSLESS_CASES) {
            ReferenceImage expected = readPng(testCase.pngFile());
            WebPImage actual;
            try (InputStream input = openResource(testCase.webpFile())) {
                actual = WebPImage.read(input);
            }

            assertFalse(actual.isAnimated(), testCase.webpFile());
            assertFalse(actual.isLossy(), testCase.webpFile());
            assertEquals(expected.width(), actual.getWidth(), testCase.webpFile() + " width");
            assertEquals(expected.height(), actual.getHeight(), testCase.webpFile() + " height");
            assertArrayEquals(expected.argb(), actual.getFirstFrame().getArgbArray(), testCase.webpFile());
        }
    }

    /// Verifies that a forged large first-chunk length is rejected without trusting that length.
    ///
    /// @param tempDirectory the temporary directory used to exercise seekable input
    @Test
    void rejectsTruncatedPartitionWithLargeDeclaredLength(@TempDir Path tempDirectory) throws IOException {
        byte[] data = {
                'R', 'I', 'F', 'F', (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F,
                'W', 'E', 'B', 'P', 'V', 'P', '8', ' ', 0x78, 0x56, 0x34, 0x12,
                (byte) 0xBD, 0x01, 0x00, 0x14, 0x00, 0x00, (byte) 0xB2, 0x34,
                0x0A, (byte) 0x9D, 0x01, 0x2A, (byte) 0x96, 0x00, 0x67, 0x00
        };

        assertThrows(WebPException.class, () -> WebPImage.read(new ByteArrayInputStream(data)));

        Path file = tempDirectory.resolve("large-declared-length.webp");
        Files.write(file, data);
        assertThrows(WebPException.class, () -> WebPImage.read(file));
    }

    /// Verifies that an extended container cannot contain a second VP8X chunk.
    @Test
    void rejectsDuplicateVp8xChunk() {
        byte[] data = {
                'R', 'I', 'F', 'F', 49, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', 'X', 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                'V', 'P', '8', 'X', 10, 0, 0, 0, 0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };

        assertThrows(WebPException.class, () -> WebPImage.read(new ByteArrayInputStream(data)));
    }

    /// Verifies that VP8X canvas dimensions must match both VP8 and VP8L static payloads.
    @Test
    void rejectsStaticFrameDimensionMismatch() throws Exception {
        for (String fileName : new String[]{
                "blue-purple-pink.lossy.webp",
                "blue-purple-pink.lossless.webp"
        }) {
            byte[] data = extendedWithOnePixelCanvas(readResource(fileName));
            assertThrows(
                    WebPException.class,
                    () -> WebPImage.read(new ByteArrayInputStream(data)),
                    fileName
            );
        }
    }

    /// Wraps the image chunk from a simple WebP file in a mismatched one-pixel VP8X canvas.
    ///
    /// @param simpleWebP the complete simple WebP file
    /// @return an extended WebP file whose canvas is one by one
    private static byte[] extendedWithOnePixelCanvas(byte[] simpleWebP) {
        if (simpleWebP.length < 12) {
            throw new IllegalArgumentException("Simple WebP file is too small");
        }

        byte[] imageChunk = Arrays.copyOfRange(simpleWebP, 12, simpleWebP.length);
        byte[] vp8xChunk = {
                'V', 'P', '8', 'X', 10, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0
        };
        int riffSize = 4 + vp8xChunk.length + imageChunk.length;
        ByteBuffer result = ByteBuffer.allocate(riffSize + 8).order(ByteOrder.LITTLE_ENDIAN);
        result.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        result.putInt(riffSize);
        result.put("WEBP".getBytes(StandardCharsets.US_ASCII));
        result.put(vp8xChunk);
        result.put(imageChunk);
        return result.array();
    }

    /// Reads one downloaded resource completely.
    ///
    /// @param fileName the file name relative to [#ROOT]
    /// @return the resource bytes
    /// @throws IOException if the resource cannot be read
    private static byte[] readResource(String fileName) throws IOException {
        try (InputStream input = openResource(fileName)) {
            return input.readAllBytes();
        }
    }

    /// Opens one required downloaded resource.
    ///
    /// The caller owns the returned stream.
    ///
    /// @param fileName the file name relative to [#ROOT]
    /// @return the open resource stream
    private static InputStream openResource(String fileName) {
        @Nullable InputStream input = GoImageWebPTestDataTest.class.getClassLoader()
                .getResourceAsStream(ROOT + fileName);
        if (input == null) {
            throw new AssertionError("Missing test resource: " + ROOT + fileName);
        }
        return input;
    }

    /// Reads one PNG reference as tightly packed non-premultiplied ARGB.
    ///
    /// @param fileName the file name relative to [#ROOT]
    /// @return the decoded image dimensions and pixels
    /// @throws IOException if the PNG cannot be read
    private static ReferenceImage readPng(String fileName) throws IOException {
        try (InputStream input = openResource(fileName)) {
            @Nullable BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Failed to decode reference PNG: " + fileName);
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
            return new ReferenceImage(width, height, argb);
        }
    }

    /// Associates one lossless WebP input with its PNG reference.
    ///
    /// @param webpFile the WebP resource file name
    /// @param pngFile the PNG reference file name
    @NotNullByDefault
    private record LosslessCase(String webpFile, String pngFile) {
    }

    /// Holds a decoded PNG reference image.
    ///
    /// @param width the image width
    /// @param height the image height
    /// @param argb the tightly packed non-premultiplied pixels
    @NotNullByDefault
    private record ReferenceImage(int width, int height, int[] argb) {
    }
}
