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
package org.glavo.webp.internal.lossy;

import org.jetbrains.annotations.NotNullByDefault;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@NotNullByDefault
final class Vp8DecoderTest {

    private static final byte[] SIMPLE_WEBP = {
            0x52, 0x49, 0x46, 0x46, 0x3C, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50,
            0x38, 0x20, 0x30, 0x00, 0x00, 0x00, (byte) 0xD0, 0x01, 0x00, (byte) 0x9D, 0x01, 0x2A, 0x02, 0x00,
            0x02, 0x00, 0x02, 0x00, 0x34, 0x25, (byte) 0xA0, 0x02, 0x74, (byte) 0xBA, 0x01, (byte) 0xF8, 0x00, 0x03,
            (byte) 0xB0, 0x00, (byte) 0xFE, (byte) 0xF0, (byte) 0xC4, 0x0B, (byte) 0xFF, 0x20, (byte) 0xB9, 0x61, 0x75, (byte) 0xC8, (byte) 0xD7, (byte) 0xFF,
            0x20, 0x3F, (byte) 0xE4, 0x07, (byte) 0xFC, (byte) 0x80, (byte) 0xFF, (byte) 0xF8, (byte) 0xF2, 0x00, 0x00, 0x00
    };

    private static final int VP8_PAYLOAD_OFFSET = 20;
    private static final int VP8_PAYLOAD_SIZE = 0x30;

    @Test
    void byteBufferDecodePreservesCallerPositionAndDecodesUniformImage() throws Exception {
        byte[] payload = vp8Payload();
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        int[] decodedFromBuffer = Vp8Decoder.decodeArgb(buffer, false);

        assertEquals(0, buffer.position());
        assertArrayEquals(new int[]{
                decodedFromBuffer[0],
                decodedFromBuffer[0],
                decodedFromBuffer[0],
                decodedFromBuffer[0]
        }, decodedFromBuffer);
    }

    @Test
    void byteBufferHeaderDecodePreservesCallerPosition() throws Exception {
        byte[] payload = vp8Payload();
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        Vp8Frame headerFromBuffer = Vp8Decoder.decodeFrameHeader(buffer);

        assertEquals(0, buffer.position());
        assertEquals(2, headerFromBuffer.width);
        assertEquals(2, headerFromBuffer.height);
        assertEquals(0, headerFromBuffer.version);
        assertTrue(headerFromBuffer.keyframe);
        assertTrue(headerFromBuffer.forDisplay);
    }

    private static byte[] vp8Payload() {
        return Arrays.copyOfRange(
                SIMPLE_WEBP,
                VP8_PAYLOAD_OFFSET,
                VP8_PAYLOAD_OFFSET + VP8_PAYLOAD_SIZE
        );
    }
}
