// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.benchmark;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import dev.matrixlab.webp4j.WebPCodec;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPImageReader;
import org.glavo.webp.WebPPixelFormat;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Objects;
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

    /// Measures lossy decoding directly into retained off-heap frame storage.
    ///
    /// @param images the selected benchmark inputs
    /// @return the decoded frame
    /// @throws Exception if decoding fails
    @Benchmark
    public WebPFrame jwebpLossyDirect(BenchmarkImages images) throws Exception {
        return readDirectFrame(images.lossyWebp);
    }

    @Benchmark
    public BufferedImage twelveMonkeysLossy(BenchmarkImages images) throws Exception {
        return readStillImageWithProvider(images.lossyWebp);
    }

    @Benchmark
    public Image jwebpLossyToJavaFX(BenchmarkImages images) throws Exception {
        return WebPFXImage.of(WebPImage.read(new ByteArrayInputStream(images.lossyWebp)));
    }

    @Benchmark
    public Image twelveMonkeysLossyToJavaFX(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(readStillImageWithProvider(images.lossyWebp), null);
    }

    @Benchmark
    public WebPImage jwebpLossless(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.losslessWebp));
    }

    /// Measures lossless decoding directly into retained off-heap frame storage.
    ///
    /// @param images the selected benchmark inputs
    /// @return the decoded frame
    /// @throws Exception if decoding fails
    @Benchmark
    public WebPFrame jwebpLosslessDirect(BenchmarkImages images) throws Exception {
        return readDirectFrame(images.losslessWebp);
    }

    /// Decodes one static WebP into caller-allocated direct storage.
    ///
    /// @param encoded the encoded WebP payload
    /// @return the decoded frame
    /// @throws Exception if opening, allocation, or decoding fails
    private static WebPFrame readDirectFrame(byte[] encoded) throws Exception {
        try (WebPImageReader reader = WebPImageReader.open(new ByteArrayInputStream(encoded))) {
            int pixelCount = Math.multiplyExact(reader.getWidth(), reader.getHeight());
            IntBuffer storage = ByteBuffer
                    .allocateDirect(Math.multiplyExact(pixelCount, Integer.BYTES))
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            return Objects.requireNonNull(
                    reader.readNextFrame(WebPPixelFormat.INT_ARGB, storage)
            );
        }
    }

    @Benchmark
    public BufferedImage twelveMonkeysLossless(BenchmarkImages images) throws Exception {
        return readStillImageWithProvider(images.losslessWebp);
    }

    @Benchmark
    public Image jwebpLosslessToJavaFX(BenchmarkImages images) throws Exception {
        return WebPFXImage.of(WebPImage.read(new ByteArrayInputStream(images.losslessWebp)));
    }

    @Benchmark
    public Image twelveMonkeysLosslessToJavaFX(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(readStillImageWithProvider(images.losslessWebp), null);
    }

    @Benchmark
    public BufferedImage jniLossless(BenchmarkImages images) throws Exception {
        return WebPCodec.decodeImage(images.losslessWebp);
    }

    @Benchmark
    public Image jniLosslessToJavaFX(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(WebPCodec.decodeImage(images.losslessWebp), null);
    }

    @Benchmark
    public BufferedImage jniLossy(BenchmarkImages images) throws Exception {
        return WebPCodec.decodeImage(images.lossyWebp);
    }

    @Benchmark
    public Image jniLossyToJavaFX(BenchmarkImages images) throws Exception {
        return SwingFXUtils.toFXImage(WebPCodec.decodeImage(images.lossyWebp), null);
    }

    @Benchmark
    public Image javafxPNG(BenchmarkImages images) {
        return new Image(new ByteArrayInputStream(images.png));
    }

    @Benchmark
    public Image javafxJPG(BenchmarkImages images) {
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
