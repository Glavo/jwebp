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
package org.glavo.javafx.webp.internal.io;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.javafx.webp.WebPException;
import org.glavo.javafx.webp.internal.codec.FourCC;

import java.util.Arrays;

/// Little-endian byte reader backed by an in-memory array.
///
/// The pure-Java decoder uses this reader for chunk payloads after the streaming container layer
/// has isolated them. The implementation favors predictable bounds checks and simple primitive read
/// helpers because the decode hot paths perform many small reads.
@NotNullByDefault
public final class ByteArrayReader {

    private final byte[] data;
    private int position;

    /// Creates a reader for the supplied byte array.
    ///
    /// @param data the byte array to read from
    public ByteArrayReader(byte[] data) {
        this.data = data;
    }

    /// Returns the current read position.
    ///
    /// @return the zero-based byte offset
    public int position() {
        return position;
    }

    /// Returns the number of unread bytes.
    ///
    /// @return the unread byte count
    public int remaining() {
        return data.length - position;
    }

    /// Returns the underlying array length.
    ///
    /// @return the total array length
    public int length() {
        return data.length;
    }

    /// Sets the current read position.
    ///
    /// @param position the new byte offset
    /// @throws WebPException if the position is outside the array
    public void position(int position) throws WebPException {
        if (position < 0 || position > data.length) {
            throw new WebPException("ByteArrayReader position out of bounds: " + position);
        }
        this.position = position;
    }

    /// Skips a fixed number of bytes.
    ///
    /// @param bytes the number of bytes to skip
    /// @throws WebPException if the skip would move beyond the end of the buffer
    public void skip(int bytes) throws WebPException {
        require(bytes);
        position += bytes;
    }

    /// Reads an unsigned byte.
    ///
    /// @return the unsigned byte value in the range `[0, 255]`
    /// @throws WebPException if the reader is already at the end of the array
    public int readU8() throws WebPException {
        require(1);
        return data[position++] & 0xFF;
    }

    /// Reads an unsigned 16-bit little-endian integer.
    ///
    /// @return the unsigned short value in the range `[0, 65535]`
    /// @throws WebPException if the buffer is truncated
    public int readU16LE() throws WebPException {
        require(2);
        int value = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
        position += 2;
        return value;
    }

    /// Reads an unsigned 24-bit little-endian integer.
    ///
    /// @return the unsigned 24-bit value
    /// @throws WebPException if the buffer is truncated
    public int readU24LE() throws WebPException {
        require(3);
        int value = (data[position] & 0xFF)
                | ((data[position + 1] & 0xFF) << 8)
                | ((data[position + 2] & 0xFF) << 16);
        position += 3;
        return value;
    }

    /// Reads a signed 32-bit little-endian integer.
    ///
    /// @return the 32-bit value
    /// @throws WebPException if the buffer is truncated
    public int readI32LE() throws WebPException {
        require(4);
        int value = (data[position] & 0xFF)
                | ((data[position + 1] & 0xFF) << 8)
                | ((data[position + 2] & 0xFF) << 16)
                | ((data[position + 3] & 0xFF) << 24);
        position += 4;
        return value;
    }

    /// Reads an unsigned 32-bit little-endian integer.
    ///
    /// @return the unsigned 32-bit value widened to `long`
    /// @throws WebPException if the buffer is truncated
    public long readU32LE() throws WebPException {
        return Integer.toUnsignedLong(readI32LE());
    }

    /// Reads a FourCC identifier.
    ///
    /// @return the chunk identifier
    /// @throws WebPException if the buffer is truncated
    public FourCC readFourCC() throws WebPException {
        return FourCC.of(readBytes(4));
    }

    /// Reads a fixed-size byte array.
    ///
    /// @param length the number of bytes to read
    /// @return a newly allocated byte array
    /// @throws WebPException if the buffer is truncated
    public byte[] readBytes(int length) throws WebPException {
        require(length);
        byte[] result = Arrays.copyOfRange(data, position, position + length);
        position += length;
        return result;
    }

    /// Returns a copy of the next `length` bytes without advancing the position.
    ///
    /// @param length the number of bytes to inspect
    /// @return a copied byte range
    /// @throws WebPException if the range extends past the end of the array
    public byte[] peekBytes(int length) throws WebPException {
        require(length);
        return Arrays.copyOfRange(data, position, position + length);
    }

    /// Returns a copy of the unread suffix.
    ///
    /// @return the unread bytes
    public byte[] readRemainingBytes() {
        return Arrays.copyOfRange(data, position, data.length);
    }

    /// Reads all remaining bytes and advances the position to the end of the array.
    ///
    /// @return the unread bytes
    public byte[] drain() {
        byte[] bytes = readRemainingBytes();
        position = data.length;
        return bytes;
    }

    private void require(int bytes) throws WebPException {
        if (bytes < 0 || position + bytes > data.length) {
            throw new WebPException("Unexpected end of WebP data");
        }
    }
}
