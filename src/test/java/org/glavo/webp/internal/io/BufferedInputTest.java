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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NotNullByDefault
final class BufferedInputTest {

    @Test
    void readsAcrossMultipleInputStreamRefills() throws Exception {
        byte[] payload = payload(9000);
        byte[] data = testData(payload);

        try (BufferedInput input = new BufferedInput.OfInputStream(new FragmentedInputStream(data, 127))) {
            assertReadsHeaderAndPayload(input, payload);
        }
    }

    @Test
    void readsAcrossMultipleChannelRefills() throws Exception {
        byte[] payload = payload(9000);
        byte[] data = testData(payload);

        try (BufferedInput input = new BufferedInput.OfByteChannel(Channels.newChannel(new FragmentedInputStream(data, 211)))) {
            assertReadsHeaderAndPayload(input, payload);
        }
    }

    @Test
    void byteBufferWrapperUsesSliceWithoutMutatingCallerState() throws Exception {
        byte[] data = ByteBuffer.allocate(6)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0x1234)
                .putInt(0x89ABCDEF)
                .array();

        ByteBuffer source = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        source.position(2);

        try (BufferedInput input = new BufferedInput.OfByteBuffer(source)) {
            assertEquals(0x89ABCDEF, input.readIntLE());
            assertEquals(2, source.position());
            assertEquals(ByteOrder.BIG_ENDIAN, source.order());
        }
    }

    @Test
    void truncatedInputThrowsWebPException() throws Exception {
        try (BufferedInput input = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(new byte[]{1, 2, 3}))) {
            assertThrows(WebPException.class, input::readIntLE);
        }
    }

    @Test
    void skipConsumesBytesAcrossMultipleRefills() throws Exception {
        byte[] payload = payload(9000);
        byte[] data = testData(payload);

        try (BufferedInput input = new BufferedInput.OfInputStream(new FragmentedInputStream(data, 113))) {
            input.skip(1 + 2 + 4 + 8L);
            assertArrayEquals(payload, input.readByteArray(payload.length));
        }
    }

    @Test
    void skipThrowsWhenSourceIsTruncated() throws Exception {
        try (BufferedInput input = new BufferedInput.OfByteBuffer(ByteBuffer.wrap(new byte[]{1, 2, 3}))) {
            assertThrows(WebPException.class, () -> input.skip(4));
        }
    }

    @Test
    void inputStreamSkipUsesUnderlyingSkipFastPath() throws Exception {
        byte[] data = payload(10000);
        TrackingSkipInputStream source = new TrackingSkipInputStream(data, 257);

        try (BufferedInput input = new BufferedInput.OfInputStream(source)) {
            input.skip(9000);

            assertTrue(source.skipCalls > 0);
            assertEquals(0, source.bulkReadCalls);
            assertEquals(0, source.singleByteReadCalls);
            assertEquals(Byte.toUnsignedInt(data[9000]), input.readUnsignedByte());
        }
    }

    @Test
    void seekableChannelSkipUsesPositionFastPath() throws Exception {
        byte[] data = payload(10000);
        TrackingSeekableByteChannel channel = new TrackingSeekableByteChannel(data);

        try (BufferedInput input = new BufferedInput.OfByteChannel(channel)) {
            input.skip(9000);

            assertTrue(channel.positionSetCalls > 0);
            assertEquals(0, channel.readCalls);
            assertEquals(Byte.toUnsignedInt(data[9000]), input.readUnsignedByte());
        }
    }

    @Test
    void closeClosesUnderlyingStreamAndRejectsFurtherReads() throws Exception {
        TrackingInputStream source = new TrackingInputStream(new byte[]{1, 2, 3}, 2);
        BufferedInput input = new BufferedInput.OfInputStream(source);

        input.close();

        assertTrue(source.wasClosed);
        assertThrows(IOException.class, input::readByte);
    }

    private static void assertReadsHeaderAndPayload(BufferedInput input, byte[] payload) throws Exception {
        assertEquals(0xAB, input.readUnsignedByte());
        assertEquals(0x1234, input.readUnsignedShortLE());
        assertEquals(0x89ABCDEF, input.readIntLE());
        assertEquals(0x0123456789ABCDEFL, input.readLongLE());
        assertArrayEquals(payload, input.readByteArray(payload.length));
    }

    private static byte[] testData(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 2 + 4 + 8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0xAB);
        buffer.putShort((short) 0x1234);
        buffer.putInt(0x89ABCDEF);
        buffer.putLong(0x0123456789ABCDEFL);
        buffer.put(payload);
        return buffer.array();
    }

    private static byte[] payload(int length) {
        byte[] payload = new byte[length];
        for (int i = 0; i < length; i++) {
            payload[i] = (byte) i;
        }
        return payload;
    }

    private static class FragmentedInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int maxChunkSize;

        private FragmentedInputStream(byte[] data, int maxChunkSize) {
            this.delegate = new ByteArrayInputStream(data);
            this.maxChunkSize = maxChunkSize;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, Math.min(len, maxChunkSize));
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class TrackingInputStream extends FragmentedInputStream {
        private boolean wasClosed = false;

        private TrackingInputStream(byte[] data, int maxChunkSize) {
            super(data, maxChunkSize);
        }

        @Override
        public void close() throws IOException {
            wasClosed = true;
            super.close();
        }
    }

    private static final class TrackingSkipInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final int maxSkipPerCall;
        private int skipCalls = 0;
        private int bulkReadCalls = 0;
        private int singleByteReadCalls = 0;

        private TrackingSkipInputStream(byte[] data, int maxSkipPerCall) {
            this.delegate = new ByteArrayInputStream(data);
            this.maxSkipPerCall = maxSkipPerCall;
        }

        @Override
        public int read() {
            singleByteReadCalls++;
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            bulkReadCalls++;
            return delegate.read(b, off, len);
        }

        @Override
        public long skip(long n) {
            skipCalls++;
            return delegate.skip(Math.min(n, maxSkipPerCall));
        }
    }

    private static final class TrackingSeekableByteChannel implements SeekableByteChannel {
        private final byte[] data;
        private long position = 0;
        private boolean open = true;
        private int readCalls = 0;
        private int positionSetCalls = 0;

        private TrackingSeekableByteChannel(byte[] data) {
            this.data = data;
        }

        @Override
        public int read(ByteBuffer dst) {
            readCalls++;
            if (position >= data.length) {
                return -1;
            }

            int read = Math.min(dst.remaining(), data.length - (int) position);
            dst.put(data, (int) position, read);
            position += read;
            return read;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            positionSetCalls++;
            position = newPosition;
            return this;
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
