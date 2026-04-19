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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/// Four-byte RIFF identifier stored as raw bytes.
///
/// WebP chunk types, the `RIFF` container marker and the `WEBP` signature are all encoded as
/// four ASCII bytes. Keeping them in a dedicated value type avoids repeated string allocation in
/// the parser and makes identifier comparisons explicit.
@NotNullByDefault
public record FourCC(int value) implements Comparable<FourCC> {

    /// Creates a FourCC from four raw bytes.
    ///
    /// @param b0 the first byte
    /// @param b1 the second byte
    /// @param b2 the third byte
    /// @param b3 the fourth byte
    /// @return the parsed FourCC value
    public static FourCC of(byte b0, byte b1, byte b2, byte b3) {
        return new FourCC(
                ((b0 & 0xFF) << 24)
                        | ((b1 & 0xFF) << 16)
                        | ((b2 & 0xFF) << 8)
                        | (b3 & 0xFF)
        );
    }

    /// Creates a FourCC from a byte array.
    ///
    /// @param bytes the four-byte identifier
    /// @return the parsed FourCC value
    /// @throws IllegalArgumentException if the array is not exactly four bytes long
    public static FourCC of(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Invalid fourCC: " + Arrays.toString(bytes));
        }
        return of(bytes[0], bytes[1], bytes[2], bytes[3]);
    }

    /// Creates a FourCC from a four-character ASCII string.
    ///
    /// @param fourCC the textual identifier
    /// @return the parsed FourCC value
    /// @throws IllegalArgumentException if the string is not exactly four characters long
    public static FourCC of(String fourCC) {
        if (fourCC.length() != 4) {
            throw new IllegalArgumentException("Invalid fourCC: " + fourCC);
        }
        var ch0 = fourCC.charAt(0);
        var ch1 = fourCC.charAt(1);
        var ch2 = fourCC.charAt(2);
        var ch3 = fourCC.charAt(3);

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

    @Override
    public int compareTo(FourCC that) {
        return Integer.compare(value, that.value);
    }

    /// Returns the canonical ASCII representation.
    @Override
    public String toString() {
        return new String(new byte[]{
                (byte) ((value >>> 24) & 0xFF),
                (byte) ((value >>> 16) & 0xFF),
                (byte) ((value >>> 8) & 0xFF),
                (byte) (value & 0xFF)
        }, StandardCharsets.US_ASCII);
    }
}
