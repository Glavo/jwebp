package org.glavo.javafx.webp.internal;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/// JavaFX image conversion helpers.
public final class FxImages {

    private FxImages() {
    }

    /// Converts non-premultiplied RGBA bytes to a JavaFX [WritableImage].
    ///
    /// JavaFX writable pixel formats expect premultiplied BGRA data, so the method performs the
    /// required channel swizzle and alpha premultiplication explicitly.
    ///
    /// @param width the image width
    /// @param height the image height
    /// @param rgba tightly packed non-premultiplied RGBA pixels
    /// @return a new writable JavaFX image
    public static WritableImage toWritableImage(int width, int height, byte[] rgba) {
        byte[] bgraPre = new byte[rgba.length];
        for (int src = 0, dst = 0; src < rgba.length; src += 4, dst += 4) {
            int r = rgba[src] & 0xFF;
            int g = rgba[src + 1] & 0xFF;
            int b = rgba[src + 2] & 0xFF;
            int a = rgba[src + 3] & 0xFF;

            bgraPre[dst] = (byte) premultiply(b, a);
            bgraPre[dst + 1] = (byte) premultiply(g, a);
            bgraPre[dst + 2] = (byte) premultiply(r, a);
            bgraPre[dst + 3] = (byte) a;
        }

        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        writer.setPixels(0, 0, width, height, PixelFormat.getByteBgraPreInstance(), bgraPre, 0, width * 4);
        return image;
    }

    private static int premultiply(int component, int alpha) {
        if (alpha <= 0) {
            return 0;
        }
        if (alpha >= 255) {
            return component;
        }
        return (component * alpha + 127) / 255;
    }
}
