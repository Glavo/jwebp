// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import org.glavo.webp.WebPMetadata;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Pure-Java container parse result used by the decoder backend.
///
/// @param sourceWidth the canvas width
/// @param sourceHeight the canvas height
/// @param hasAlpha whether the container declares transparency support
/// @param animated whether the container is animated
/// @param lossy whether any frame uses VP8 lossy compression
/// @param loopCount the animation loop count; `0` means infinite looping
/// @param loopDurationMillis the total duration of one animation loop
/// @param metadata raw ICC/EXIF/XMP metadata
/// @param backgroundColorHint the animation background color hint, or `null`
/// @param frames encoded frame descriptors in presentation order
@NotNullByDefault
public record ParsedWebPImage(
        int sourceWidth,
        int sourceHeight,
        boolean hasAlpha,
        boolean animated,
        boolean lossy,
        int loopCount,
        long loopDurationMillis,
        WebPMetadata metadata,
        byte @Nullable [] backgroundColorHint,
        @Unmodifiable List<ParsedFrameDescriptor> frames
) {
}
