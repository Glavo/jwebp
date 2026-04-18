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
package org.glavo.javafx.webp.internal.lossless;

import org.glavo.javafx.webp.WebPException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Ports of [lossless/decoder/mod.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossless/decoder/mod.rs) bit-reader tests.
final class LosslessBitReaderTest {

    @Test
    void bitReadTest() throws Exception {
        LosslessBitReader bitReader = new LosslessBitReader(new byte[]{(byte) 0x9C, 0x41, (byte) 0xE1});

        assertEquals(4, bitReader.readBits(3));
        assertEquals(3, bitReader.readBits(2));
        assertEquals(12, bitReader.readBits(6));
        assertEquals(40, bitReader.readBits(10));
        assertEquals(7, bitReader.readBits(3));
    }

    @Test
    void bitReadErrorTest() throws Exception {
        LosslessBitReader bitReader = new LosslessBitReader(new byte[]{0x6A});

        assertEquals(2, bitReader.readBits(3));
        assertEquals(13, bitReader.readBits(5));
        assertThrows(WebPException.class, () -> bitReader.readBits(4));
    }
}
