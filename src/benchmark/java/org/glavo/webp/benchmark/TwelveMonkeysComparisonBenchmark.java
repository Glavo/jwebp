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

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.glavo.webp.WebPImage;
import org.glavo.webp.javafx.WebPFXImage;
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

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/// JMH benchmarks comparing this project against TwelveMonkeys for still-image WebP decoding.
///
/// The comparison reuses the same checked-in sample images for both implementations and is
/// intentionally limited to still images.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TwelveMonkeysComparisonBenchmark {

    private static final WebPImageReaderSpi TWELVE_MONKEYS_SPI = new WebPImageReaderSpi();

    @State(Scope.Benchmark)
    public static class BenchmarkImages {
        byte[] staticLossy;
        byte[] staticLosslessAlpha;

        @Setup
        public void load() throws IOException {
            staticLossy = resourceBytes("images/gallery1-1.webp");
            staticLosslessAlpha = resourceBytes("images/gallery2-1_webp_a.webp");
        }
    }

    @Benchmark
    public WebPImage jwebpDecodeStaticLossy(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.staticLossy));
    }

    @Benchmark
    public BufferedImage twelveMonkeysDecodeStaticLossy(BenchmarkImages images) throws Exception {
        return readStillImageWithProvider(images.staticLossy);
    }

    @Benchmark
    public WebPImage jwebpDecodeStaticLosslessAlpha(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.staticLosslessAlpha));
    }

    @Benchmark
    public BufferedImage twelveMonkeysDecodeStaticLosslessAlpha(BenchmarkImages images) throws Exception {
        return readStillImageWithProvider(images.staticLosslessAlpha);
    }

    @Benchmark
    public Image jwebpToJavaFXStaticLossy(BenchmarkImages images) throws Exception {
        return new WebPFXImage(WebPImage.read(new ByteArrayInputStream(images.staticLossy)), false);
    }

    @Benchmark
    public Image twelveMonkeysToJavaFXStaticLossy(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(readStillImageWithProvider(images.staticLossy), null);
    }

    @Benchmark
    public Image jwebpToJavaFXStaticLosslessAlpha(BenchmarkImages images) throws Exception {
        return new WebPFXImage(WebPImage.read(new ByteArrayInputStream(images.staticLosslessAlpha)), false);
    }

    @Benchmark
    public Image twelveMonkeysToJavaFXStaticLosslessAlpha(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(readStillImageWithProvider(images.staticLosslessAlpha), null);
    }

    private static BufferedImage readStillImageWithProvider(byte[] bytes) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = TWELVE_MONKEYS_SPI.createReaderInstance();
            try {
                reader.setInput(input, true, true);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] resourceBytes(String path) throws IOException {
        try (InputStream input = TwelveMonkeysComparisonBenchmark.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing benchmark resource: " + path);
            }
            return input.readAllBytes();
        }
    }
}
