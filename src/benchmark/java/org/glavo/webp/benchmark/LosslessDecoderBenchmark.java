// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.benchmark;

import org.glavo.webp.internal.codec.ParsedFrameDescriptor;
import org.glavo.webp.internal.codec.WebPSequentialParser;
import org.glavo.webp.internal.io.BufferedInput;
import org.glavo.webp.internal.lossless.LosslessDecoder;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/// Measures VP8L decoding with retained workspaces, excluding container parsing and output allocation.
@NotNullByDefault
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class LosslessDecoderBenchmark {
    /// Holds one encoded frame and thread-confined decode storage.
    @NotNullByDefault
    @State(Scope.Thread)
    public static class ReusedFrame {
        /// Basename of the lossless benchmark image.
        @Param({"hmcl-64x64", "hmcl-256x256", "glavo-1920x1080"})
        public String image = "";

        /// Encoded VP8L payload retained throughout the trial.
        private byte @Unmodifiable [] payload = new byte[0];

        /// Expected image width in pixels.
        private int width;

        /// Expected image height in pixels.
        private int height;

        /// Destination overwritten by every invocation.
        private int[] output = new int[0];

        /// Decoder whose workspaces are reused by consecutive invocations.
        private final LosslessDecoder decoder = new LosslessDecoder(new byte[0]);

        /// Parses the test input and allocates output storage outside the measured operation.
        ///
        /// @throws IOException if the benchmark resource cannot be read or parsed
        @Setup
        public void load() throws IOException {
            String path = "/jwebp-test-data/" + image + "-lossless.webp";
            try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(path), path);
                 BufferedInput buffered = new BufferedInput.OfInputStream(input)) {
                ParsedFrameDescriptor frame = WebPSequentialParser.parse(buffered).frames().get(0);
                payload = frame.imageChunk();
                width = frame.width();
                height = frame.height();
                output = new int[Math.multiplyExact(width, height)];
            }
        }
    }

    /// Decodes one image while retaining input, output, and decoder workspaces.
    ///
    /// @param frame the thread-confined benchmark state
    /// @return the reused output array, overwritten by the next invocation
    /// @throws IOException if decoding fails
    @Benchmark
    public int[] decodeReused(ReusedFrame frame) throws IOException {
        frame.decoder.resetInput(frame.payload);
        frame.decoder.decodeFrame(frame.width, frame.height, false, frame.output);
        return frame.output;
    }
}
