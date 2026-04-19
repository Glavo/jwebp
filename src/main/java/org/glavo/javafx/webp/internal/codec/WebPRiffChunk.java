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
package org.glavo.javafx.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/// All relevant RIFF chunk identifiers used by WebP containers.
@NotNullByDefault
public enum WebPRiffChunk {
    RIFF("RIFF"),
    WEBP("WEBP"),
    VP8("VP8 "),
    VP8L("VP8L"),
    VP8X("VP8X"),
    ANIM("ANIM"),
    ANMF("ANMF"),
    ALPH("ALPH"),
    ICCP("ICCP"),
    EXIF("EXIF"),
    XMP("XMP "),
    UNKNOWN(null);

    private static final WebPRiffChunk[] SORTED_CHUNKS = Arrays.stream(values())
            .filter(c -> c.fourCC != null)
            .sorted(Comparator.comparing(c -> c.fourCC))
            .toArray(WebPRiffChunk[]::new);

    @SuppressWarnings("DataFlowIssue")
    private static final FourCC[] SORTED_FOURCC_VALUES = Arrays.stream(SORTED_CHUNKS)
            .map(c -> c.fourCC)
            .toArray(FourCC[]::new);

    private final @Nullable FourCC fourCC;

    WebPRiffChunk(@Nullable String fourCC) {
        this.fourCC = fourCC != null ? FourCC.of(fourCC) : null;
    }

    /// Maps a FourCC identifier to its chunk type.
    ///
    /// @param fourCC the chunk identifier
    /// @return the matching chunk type, or [#UNKNOWN]
    public static WebPRiffChunk fromFourCC(FourCC fourCC) {
        int index = Arrays.binarySearch(SORTED_FOURCC_VALUES, fourCC);
        return index >= 0 ? SORTED_CHUNKS[index] : UNKNOWN;
    }

    /// Returns the canonical FourCC value when one exists.
    ///
    /// @return the chunk identifier.
    /// @throws IllegalStateException if the chunk has no FourCC identifier
    public FourCC fourCC() {
        if (fourCC == null) {
            throw new IllegalStateException("No FourCC for " + this);
        }
        return fourCC;
    }
}
