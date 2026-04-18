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
