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
