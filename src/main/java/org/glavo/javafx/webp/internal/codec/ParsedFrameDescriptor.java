package org.glavo.javafx.webp.internal.codec;

/// Encoded frame data extracted from a WebP container.
///
/// @param x the frame x offset within the canvas
/// @param y the frame y offset within the canvas
/// @param width the frame width
/// @param height the frame height
/// @param durationMillis the presentation duration in milliseconds
/// @param useAlphaBlending whether the frame should be blended with the previous canvas contents
/// @param disposeToBackground whether the covered area should be cleared before the next frame
/// @param lossless whether the image chunk uses VP8L lossless compression
/// @param alphaChunk the optional ALPH payload, or `null`
/// @param imageChunk the VP8 or VP8L payload
public record ParsedFrameDescriptor(
        int x,
        int y,
        int width,
        int height,
        int durationMillis,
        boolean useAlphaBlending,
        boolean disposeToBackground,
        boolean lossless,
        byte[] alphaChunk,
        byte[] imageChunk
) {
}
