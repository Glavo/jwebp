package org.glavo.javafx.webp.internal;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Buffered-image utilities used by the Image I/O based decoder backend.
 */
public final class BufferedImages {

    private BufferedImages() {
    }

    /**
     * Scales an image according to the supplied plan.
     *
     * <p>Smooth downscaling uses a multi-pass bilinear strategy to avoid the coarse artifacts that
     * appear when a large image is reduced in a single step. Fast scaling uses a single nearest-
     * neighbor pass.
     *
     * @param source the decoded source frame
     * @param scalePlan the requested target dimensions and filtering mode
     * @return a buffered image in {@code TYPE_INT_ARGB} format
     */
    public static BufferedImage scale(BufferedImage source, ScalePlan scalePlan) {
        BufferedImage current = toIntArgb(source);
        if (current.getWidth() == scalePlan.targetWidth() && current.getHeight() == scalePlan.targetHeight()) {
            return current;
        }

        if (!scalePlan.smooth()) {
            return scaleSingleStep(current, scalePlan.targetWidth(), scalePlan.targetHeight(), RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }

        if (scalePlan.targetWidth() < current.getWidth() || scalePlan.targetHeight() < current.getHeight()) {
            current = scaleMultiStep(current, scalePlan.targetWidth(), scalePlan.targetHeight());
        }
        if (current.getWidth() != scalePlan.targetWidth() || current.getHeight() != scalePlan.targetHeight()) {
            current = scaleSingleStep(current, scalePlan.targetWidth(), scalePlan.targetHeight(), RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return current;
    }

    /**
     * Converts an image to tightly packed non-premultiplied RGBA bytes.
     *
     * @param image the image to convert
     * @return tightly packed RGBA pixels
     */
    public static byte[] toRgba(BufferedImage image) {
        BufferedImage argb = toIntArgb(image);
        int width = argb.getWidth();
        int height = argb.getHeight();
        int[] pixels = argb.getRGB(0, 0, width, height, null, 0, width);
        byte[] rgba = new byte[width * height * 4];

        for (int index = 0, pixelIndex = 0; pixelIndex < pixels.length; pixelIndex++) {
            int argbPixel = pixels[pixelIndex];
            rgba[index++] = (byte) ((argbPixel >>> 16) & 0xFF);
            rgba[index++] = (byte) ((argbPixel >>> 8) & 0xFF);
            rgba[index++] = (byte) (argbPixel & 0xFF);
            rgba[index++] = (byte) ((argbPixel >>> 24) & 0xFF);
        }

        return rgba;
    }

    private static BufferedImage toIntArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static BufferedImage scaleMultiStep(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();

        while (width != targetWidth || height != targetHeight) {
            int nextWidth = width;
            int nextHeight = height;

            if (nextWidth > targetWidth) {
                nextWidth = Math.max(targetWidth, nextWidth / 2);
            }
            if (nextHeight > targetHeight) {
                nextHeight = Math.max(targetHeight, nextHeight / 2);
            }

            if (nextWidth == width && nextHeight == height) {
                break;
            }

            current = scaleSingleStep(current, nextWidth, nextHeight, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            width = nextWidth;
            height = nextHeight;
        }

        return current;
    }

    private static BufferedImage scaleSingleStep(BufferedImage source, int targetWidth, int targetHeight, Object interpolationHint) {
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }
}
