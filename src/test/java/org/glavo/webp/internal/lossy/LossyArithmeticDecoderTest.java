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

import org.glavo.webp.WebPException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Ports of [lossy/arithmetic_decoder.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossy/arithmetic_decoder.rs) unit tests.
@NotNullByDefault
final class LossyArithmeticDecoderTest {

    @Test
    void arithmeticDecoderHelloShort() throws Exception {
        LossyArithmeticDecoder decoder = new LossyArithmeticDecoder();
        decoder.init("hel".getBytes(StandardCharsets.US_ASCII));

        LossyArithmeticDecoder.BitResultAccumulator accumulator = decoder.startAccumulatedResult();
        assertFalse(decoder.readFlag().orAccumulate(accumulator));
        assertTrue(decoder.readBool(10).orAccumulate(accumulator));
        assertFalse(decoder.readBool(250).orAccumulate(accumulator));
        assertEquals(1, decoder.readLiteral(1).orAccumulate(accumulator));
        assertEquals(5, decoder.readLiteral(3).orAccumulate(accumulator));
        assertEquals(64, decoder.readLiteral(8).orAccumulate(accumulator));
        assertEquals(185, decoder.readLiteral(8).orAccumulate(accumulator));
        assertTrue(decoder.check(accumulator, Boolean.TRUE));
    }

    @Test
    void arithmeticDecoderHelloLong() throws Exception {
        LossyArithmeticDecoder decoder = new LossyArithmeticDecoder();
        decoder.init("hello world".getBytes(StandardCharsets.US_ASCII));

        LossyArithmeticDecoder.BitResultAccumulator accumulator = decoder.startAccumulatedResult();
        assertFalse(decoder.readFlag().orAccumulate(accumulator));
        assertTrue(decoder.readBool(10).orAccumulate(accumulator));
        assertFalse(decoder.readBool(250).orAccumulate(accumulator));
        assertEquals(1, decoder.readLiteral(1).orAccumulate(accumulator));
        assertEquals(5, decoder.readLiteral(3).orAccumulate(accumulator));
        assertEquals(64, decoder.readLiteral(8).orAccumulate(accumulator));
        assertEquals(185, decoder.readLiteral(8).orAccumulate(accumulator));
        assertEquals(31, decoder.readLiteral(8).orAccumulate(accumulator));
        assertTrue(decoder.check(accumulator, Boolean.TRUE));
    }

    @Test
    void arithmeticDecoderUninitializedReaderFailsCheck() {
        LossyArithmeticDecoder decoder = new LossyArithmeticDecoder();
        LossyArithmeticDecoder.BitResultAccumulator accumulator = decoder.startAccumulatedResult();
        decoder.readFlag().orAccumulate(accumulator);
        assertThrows(WebPException.class, () -> decoder.check(accumulator, Boolean.TRUE));
    }
}
