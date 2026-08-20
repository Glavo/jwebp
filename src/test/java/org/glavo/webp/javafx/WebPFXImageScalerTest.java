// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.javafx;

import org.glavo.webp.WebPDecoder;
import org.glavo.webp.WebPException;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPPixelFormat;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests direct pixel storage prepared for JavaFX presentation.
@NotNullByDefault
final class WebPFXImageScalerTest {

    /// Verifies that newly allocated presentation storage is direct and uses native byte order.
    @Test
    void allocatesDirectPresentationStorage() {
        IntBuffer buffer = WebPFXImageScaler.allocateDirectBuffer(3, 2);

        assertTrue(buffer.isDirect());
        assertEquals(ByteOrder.nativeOrder(), buffer.order());
        assertEquals(6, buffer.capacity());
    }

    /// Verifies that a compatible heap frame is copied before it backs a JavaFX pixel buffer.
    ///
    /// @throws WebPException if the regression fixture cannot be decoded
    @Test
    void copiesCompatibleHeapFrameIntoDirectPresentationStorage() throws WebPException {
        WebPFrame frame = decodeHeapPremultipliedFrame();
        IntBuffer source = frame.getPixels();
        WebPFXImageScaler.ScalePlan scalePlan = WebPFXImageScaler.ScalePlan.create(
                frame.getWidth(),
                frame.getHeight(),
                WebPFXImageOptions.DEFAULT
        );

        assertFalse(source.isDirect());
        IntBuffer presentation = WebPFXImageScaler.prepareStaticPixels(frame, scalePlan);

        assertTrue(presentation.isDirect());
        assertEquals(source, presentation);
    }

    /// Verifies that bulk copying does not mutate the source cursor and resets the target cursor.
    @Test
    void bulkCopyPreservesBufferState() {
        IntBuffer source = IntBuffer.wrap(new int[]{1, 2, 3});
        source.position(1);
        IntBuffer target = WebPFXImageScaler.allocateDirectBuffer(3, 1);
        target.position(2);

        WebPFXImageScaler.copyAsArgbPre(source, WebPPixelFormat.INT_ARGB_PRE, target);

        assertEquals(1, source.position());
        assertEquals(3, source.limit());
        assertEquals(0, target.position());
        assertEquals(1, target.get(0));
        assertEquals(2, target.get(1));
        assertEquals(3, target.get(2));
    }

    /// Decodes the regression image into heap-backed premultiplied pixels.
    ///
    /// @return the decoded static frame
    /// @throws WebPException if the regression fixture cannot be decoded
    private static WebPFrame decodeHeapPremultipliedFrame() throws WebPException {
        InputStream source = Objects.requireNonNull(
                WebPFXImageScalerTest.class.getClassLoader().getResourceAsStream("images/regression-tiny.webp")
        );
        return WebPDecoder.DEFAULT
                .withPixelFormat(WebPPixelFormat.INT_ARGB_PRE)
                .read(source)
                .getFirstFrame();
    }
}
