package org.glavo.javafx.webp.internal.codec;

import org.glavo.javafx.webp.LoopCount;
import org.glavo.javafx.webp.WebPMetadata;

import java.util.List;

/// Pure-Java container parse result used by the decoder backend.
///
/// @param sourceWidth the canvas width
/// @param sourceHeight the canvas height
/// @param hasAlpha whether the container declares transparency support
/// @param animated whether the container is animated
/// @param lossy whether any frame uses VP8 lossy compression
/// @param loopCount the animation loop count
/// @param loopDurationMillis the total duration of one animation loop
/// @param metadata raw ICC/EXIF/XMP metadata
/// @param backgroundColorHint the animation background color hint, or `null`
/// @param frames encoded frame descriptors in presentation order
public record ParsedWebPImage(
        int sourceWidth,
        int sourceHeight,
        boolean hasAlpha,
        boolean animated,
        boolean lossy,
        LoopCount loopCount,
        long loopDurationMillis,
        WebPMetadata metadata,
        byte[] backgroundColorHint,
        List<ParsedFrameDescriptor> frames
) {
}
