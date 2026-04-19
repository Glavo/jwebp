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
package org.glavo.webp.internal.io;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.codec.FourCC;

import java.io.IOException;
import java.io.InputStream;

/// Primitive stream helpers for the sequential RIFF parser.
@NotNullByDefault
public final class InputStreams {

    private InputStreams() {
    }

    /// Reads a FourCC identifier from an input stream.
    ///
    /// @param input the source stream
    /// @return the chunk identifier
    /// @throws IOException if the stream is truncated or unreadable
    public static FourCC readFourCC(InputStream input) throws IOException {
        return FourCC.of(readFully(input, 4));
    }

    /// Reads an unsigned 16-bit little-endian integer.
    ///
    /// @param input the source stream
    /// @return the unsigned short value
    /// @throws IOException if the stream is truncated or unreadable
    public static int readU16LE(InputStream input) throws IOException {
        int b0 = readU8(input);
        int b1 = readU8(input);
        return b0 | (b1 << 8);
    }

    /// Reads an unsigned 24-bit little-endian integer.
    ///
    /// @param input the source stream
    /// @return the unsigned 24-bit value
    /// @throws IOException if the stream is truncated or unreadable
    public static int readU24LE(InputStream input) throws IOException {
        int b0 = readU8(input);
        int b1 = readU8(input);
        int b2 = readU8(input);
        return b0 | (b1 << 8) | (b2 << 16);
    }

    /// Reads an unsigned 32-bit little-endian integer.
    ///
    /// @param input the source stream
    /// @return the unsigned 32-bit value widened to `long`
    /// @throws IOException if the stream is truncated or unreadable
    public static long readU32LE(InputStream input) throws IOException {
        int b0 = readU8(input);
        int b1 = readU8(input);
        int b2 = readU8(input);
        int b3 = readU8(input);
        return Integer.toUnsignedLong(b0 | (b1 << 8) | (b2 << 16) | (b3 << 24));
    }

    /// Reads a single unsigned byte.
    ///
    /// @param input the source stream
    /// @return the unsigned byte value
    /// @throws IOException if the stream is truncated or unreadable
    public static int readU8(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new WebPException("Unexpected end of WebP stream");
        }
        return value;
    }

    /// Reads a fixed number of bytes.
    ///
    /// @param input the source stream
    /// @param length the exact number of bytes to read
    /// @return a newly allocated byte array
    /// @throws IOException if the stream is truncated or unreadable
    public static byte[] readFully(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) {
                throw new WebPException("Unexpected end of WebP stream");
            }
            offset += read;
        }
        return data;
    }

    /// Skips exactly the requested number of bytes.
    ///
    /// @param input the source stream
    /// @param length the number of bytes to skip
    /// @throws IOException if the stream is truncated or unreadable
    public static void skipFully(InputStream input, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }

            if (input.read() < 0) {
                throw new WebPException("Unexpected end of WebP stream");
            }
            remaining--;
        }
    }
}
