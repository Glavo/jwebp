package org.glavo.javafx.webp.internal;

import org.glavo.javafx.webp.LoopCount;
import org.glavo.javafx.webp.WebPMetadata;

import java.util.List;

/// Immutable summary of the parsed WebP container.
///
/// @param sourceWidth the intrinsic canvas width
/// @param sourceHeight the intrinsic canvas height
/// @param hasAlpha whether the container declares transparency support
/// @param animated whether the container is animated
/// @param lossy whether any frame uses lossy VP8 compression
/// @param loopCount the animation loop count
/// @param loopDurationMillis the total duration of one animation loop
/// @param metadata raw ICC/EXIF/XMP metadata
/// @param frames frame timing information in presentation order
public record WebPContainerInfo(
        int sourceWidth,
        int sourceHeight,
        boolean hasAlpha,
        boolean animated,
        boolean lossy,
        LoopCount loopCount,
        long loopDurationMillis,
        WebPMetadata metadata,
        List<WebPFrameInfo> frames
) {
}
