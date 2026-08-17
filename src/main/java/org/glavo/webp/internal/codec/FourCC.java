// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.codec;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.charset.StandardCharsets;

/// Packed integer representations of four-byte RIFF identifiers.
///
/// The first identifier byte occupies the least-significant eight bits so values can be read
/// directly from the little-endian buffers used by the RIFF parser. The predefined identifiers
/// are compile-time constants and can therefore be used as `switch` labels.
@NotNullByDefault
public final class FourCC {

    /// The `RIFF` container identifier.
    public static final int RIFF = 'R' | ('I' << 8) | ('F' << 16) | ('F' << 24);

    /// The `WEBP` RIFF form identifier.
    public static final int WEBP = 'W' | ('E' << 8) | ('B' << 16) | ('P' << 24);

    /// The `VP8 ` lossy image chunk identifier.
    public static final int VP8 = 'V' | ('P' << 8) | ('8' << 16) | (' ' << 24);

    /// The `VP8L` lossless image chunk identifier.
    public static final int VP8L = 'V' | ('P' << 8) | ('8' << 16) | ('L' << 24);

    /// The `VP8X` extended header chunk identifier.
    public static final int VP8X = 'V' | ('P' << 8) | ('8' << 16) | ('X' << 24);

    /// The `ANIM` animation parameters chunk identifier.
    public static final int ANIM = 'A' | ('N' << 8) | ('I' << 16) | ('M' << 24);

    /// The `ANMF` animation frame chunk identifier.
    public static final int ANMF = 'A' | ('N' << 8) | ('M' << 16) | ('F' << 24);

    /// The `ALPH` alpha data chunk identifier.
    public static final int ALPH = 'A' | ('L' << 8) | ('P' << 16) | ('H' << 24);

    /// The `ICCP` color profile chunk identifier.
    public static final int ICCP = 'I' | ('C' << 8) | ('C' << 16) | ('P' << 24);

    /// The `EXIF` metadata chunk identifier.
    public static final int EXIF = 'E' | ('X' << 8) | ('I' << 16) | ('F' << 24);

    /// The `XMP ` metadata chunk identifier.
    public static final int XMP = 'X' | ('M' << 8) | ('P' << 16) | (' ' << 24);

    /// Creates a FourCC from four raw bytes.
    ///
    /// @param b0 the first byte
    /// @param b1 the second byte
    /// @param b2 the third byte
    /// @param b3 the fourth byte
    /// @return the parsed FourCC value
    public static int of(byte b0, byte b1, byte b2, byte b3) {
        return b0 & 0xFF | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
    }

    /// Creates a FourCC from four ISO-8859-1 characters.
    ///
    /// @param fourCC the textual identifier
    /// @return the parsed FourCC value
    /// @throws IllegalArgumentException if the string is not exactly four characters long or any
    ///                                  character cannot be represented by one byte
    public static int of(String fourCC) {
        if (fourCC.length() != 4) {
            throw new IllegalArgumentException("Invalid fourCC: " + fourCC);
        }
        char ch0 = fourCC.charAt(0);
        char ch1 = fourCC.charAt(1);
        char ch2 = fourCC.charAt(2);
        char ch3 = fourCC.charAt(3);

        if (ch0 > 0xFF || ch1 > 0xFF || ch2 > 0xFF || ch3 > 0xFF) {
            throw new IllegalArgumentException("Invalid fourCC: " + fourCC);
        }

        return FourCC.of(
                (byte) ch0,
                (byte) ch1,
                (byte) ch2,
                (byte) ch3
        );
    }

    /// Returns the four-character ISO-8859-1 representation of a packed identifier.
    ///
    /// @param fourCC the packed FourCC value
    /// @return a four-character string preserving all identifier bytes
    public static String toString(int fourCC) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (fourCC & 0xFF);
        bytes[1] = (byte) ((fourCC >>> 8) & 0xFF);
        bytes[2] = (byte) ((fourCC >>> 16) & 0xFF);
        bytes[3] = (byte) ((fourCC >>> 24) & 0xFF);
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /// Prevents instantiation of this utility class.
    private FourCC() {
    }
}
