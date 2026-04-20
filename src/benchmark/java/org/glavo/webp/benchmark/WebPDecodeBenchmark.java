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
package org.glavo.webp.benchmark;

import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPImageLoadOptions;
import org.glavo.webp.WebPImageReader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/// JMH benchmarks for the public WebP decoding API.
///
/// The benchmarks reuse the checked-in test images so performance comparisons can be reproduced
/// against the same corpus used by correctness tests.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class WebPDecodeBenchmark {

    private static final WebPImageLoadOptions SCALED_LOSSY_OPTIONS = WebPImageLoadOptions.builder()
            .requestedWidth(180)
            .requestedHeight(96)
            .preserveRatio(true)
            .smooth(true)
            .build();

    @State(Scope.Benchmark)
    public static class BenchmarkImages {
        byte[] staticLossy;
        byte[] staticLosslessAlpha;
        byte[] animatedLossless;
        byte[] animatedLossy;

        @Setup
        public void load() throws IOException {
            staticLossy = resourceBytes("images/gallery1-1.webp");
            staticLosslessAlpha = resourceBytes("images/gallery2-1_webp_a.webp");
            animatedLossless = resourceBytes("images/animated-random_lossless.webp");
            animatedLossy = resourceBytes("images/animated-random_lossy.webp");
        }
    }

    @Benchmark
    public WebPImage decodeStaticLossy(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.staticLossy));
    }

    @Benchmark
    public WebPImage decodeStaticLosslessWithAlpha(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.staticLosslessAlpha));
    }

    @Benchmark
    public WebPImage decodeScaledStaticLossy(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.staticLossy), SCALED_LOSSY_OPTIONS);
    }

    @Benchmark
    public WebPImage decodeAnimatedLossless(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.animatedLossless));
    }

    @Benchmark
    public void streamAnimatedLossyFrames(BenchmarkImages images, Blackhole blackhole) throws Exception {
        try (WebPImageReader reader = WebPImageReader.open(new ByteArrayInputStream(images.animatedLossy))) {
            int frameCount = 0;
            long totalDurationMillis = 0L;
            while (true) {
                WebPFrame frame = reader.readNextFrame();
                if (frame == null) {
                    break;
                }

                frameCount++;
                totalDurationMillis += frame.getDurationMillis();
                blackhole.consume(frame);
            }

            blackhole.consume(frameCount);
            blackhole.consume(totalDurationMillis);
            blackhole.consume(reader.isComplete());
        }
    }

    private static byte[] resourceBytes(String path) throws IOException {
        try (InputStream input = WebPDecodeBenchmark.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing benchmark resource: " + path);
            }
            return input.readAllBytes();
        }
    }
}
