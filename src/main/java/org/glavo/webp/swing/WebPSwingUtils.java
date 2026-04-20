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
package org.glavo.webp.swing;

import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/// Swing helpers for converting decoded WebP content into `BufferedImage` instances.
@NotNullByDefault
public final class WebPSwingUtils {
    private WebPSwingUtils() {
    }

    /// Creates a `BufferedImage` from the first frame of a decoded WebP image.
    ///
    /// Animated WebP images are converted using their first presentation frame.
    ///
    /// @param img the decoded WebP image
    /// @return a `TYPE_INT_ARGB` buffered image
    public static BufferedImage fromWebPImage(WebPImage img) {
        return fromWebPImage(img, null);
    }

    /// Writes the first frame of a decoded WebP image into a `BufferedImage`.
    ///
    /// Animated WebP images are converted using their first presentation frame. If `bimg`
    /// is `null`, is too small, or is not `TYPE_INT_ARGB` / `TYPE_INT_ARGB_PRE`, a replacement
    /// image is allocated and returned.
    ///
    /// @param img  the decoded WebP image
    /// @param bimg the optional destination image to reuse
    /// @return the destination image containing the converted pixels
    public static BufferedImage fromWebPImage(WebPImage img, @Nullable BufferedImage bimg) {
        Objects.requireNonNull(img, "img");
        return fromWebPFrame(img.getFirstFrame(), bimg);
    }

    /// Creates a `BufferedImage` from one decoded WebP frame.
    ///
    /// @param frame the decoded frame
    /// @return a `TYPE_INT_ARGB` buffered image
    public static BufferedImage fromWebPFrame(WebPFrame frame) {
        return fromWebPFrame(frame, null);
    }

    /// Writes one decoded WebP frame into a `BufferedImage`.
    ///
    /// If `bimg` is `null`, is too small, or is not `TYPE_INT_ARGB` / `TYPE_INT_ARGB_PRE`,
    /// a replacement image is allocated and returned. A `TYPE_INT_RGB` destination can also
    /// be reused when the frame is fully opaque.
    ///
    /// @param frame the decoded frame
    /// @param bimg  the optional destination image to reuse
    /// @return the destination image containing the converted pixels
    public static BufferedImage fromWebPFrame(WebPFrame frame, @Nullable BufferedImage bimg) {
        Objects.requireNonNull(frame, "frame");

        int fw = frame.getWidth();
        int fh = frame.getHeight();
        int[] pixels = frame.getArgbArray();

        checkBimg:
        if (bimg != null) {
            switch (bimg.getType()) {
                case BufferedImage.TYPE_INT_ARGB -> {
                    // Ok
                }
                case BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB_PRE -> {
                    if (!checkWebPFrameOpaque(pixels)) {
                        bimg = null;
                        break checkBimg;
                    }
                }
                default -> {
                    // Unsupported pixel type
                    bimg = null;
                    break checkBimg;
                }
            }

            int bw = bimg.getWidth();
            int bh = bimg.getHeight();
            if (bw < fw || bh < fh) {
                bimg = null;
                break checkBimg;
            }

            if (fw < bw || fh < bh) {
                Graphics2D g2d = bimg.createGraphics();
                try {
                    g2d.setComposite(AlphaComposite.Clear);
                    g2d.fillRect(0, 0, bw, bh);
                } finally {
                    g2d.dispose();
                }
            }
        }

        if (bimg == null) {
            bimg = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
        }
        bimg.setRGB(0, 0, fw, fh, pixels, 0, fw);
        return bimg;
    }

    private static boolean checkWebPFrameOpaque(int[] pixels) {
        for (int pixel : pixels) {
            if (Argb.alpha(pixel) != 0xFF) {
                return false;
            }
        }
        return true;
    }

}
