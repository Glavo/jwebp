// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.io;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.WebPException;
import org.glavo.webp.internal.codec.FourCC;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

/// Little-endian primitive reader backed by a reusable staging buffer.
///
/// The decoder frequently mixes scalar reads with byte-array reads that cross underlying I/O
/// boundaries. `BufferedInput` keeps unread bytes in an internal buffer so callers can consume
/// the stream as a sequence of little-endian primitives without worrying about partial reads.
///
/// The class is not thread-safe.
@NotNullByDefault
public sealed abstract class BufferedInput implements Closeable {
    /// Staging capacity for the widest scalar container field.
    private static final int DEFAULT_BUFFER_SIZE = Long.BYTES;

    /// Largest payload allocated at its declared size before any payload bytes are observed.
    private static final int MAX_PREALLOCATED_PAYLOAD_SIZE = 16 * 1024 * 1024;

    protected final ByteBuffer buffer;
    protected boolean closed = false;

    /// Creates an input with the default heap-backed staging buffer.
    protected BufferedInput() {
        this.buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.buffer.limit(0);
    }

    /// Creates an input that reads directly from an existing little-endian buffer.
    ///
    /// @param buffer the backing buffer, already configured for little-endian reads
    protected BufferedInput(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer");
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalArgumentException("buffer order must be little-endian");
        }

