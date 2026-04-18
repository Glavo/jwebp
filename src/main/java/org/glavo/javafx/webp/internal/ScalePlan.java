package org.glavo.javafx.webp.internal;

import org.glavo.javafx.webp.WebPImageLoadOptions;

/**
 * Calculates the decoded output dimensions from JavaFX-style image loading options.
 */
public record ScalePlan(
        int sourceWidth,
        int sourceHeight,
        int targetWidth,
        int targetHeight,
        boolean smooth
) {

    /**
     * Computes a scale plan that mirrors the documented semantics of JavaFX {@code Image}.
     *
     * <p>The calculations intentionally happen once per image so that both eager and streaming
     * decoding share the same target dimensions.
     *
     * @param sourceWidth the intrinsic width
     * @param sourceHeight the intrinsic height
     * @param options the user-supplied load options
     * @return the resulting scale plan
     */
    public static ScalePlan create(int sourceWidth, int sourceHeight, WebPImageLoadOptions options) {
        int targetWidth = sourceWidth;
        int targetHeight = sourceHeight;

        double requestedWidth = options.getRequestedWidth();
        double requestedHeight = options.getRequestedHeight();

        if (options.isPreserveRatio()) {
            if (requestedWidth > 0 && requestedHeight > 0) {
                double scale = Math.min(requestedWidth / sourceWidth, requestedHeight / sourceHeight);
                targetWidth = clampDimension(sourceWidth * scale);
                targetHeight = clampDimension(sourceHeight * scale);
            } else if (requestedWidth > 0) {
                double scale = requestedWidth / sourceWidth;
                targetWidth = clampDimension(requestedWidth);
                targetHeight = clampDimension(sourceHeight * scale);
            } else if (requestedHeight > 0) {
                double scale = requestedHeight / sourceHeight;
                targetWidth = clampDimension(sourceWidth * scale);
                targetHeight = clampDimension(requestedHeight);
            }
        } else {
            if (requestedWidth > 0) {
                targetWidth = clampDimension(requestedWidth);
            }
            if (requestedHeight > 0) {
                targetHeight = clampDimension(requestedHeight);
            }
        }

        return new ScalePlan(sourceWidth, sourceHeight, targetWidth, targetHeight, options.isSmooth());
    }

    private static int clampDimension(double value) {
        return Math.max(1, (int) Math.round(value));
    }
}
