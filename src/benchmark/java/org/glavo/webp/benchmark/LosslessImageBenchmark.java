// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.benchmark;

import org.glavo.webp.WebPImage;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/// Measures complete lossless decoding for photographs, transparency, icons, and indexed images.
@NotNullByDefault
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class LosslessImageBenchmark {
    /// Holds an immutable encoded image loaded before measurement.
    @NotNullByDefault
    @State(Scope.Benchmark)
    public static class EncodedImage {
        /// Class-path resource covering one lossless image shape or encoding style.
        @Param({
                "jwebp-test-data/glavo-1920x1080-lossless.webp",
                "jwebp-test-data/hmcl-256x256-lossless.webp",
                "jwebp-test-data/hmcl-64x64-lossless.webp",
                "images/gallery2-1_webp_ll.webp",
                "firefox-webp-test-data/perf_srgb_alpha_lossless.webp",
                "go-image-webp-test-data/gopher-doc.8bpp.lossless.webp",
                "go-image-webp-test-data/gopher-doc.1bpp.lossless.webp"
        })
        public String resource = "";

        /// Encoded container reused by all invocations in this trial.
        private byte @Unmodifiable [] data = new byte[0];

        /// Loads the selected encoded resource outside the measured operation.
        ///
        /// @throws IOException if the resource cannot be read
        @Setup
        public void load() throws IOException {
            try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream("/" + resource), resource)) {
                data = input.readAllBytes();
            }
        }
    }

    /// Parses the container and decodes its pixels into newly allocated output storage.
    ///
    /// @param image the retained encoded input
    /// @return the complete decoded image
    /// @throws IOException if decoding fails
    @Benchmark
    public WebPImage decode(EncodedImage image) throws IOException {
        return WebPImage.read(new ByteArrayInputStream(image.data));
    }
}