        this.buffer = buffer;
    }

    /// Fails when the input has already been closed.
    protected final void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("BufferedInput is closed");
        }
    }

    /// Ensures that the staging buffer exposes at least `required` unread bytes.
    ///
    /// @param required the minimum unread byte count
    /// @throws IOException if the underlying source is truncated, closed, or unreadable
    public final void ensureBufferRemaining(int required) throws IOException {
        ensureOpen();
        if (required < 0) {
            throw new IllegalArgumentException("required < 0: " + required);
        }

        if (buffer.remaining() < required) {
            fillBuffer(required);

            if (buffer.remaining() < required) {
                throw unexpectedEndOfInput();
            }
        }
    }

    /// Refills the internal buffer until at least `required` unread bytes remain.
    ///
    /// Implementations may assume that `required` is non-negative and larger than the current
    /// unread byte count. Stream-backed implementations should preserve existing unread bytes.
    ///
    /// @param required the minimum unread byte count needed by the caller
    /// @throws IOException if the underlying source is truncated, closed, or unreadable
    protected abstract void fillBuffer(int required) throws IOException;

    protected ByteBuffer prepareForFill(int required) throws IOException {
        ensureOpen();
        ByteBuffer buffer = this.buffer;
        if (required > buffer.capacity()) {
            throw new IllegalArgumentException(
                    "required exceeds internal buffer capacity: " + required + " > " + buffer.capacity()
            );
        }
        buffer.compact();
        return buffer;
    }

    protected final long skipBufferedBytes(long len) {
        int skipped = (int) Math.min(len, buffer.remaining());
        buffer.position(buffer.position() + skipped);
        return skipped;
    }

    protected final void clearBuffer() {
        buffer.position(0);
        buffer.limit(0);
    }

    private static WebPException unexpectedEndOfInput() {
        return new WebPException("Unexpected end of WebP stream");
    }

    private static void skipFully(InputStream input, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }

            if (input.read() < 0) {
                throw unexpectedEndOfInput();
            }
            remaining--;
        }
    }

    /// Reads a fixed number of bytes into a newly allocated array.
    ///
    /// @param len the number of bytes to read
    /// @return a newly allocated byte array
    /// @throws IOException if the source is truncated, closed, or unreadable
    public byte[] readByteArray(int len) throws IOException {
        byte[] result = allocateByteArray(len);
        if (result.length == 0) {
            return result;
        }

        ensureOpen();

        int offset = 0;
        while (offset < len) {
            if (!buffer.hasRemaining()) {
                int request = buffer.capacity() == 0 ? 1 : Math.min(len - offset, buffer.capacity());
                ensureBufferRemaining(request);
            }

            int chunk = Math.min(buffer.remaining(), len - offset);
            buffer.get(result, offset, chunk);
            offset += chunk;
        }
        return result;
    }

    /// Allocates storage for a fixed-size byte-array read after validating its length.
    ///
    /// @param len the requested array length
    /// @return a new byte array of exactly `len` bytes
    /// @throws IOException if the requested array cannot be represented safely
    private static byte[] allocateByteArray(int len) throws IOException {
        if (len < 0 || len >= Integer.MAX_VALUE - 8) {
            throw new IOException("Array length too large: " + len);
        }
        return new byte[len];
    }

    /// Skips exactly `len` bytes.
    ///
    /// Unlike `InputStream.skip(long)`, this method guarantees that either all requested bytes
    /// are discarded or an exception is thrown.
    ///
    /// @param len the number of bytes to skip
    /// @throws IOException if the source is truncated, closed, or unreadable
    public void skip(long len) throws IOException {
        if (len < 0) {
            throw new IllegalArgumentException("len < 0: " + len);
        }
        if (len == 0) {
            ensureOpen();
            return;
        }

        ensureOpen();

        long remaining = len - skipBufferedBytes(len);
        while (remaining > 0) {
            if (!buffer.hasRemaining()) {
                int request = buffer.capacity() == 0 ? 1 : (int) Math.min(remaining, buffer.capacity());
                ensureBufferRemaining(request);
            }

            remaining -= skipBufferedBytes(remaining);
        }
    }

    /// Reads a signed byte.
    ///
    /// @return the next byte
    /// @throws IOException if the source is truncated, closed, or unreadable
    public byte readByte() throws IOException {
        ensureBufferRemaining(Byte.BYTES);
        return buffer.get();
    }

    /// Reads an unsigned byte.
    ///
    /// @return the next byte widened to `int`
    /// @throws IOException if the source is truncated, closed, or unreadable
    public int readUnsignedByte() throws IOException {
        return Byte.toUnsignedInt(readByte());
    }

    /// Reads a FourCC identifier.
    ///
    /// @return the packed value with the first byte in the least-significant bits, compatible with
    ///         the constants in [FourCC]
    /// @throws IOException if the source is truncated, closed, or unreadable
    public int readFourCC() throws IOException {
        ensureBufferRemaining(Integer.BYTES);
        return buffer.getInt();
    }

    /// Reads a signed 16-bit little-endian integer.
    ///
    /// @return the next short value
    /// @throws IOException if the source is truncated, closed, or unreadable
    public short readShortLE() throws IOException {
        ensureBufferRemaining(Short.BYTES);
        return buffer.getShort();
    }

    /// Reads an unsigned 16-bit little-endian integer.
    ///
    /// @return the next unsigned short widened to `int`
    /// @throws IOException if the source is truncated, closed, or unreadable
    public int readUnsignedShortLE() throws IOException {
        return Short.toUnsignedInt(readShortLE());
    }

    /// Reads an unsigned 24-bit little-endian integer.
    ///
    /// @return the next unsigned 24-bit value widened to `int`
    /// @throws IOException if the source is truncated, closed, or unreadable
    public int readUnsignedInt24LE() throws IOException {
        ensureBufferRemaining(3);
        return Byte.toUnsignedInt(buffer.get())
                | (Byte.toUnsignedInt(buffer.get()) << 8)
                | (Byte.toUnsignedInt(buffer.get()) << 16);
    }

    /// Reads a signed 32-bit little-endian integer.
    ///
    /// @return the next int value
    /// @throws IOException if the source is truncated, closed, or unreadable
    public int readIntLE() throws IOException {
        ensureBufferRemaining(Integer.BYTES);
        return buffer.getInt();
    }

    /// Reads an unsigned 32-bit little-endian integer.
    ///
    /// @return the next unsigned int widened to `long`
    /// @throws IOException if the source is truncated, closed, or unreadable
    public long readUnsignedIntLE() throws IOException {
        return Integer.toUnsignedLong(readIntLE());
    }

    /// Reads a signed 64-bit little-endian integer.
    ///
    /// @return the next long value
    /// @throws IOException if the source is truncated, closed, or unreadable
    public long readLongLE() throws IOException {
        ensureBufferRemaining(Long.BYTES);
        return buffer.getLong();
    }

    /// `BufferedInput` backed by an `InputStream`.
    ///
    /// The implementation uses a heap buffer so bytes can be read directly into the buffer's
    /// backing array without an extra copy.
    public static final class OfInputStream extends BufferedInput {
        private final InputStream input;

        /// Creates a buffered view of the supplied stream.
        ///
        /// @param input the stream to read from
        public OfInputStream(InputStream input) {
            super();
            this.input = Objects.requireNonNull(input, "input");
        }

        /// Reads payload bytes directly into their retained array after draining staged bytes.
        ///
        /// @param len the number of bytes to read
        /// @return a newly allocated byte array
        /// @throws IOException if the source is truncated, closed, or unreadable
        @Override
        public byte[] readByteArray(int len) throws IOException {
            if (len > MAX_PREALLOCATED_PAYLOAD_SIZE) {
                return readLargeByteArray(len);
            }

            byte[] result = allocateByteArray(len);
            if (result.length == 0) {
                return result;
            }

            ensureOpen();
            int offset = Math.min(buffer.remaining(), len);
            buffer.get(result, 0, offset);
            while (offset < len) {
                int read = input.read(result, offset, len - offset);
                if (read < 0) {
                    throw unexpectedEndOfInput();
                }
                if (read == 0) {
                    int value = input.read();
                    if (value < 0) {
                        throw unexpectedEndOfInput();
                    }
                    result[offset++] = (byte) value;
                } else {
                    offset += read;
                }
            }
            return result;
        }

        /// Reads a large declared payload without allocating its full size before reaching it.
        ///
        /// This prevents a short stream with a forged chunk length from forcing an allocation of
        /// that length. A complete large payload is still returned as one exact-sized array.
        ///
        /// @param len the declared payload length
        /// @return the complete payload
        /// @throws IOException if the source is truncated, closed, or unreadable
        private byte[] readLargeByteArray(int len) throws IOException {
            ensureOpen();

            int prefixLength = Math.min(buffer.remaining(), len);
            byte[] prefix = new byte[prefixLength];
            buffer.get(prefix);

            byte[] suffix = input.readNBytes(len - prefixLength);
            if (suffix.length != len - prefixLength) {
                throw unexpectedEndOfInput();
            }
            if (prefixLength == 0) {
                return suffix;
            }

            byte[] result = new byte[len];
            System.arraycopy(prefix, 0, result, 0, prefixLength);
            System.arraycopy(suffix, 0, result, prefixLength, suffix.length);
            return result;
        }

        @Override
        protected void fillBuffer(int required) throws IOException {
            ByteBuffer buffer1 = prepareForFill(required);
            if (!buffer1.hasArray()) {
                throw new IllegalStateException("InputStream refill requires an array-backed buffer");
            }

            byte[] array = buffer1.array();
            int arrayOffset = buffer1.arrayOffset();

            try {
                while (buffer1.position() < required) {
                    int read = input.read(array, arrayOffset + buffer1.position(), buffer1.remaining());
                    if (read < 0) {
                        throw unexpectedEndOfInput();
                    }

                    if (read == 0) {
                        int value = input.read();
                        if (value < 0) {
                            throw unexpectedEndOfInput();
                        }
                        array[arrayOffset + buffer1.position()] = (byte) value;
                        buffer1.position(buffer1.position() + 1);
                        continue;
                    }

                    buffer1.position(buffer1.position() + read);
                }
            } finally {
                buffer1.flip();
            }
        }

        @Override
        public void skip(long len) throws IOException {
            if (len < 0) {
                throw new IllegalArgumentException("len < 0: " + len);
            }
            if (len == 0) {
                ensureOpen();
                return;
            }

            ensureOpen();

            long remaining = len - skipBufferedBytes(len);
            if (remaining == 0) {
                return;
            }

            clearBuffer();
            skipFully(input, remaining);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            input.close();
        }
    }

    /// `BufferedInput` backed by a `ReadableByteChannel`.
    ///
    /// Payload bytes are read directly into their retained arrays; only scalar container fields
    /// pass through the small heap staging buffer.
    public static final class OfByteChannel extends BufferedInput {
        private final ReadableByteChannel channel;

        /// Creates a buffered view of the supplied channel.
        ///
        /// @param channel the channel to read from
        public OfByteChannel(ReadableByteChannel channel) {
            super();
            this.channel = Objects.requireNonNull(channel, "channel");
        }

        /// Reads payload bytes directly into their retained array after draining staged bytes.
        ///
        /// @param len the number of bytes to read
        /// @return a newly allocated byte array
        /// @throws IOException if the source is truncated, closed, or unreadable
        @Override
        public byte[] readByteArray(int len) throws IOException {
            if (len == 0) {
                return allocateByteArray(0);
            }

            ensureOpen();
            if (channel instanceof SeekableByteChannel seekableChannel) {
                long available = seekableChannel.size() - seekableChannel.position();
                long required = (long) len - Math.min(buffer.remaining(), len);
                if (available < 0 || required > available) {
                    throw unexpectedEndOfInput();
                }
            }

            byte[] result = allocateByteArray(len);
            int offset = Math.min(buffer.remaining(), len);
            buffer.get(result, 0, offset);
            ByteBuffer destination = ByteBuffer.wrap(result, offset, len - offset);
            while (destination.hasRemaining()) {
                int read = channel.read(destination);
                if (read < 0) {
                    throw unexpectedEndOfInput();
                }
                if (read == 0) {
                    throw new IOException("ReadableByteChannel made no progress while reading payload bytes");
                }
            }
            return result;
        }

        @Override
        protected void fillBuffer(int required) throws IOException {
            ByteBuffer buffer1 = prepareForFill(required);

            try {
                while (buffer1.position() < required) {
                    int read = channel.read(buffer1);
                    if (read < 0) {
                        throw unexpectedEndOfInput();
                    }
                    if (read == 0) {
                        throw new IOException("ReadableByteChannel made no progress while filling the buffer");
                    }
                }
            } finally {
                buffer1.flip();
            }
        }

        @Override
        public void skip(long len) throws IOException {
            if (len < 0) {
                throw new IllegalArgumentException("len < 0: " + len);
            }
            if (len == 0) {
                ensureOpen();
                return;
            }

            ensureOpen();

            long remaining = len - skipBufferedBytes(len);
            if (remaining == 0) {
                return;
            }

            if (channel instanceof SeekableByteChannel seekableChannel) {
                long position = seekableChannel.position();
                long size = seekableChannel.size();
                long available = size - position;
                if (available < 0 || remaining > available) {
                    throw unexpectedEndOfInput();
                }

                clearBuffer();
                seekableChannel.position(position + remaining);
                return;
            }

            ByteBuffer discardBuffer = buffer;
            discardBuffer.clear();
            try {
                while (remaining > 0) {
                    discardBuffer.limit((int) Math.min(remaining, discardBuffer.capacity()));

                    int read = channel.read(discardBuffer);
                    if (read < 0) {
                        throw unexpectedEndOfInput();
                    }
                    if (read == 0) {
                        throw new IOException("ReadableByteChannel made no progress while skipping bytes");
                    }

                    remaining -= read;
                    discardBuffer.clear();
                }
            } finally {
                clearBuffer();
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            channel.close();
        }
    }

    /// `BufferedInput` backed directly by an existing `ByteBuffer`.
    ///
    /// The wrapper reads from a little-endian slice of the supplied buffer, so consuming bytes
    /// does not mutate the caller's `position()` or `limit()`.
    public static final class OfByteBuffer extends BufferedInput {
        /// Creates a buffered view of the supplied byte buffer.
        ///
        /// @param buffer the buffer to read from starting at its current position
        public OfByteBuffer(ByteBuffer buffer) {
            super(Objects.requireNonNull(buffer, "buffer").slice().order(ByteOrder.LITTLE_ENDIAN));
        }

        @Override
        protected void fillBuffer(int required) throws IOException {
            throw unexpectedEndOfInput();
        }

        /// Returns the unread byte count in the wrapped buffer.
        ///
        /// @return the unread byte count
        /// @throws IOException if the reader has been closed
        public int remaining() throws IOException {
            ensureOpen();
            return buffer.remaining();
        }

        @Override
        public void skip(long len) throws IOException {
            if (len < 0) {
                throw new IllegalArgumentException("len < 0: " + len);
            }
            if (len == 0) {
                ensureOpen();
                return;
            }

            ensureOpen();

            if (len > buffer.remaining()) {
                throw unexpectedEndOfInput();
            }

            buffer.position(buffer.position() + Math.toIntExact(len));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
