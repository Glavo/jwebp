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

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPImageReader;
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
import org.openjdk.jmh.infra.Blackhole;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/// JMH benchmarks comparing animated WebP decoding against GIF baselines.
///
/// The comparison uses paired animation samples from jwebp-test-data so both formats represent
/// the same motion content.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms1G", "-Xmx1G"})
public class AnimatedImageBenchmark {

    private static final String TEST_DATA_ROOT = "jwebp-test-data/";
    private static final ImageReaderSpi GIF_SPI = gifReaderSpi();
    private static volatile boolean javaFxInitialized;

    @State(Scope.Benchmark)
    public static class BenchmarkImages {
        @Param({
                "glavo-1280x720@30",
        })
        public String image;

        byte[] animatedWebp;
        byte[] animatedGif;

        @Setup
        public void load() throws IOException {
            ensureJavaFxInitialized();
            animatedWebp = resourceBytes(TEST_DATA_ROOT + image + ".webp");
            animatedGif = resourceBytes(TEST_DATA_ROOT + image + ".gif");
        }
    }

    @Benchmark
    public WebPImage jwebpDecode(BenchmarkImages images) throws Exception {
        return WebPImage.read(new ByteArrayInputStream(images.animatedWebp));
    }

    @Benchmark
    public void jwebpStreamFrames(BenchmarkImages images, Blackhole blackhole) throws Exception {
        try (WebPImageReader reader = WebPImageReader.open(new ByteArrayInputStream(images.animatedWebp))) {
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
            blackhole.consume(reader.getFrameCount());
            blackhole.consume(reader.getLoopDurationMillis());
            blackhole.consume(reader.isComplete());
        }
    }

    @Benchmark
    public BufferedImage[] imageIOGifDecode(BenchmarkImages images) throws Exception {
        return readAllImagesWithProvider(images.animatedGif, GIF_SPI);
    }

    @Benchmark
    public Image jwebpToJavaFX(BenchmarkImages images) throws Exception {
        return new WebPFXImage(WebPImage.read(new ByteArrayInputStream(images.animatedWebp)), false);
    }

    @Benchmark
    public Image jfxGIFDecode(BenchmarkImages images) {
        return new Image(new ByteArrayInputStream(images.animatedGif));
    }

    private static BufferedImage[] readAllImagesWithProvider(byte[] bytes, ImageReaderSpi spi) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = spi.createReaderInstance();
            try {
                reader.setInput(input, false, true);

                int frameCount = reader.getNumImages(true);
                BufferedImage[] frames = new BufferedImage[frameCount];
                for (int i = 0; i < frameCount; i++) {
                    frames[i] = reader.read(i);
                }
                return frames;
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageReaderSpi gifReaderSpi() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IllegalStateException("No GIF ImageReader available");
        }

        ImageReader reader = readers.next();
        try {
            ImageReaderSpi spi = reader.getOriginatingProvider();
            if (spi == null) {
                throw new IllegalStateException("GIF ImageReader provider unavailable");
            }
            return spi;
        } finally {
            reader.dispose();
        }
    }

    private static void ensureJavaFxInitialized() throws IOException {
        if (javaFxInitialized) {
            return;
        }

        synchronized (AnimatedImageBenchmark.class) {
            if (javaFxInitialized) {
                return;
            }

            CountDownLatch startup = new CountDownLatch(1);
            try {
                Platform.startup(startup::countDown);
                if (!startup.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out initializing JavaFX toolkit");
                }
            } catch (IllegalStateException ignored) {
                // The JavaFX toolkit can only be started once per JVM.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while initializing JavaFX toolkit", ex);
            }

            javaFxInitialized = true;
        }
    }

    private static byte[] resourceBytes(String path) throws IOException {
        try (InputStream input = AnimatedImageBenchmark.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing benchmark resource: " + path);
            }
            return input.readAllBytes();
        }
    }
}
