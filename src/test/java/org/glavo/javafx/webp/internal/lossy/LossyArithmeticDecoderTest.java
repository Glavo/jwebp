package org.glavo.javafx.webp.internal.lossy;

import org.glavo.javafx.webp.WebPException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Ports of [lossy/arithmetic_decoder.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossy/arithmetic_decoder.rs) unit tests.
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
