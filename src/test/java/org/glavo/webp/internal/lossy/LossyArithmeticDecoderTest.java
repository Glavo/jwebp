// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Ports of [lossy/arithmetic_decoder.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/lossy/arithmetic_decoder.rs) unit tests.
@NotNullByDefault
final class LossyArithmeticDecoderTest {

    /// Verifies the expected prefix decoded from a three-byte partition.
    @Test
    void arithmeticDecoderHelloShort() throws Exception {
        LossyArithmeticDecoder decoder = new LossyArithmeticDecoder();
        decoder.init(ByteBuffer.wrap("hel".getBytes(StandardCharsets.US_ASCII)));

        assertFalse(decoder.readFlag());
        assertTrue(decoder.readBool(10));
        assertFalse(decoder.readBool(250));
        assertEquals(1, decoder.readLiteral(1));
        assertEquals(5, decoder.readLiteral(3));
        assertEquals(64, decoder.readLiteral(8));
        assertEquals(185, decoder.readLiteral(8));
        decoder.ensureNotPastEof();
    }

    /// Verifies the expected prefix decoded from a partition with trailing input.
    @Test
    void arithmeticDecoderHelloLong() throws Exception {
        LossyArithmeticDecoder decoder = new LossyArithmeticDecoder();
        decoder.init(ByteBuffer.wrap("hello world".getBytes(StandardCharsets.US_ASCII)));

        assertFalse(decoder.readFlag());
        assertTrue(decoder.readBool(10));
        assertFalse(decoder.readBool(250));
        assertEquals(1, decoder.readLiteral(1));
        assertEquals(5, decoder.readLiteral(3));
        assertEquals(64, decoder.readLiteral(8));
        assertEquals(185, decoder.readLiteral(8));
        assertEquals(31, decoder.readLiteral(8));
        decoder.ensureNotPastEof();
    }

    /// Verifies that reading an uninitialized decoder is reported as corrupt input.
    @Test
    void arithmeticDecoderUninitializedReaderFailsCheck() {
        LossyArithmeticDecoder decoder = new LossyArithmeticDecoder();
        decoder.readFlag();
        assertThrows(WebPException.class, decoder::ensureNotPastEof);
    }
}
