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
import org.openjdk.jmh.annotations.Param;
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

/// JMH benchmarks comparing this project against TwelveMonkeys and direct JavaFX PNG loading.
///
/// The comparison reuses the static images from jwebp-test-data for both implementations and is
/// intentionally limited to still images.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class StaticImageBenchmark {

    private static final String TEST_DATA_ROOT = "jwebp-test-data/";
    private static final WebPImageReaderSpi TWELVE_MONKEYS_SPI = new WebPImageReaderSpi();

    @State(Scope.Benchmark)
    public static class BenchmarkImages {
        @Param({
                "glavo-1920x1080",
                "hmcl-256x256",
                "hmcl-64x64",
        })
        public String image;

        byte[] lossyWebp;
        byte[] losslessWebp;
        byte[] png;
        byte[] jpg;

        @Setup
        public void load() throws IOException {
            lossyWebp = resourceBytes(TEST_DATA_ROOT + image + "-lossy.webp");
            losslessWebp = resourceBytes(TEST_DATA_ROOT + image + "-lossless.webp");
            png = resourceBytes(TEST_DATA_ROOT + image + ".png");
            jpg = resourceBytes(TEST_DATA_ROOT + image + ".jpg");
        }
    }

    @Benchmark
    public WebPImage jwebpLossy(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.lossyWebp));
    }

    @Benchmark
    public BufferedImage twelveMonkeysLossy(BenchmarkImages images) throws Exception {
        return readStillImageWithProvider(images.lossyWebp);
    }

    @Benchmark
    public Image jwebpLossyToJavaFX(BenchmarkImages images) throws Exception {
        return new WebPFXImage(WebPImage.read(new ByteArrayInputStream(images.lossyWebp)), false);
    }

    @Benchmark
    public Image twelveMonkeysLossyToJavaFX(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(readStillImageWithProvider(images.lossyWebp), null);
    }

    @Benchmark
    public WebPImage jwebpLossless(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.losslessWebp));
    }

    @Benchmark
    public BufferedImage twelveMonkeysLossless(BenchmarkImages images) throws Exception {
        return readStillImageWithProvider(images.losslessWebp);
    }

    @Benchmark
    public Image jwebpLosslessToJavaFX(BenchmarkImages images) throws Exception {
        return new WebPFXImage(WebPImage.read(new ByteArrayInputStream(images.losslessWebp)), false);
    }

    @Benchmark
    public Image twelveMonkeysLosslessToJavaFX(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(readStillImageWithProvider(images.losslessWebp), null);
    }

    @Benchmark
    public Image jfxPNG(BenchmarkImages images) {
        return new Image(new ByteArrayInputStream(images.png));
    }

    @Benchmark
    public Image jfxJPG(BenchmarkImages images) {
        return new Image(new ByteArrayInputStream(images.jpg));
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
        try (InputStream input = StaticImageBenchmark.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing benchmark resource: " + path);
            }
            return input.readAllBytes();
        }
    }
}
