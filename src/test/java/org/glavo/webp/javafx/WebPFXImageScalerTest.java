// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.javafx;

import org.glavo.webp.WebPException;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPPixelFormat;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Tests pixel storage prepared for JavaFX presentation.
@NotNullByDefault
final class WebPFXImageScalerTest {

    /// Verifies that compatible intrinsic-size heap storage can directly back a pixel buffer.
    ///
    /// @throws WebPException if the regression fixture cannot be decoded
    @Test
    void reusesCompatibleHeapPresentationStorage() throws WebPException {
        WebPFrame frame = decodeHeapPremultipliedFrame();
        IntBuffer source = frame.getPixels();
        WebPFXImageScaler.ScalePlan scalePlan = WebPFXImageScaler.ScalePlan.create(
                frame.getWidth(),
                frame.getHeight(),
                WebPFXImageOptions.DEFAULT
        );

        assertFalse(source.isDirect());
        IntBuffer presentation = WebPFXImageScaler.prepareStaticPixels(frame, scalePlan);

        assertFalse(presentation.isDirect());
        assertEquals(source, presentation);
    }

    /// Verifies that bulk copying does not mutate the source cursor and resets the target cursor.
    @Test
    void bulkCopyPreservesBufferState() {
        IntBuffer source = IntBuffer.wrap(new int[]{1, 2, 3});
        source.position(1);
        IntBuffer target = WebPFXImageStorage.allocate(3, 1);
        target.position(2);

        WebPFXImageScaler.copyAsArgbPre(source, WebPPixelFormat.INT_ARGB_PRE, target);

        assertEquals(1, source.position());
        assertEquals(3, source.limit());
        assertEquals(0, target.position());
        assertEquals(1, target.get(0));
        assertEquals(2, target.get(1));
        assertEquals(3, target.get(2));
    }

    /// Verifies scaling into a slice without modifying adjacent packed regions.
    ///
    /// @throws WebPException if the regression fixture cannot be decoded
    @Test
    void scalesIntoSuppliedPackedRegion() throws WebPException {
        WebPFrame frame = decodeHeapPremultipliedFrame();
        int targetWidth = Math.multiplyExact(frame.getWidth(), 2);
        int targetHeight = Math.multiplyExact(frame.getHeight(), 2);
        int pixelCount = Math.multiplyExact(targetWidth, targetHeight);
        int sentinel = 0x1357_9BDF;
        IntBuffer packed = WebPFXImageStorage.allocatePixels(pixelCount + 2);
        packed.put(0, sentinel);
        packed.put(pixelCount + 1, sentinel);
        IntBuffer target = packed.slice(1, pixelCount);
        WebPFXImageScaler.ScalePlan scalePlan = new WebPFXImageScaler.ScalePlan(
                frame.getWidth(),
                frame.getHeight(),
                targetWidth,
                targetHeight,
                false
        );

        WebPFXImageScaler.scaleAsArgbPre(frame, scalePlan, target);

        assertEquals(0, target.position());
        assertEquals(pixelCount, target.limit());
        assertEquals(frame.getPixels().get(0), target.get(0));
        assertEquals(sentinel, packed.get(0));
        assertEquals(sentinel, packed.get(pixelCount + 1));
    }

    /// Decodes the regression image into heap-backed premultiplied pixels.
    ///
    /// @return the decoded static frame
    /// @throws WebPException if the regression fixture cannot be decoded
    private static WebPFrame decodeHeapPremultipliedFrame() throws WebPException {
        InputStream source = Objects.requireNonNull(
                WebPFXImageScalerTest.class.getClassLoader().getResourceAsStream("images/regression-tiny.webp")
        );
        return WebPImage
                .read(source, WebPPixelFormat.INT_ARGB_PRE)
                .getFirstFrame();
    }
}
