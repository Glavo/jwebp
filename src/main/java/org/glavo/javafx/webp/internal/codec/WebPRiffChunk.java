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
