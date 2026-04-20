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

import org.glavo.webp.swing.WebPSwingUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Tests for the Swing WebP image adapter.
@NotNullByDefault
final class WebPSwingUtilsTest {

    @Test
    void convertsAnimatedImageUsingFirstFrame() {
        WebPImage image = new WebPImage(
                2,
                1,
                2,
                1,
                true,
                true,
                false,
                0,
                80,
                WebPMetadata.empty(),
                List.of(
                        new WebPFrame(2, 1, 40, new int[]{0x80FF0000, 0xFF00FF00}),
                        new WebPFrame(2, 1, 40, new int[]{0xFF0000FF, 0xFFFFFFFF})
                )
        );

        BufferedImage converted = WebPSwingUtils.fromWebPImage(image);

        assertEquals(BufferedImage.TYPE_INT_ARGB, converted.getType());
        assertEquals(2, converted.getWidth());
        assertEquals(1, converted.getHeight());
        assertEquals(0x80FF0000, converted.getRGB(0, 0));
        assertEquals(0xFF00FF00, converted.getRGB(1, 0));
    }

    @Test
    void reusesCompatibleDestinationImage() {
        WebPImage image = new WebPImage(
                1,
                2,
                1,
                2,
                true,
                false,
                false,
                1,
                0,
                WebPMetadata.empty(),
                List.of(new WebPFrame(1, 2, 0, new int[]{0x11223344, 0xFFEEDDCC}))
        );
        BufferedImage destination = new BufferedImage(1, 2, BufferedImage.TYPE_INT_ARGB);

        BufferedImage converted = WebPSwingUtils.fromWebPImage(image, destination);

        assertSame(destination, converted);
        assertEquals(0x11223344, converted.getRGB(0, 0));
        assertEquals(0xFFEEDDCC, converted.getRGB(0, 1));
    }

    @Test
    void reusesLargerCompatibleDestinationImageAndClearsUnusedPixels() {
        WebPImage image = new WebPImage(
                1,
                1,
                1,
                1,
                true,
                false,
                false,
                1,
                0,
                WebPMetadata.empty(),
                List.of(new WebPFrame(1, 1, 0, new int[]{0xAA112233}))
        );
        BufferedImage destination = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        destination.setRGB(1, 1, 0xFFFFFFFF);

        BufferedImage converted = WebPSwingUtils.fromWebPImage(image, destination);

        assertSame(destination, converted);
        assertEquals(0xAA112233, converted.getRGB(0, 0));
        assertEquals(0x00000000, converted.getRGB(1, 0));
        assertEquals(0x00000000, converted.getRGB(0, 1));
        assertEquals(0x00000000, converted.getRGB(1, 1));
    }

    @Test
    void replacesIncompatibleDestinationImage() {
        WebPImage image = new WebPImage(
                1,
                1,
                1,
                1,
                true,
                false,
                false,
                1,
                0,
                WebPMetadata.empty(),
                List.of(new WebPFrame(1, 1, 0, new int[]{0x7F010203}))
        );
        BufferedImage destination = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        BufferedImage converted = WebPSwingUtils.fromWebPImage(image, destination);

        assertNotSame(destination, converted);
        assertEquals(BufferedImage.TYPE_INT_ARGB, converted.getType());
        assertEquals(0x7F010203, converted.getRGB(0, 0));
    }
}
