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

/// All relevant RIFF chunk identifiers used by WebP containers.
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
    UNKNOWN("");

    private final String fourCc;

    WebPRiffChunk(String fourCc) {
        this.fourCc = fourCc;
    }

    /// Maps a FourCC string to its chunk type.
    ///
    /// @param fourCc the ASCII chunk identifier
    /// @return the matching chunk type, or [#UNKNOWN]
    public static WebPRiffChunk fromFourCc(String fourCc) {
        for (WebPRiffChunk value : values()) {
            if (value != UNKNOWN && value.fourCc.equals(fourCc)) {
                return value;
            }
        }
        return UNKNOWN;
    }

    /// Returns the canonical FourCC string when one exists.
    ///
    /// @return the ASCII chunk identifier, or an empty string for [#UNKNOWN]
    public String fourCc() {
        return fourCc;
    }
}
