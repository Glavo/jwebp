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

import org.glavo.webp.internal.io.BufferedInput;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests packed FourCC constants, conversion, and buffered input compatibility.
@NotNullByDefault
final class FourCCTest {

    /// Verifies that every predefined identifier preserves its canonical four-character spelling.
    @Test
    void predefinedIdentifiersUseCanonicalSpelling() {
        assertEquals("RIFF", FourCC.toString(FourCC.RIFF));
        assertEquals("WEBP", FourCC.toString(FourCC.WEBP));
        assertEquals("VP8 ", FourCC.toString(FourCC.VP8));
        assertEquals("VP8L", FourCC.toString(FourCC.VP8L));
        assertEquals("VP8X", FourCC.toString(FourCC.VP8X));
        assertEquals("ANIM", FourCC.toString(FourCC.ANIM));
        assertEquals("ANMF", FourCC.toString(FourCC.ANMF));
        assertEquals("ALPH", FourCC.toString(FourCC.ALPH));
        assertEquals("ICCP", FourCC.toString(FourCC.ICCP));
        assertEquals("EXIF", FourCC.toString(FourCC.EXIF));
        assertEquals("XMP ", FourCC.toString(FourCC.XMP));
    }

    /// Verifies that text and raw-byte construction use the stream's little-endian packing.
    @Test
    void packsIdentifiersInStreamByteOrder() {
        assertEquals(0x4646_4952, FourCC.RIFF);
        assertEquals(FourCC.RIFF, FourCC.of("RIFF"));
        assertEquals(FourCC.RIFF, FourCC.of((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F'));
    }

    /// Verifies that raw non-ASCII identifier bytes are preserved when formatted for diagnostics.
    @Test
    void formatsAllIdentifierBytesWithoutReplacement() {
        byte[] bytes = {0x00, 0x7F, (byte) 0x80, (byte) 0xFF};
        int value = FourCC.of(bytes[0], bytes[1], bytes[2], bytes[3]);

        assertEquals(new String(bytes, StandardCharsets.ISO_8859_1), FourCC.toString(value));
    }

    /// Verifies that textual identifiers must contain exactly four single-byte characters.
    @Test
    void rejectsInvalidTextualIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> FourCC.of("VP8"));
        assertThrows(IllegalArgumentException.class, () -> FourCC.of("ABCDE"));
        assertThrows(IllegalArgumentException.class, () -> FourCC.of("ABC\u0100"));
    }

    /// Verifies that the buffered reader produces values compatible with predefined constants.
    @Test
    void bufferedInputReadsPackedIdentifiers() throws Exception {
        ByteBuffer bytes = ByteBuffer.wrap("RIFF".getBytes(StandardCharsets.US_ASCII));
        try (BufferedInput input = new BufferedInput.OfByteBuffer(bytes)) {
            assertEquals(FourCC.RIFF, input.readFourCC());
        }
    }
}
