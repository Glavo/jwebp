// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.util.Duration;
import org.glavo.webp.javafx.WebPFXImage;
import org.glavo.webp.javafx.WebPFXImageOptions;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for the JavaFX WebP image adapter and its animation controls.
@NotNullByDefault
final class WebPFXImageTest {

    /// Opaque red test pixel.
    private static final int RED = 0xFFFF0000;

    /// Opaque green test pixel.
    private static final int GREEN = 0xFF00FF00;

    /// Opaque blue test pixel.
    private static final int BLUE = 0xFF0000FF;

    /// Opaque white test pixel.
    private static final int WHITE = 0xFFFFFFFF;

    /// Intrinsic-size presentation options that keep animations paused.
    private static final WebPFXImageOptions PAUSED = WebPFXImageOptions.DEFAULT.withAutoPlay(false);

    /// Verifies that every public factory rejects a null decoded source.
    @Test
    void factoryMethodsRejectNullSources() {
        assertThrows(NullPointerException.class, () -> WebPFXImage.of((WebPFrame) null));
        assertThrows(
                NullPointerException.class,
                () -> WebPFXImage.of((WebPFrame) null, WebPFXImageOptions.DEFAULT)
        );
        assertThrows(
                NullPointerException.class,
                () -> WebPFXImage.of(frame(RED, 0), (WebPFXImageOptions) null)
        );
        assertThrows(NullPointerException.class, () -> WebPFXImage.of((WebPImage) null));
        assertThrows(
                NullPointerException.class,
                () -> WebPFXImage.of((WebPImage) null, WebPFXImageOptions.DEFAULT)
        );
        assertThrows(
                NullPointerException.class,
                () -> WebPFXImage.of(
                        animatedImage(0, frame(RED, 40), frame(GREEN, 40)),
                        (WebPFXImageOptions) null
                )
        );
    }

    /// Verifies the defaults and copy-on-write behavior of JavaFX presentation options.
    @Test
    void imageOptionsAreImmutable() {
        WebPFXImageOptions defaults = WebPFXImageOptions.DEFAULT;
        WebPFXImageOptions configured = defaults
                .withRequestedSize(640, 480)
                .withPreserveRatio(true)
                .withSmooth(false)
                .withAutoPlay(false);

        assertEquals(0.0, defaults.getRequestedWidth());
        assertEquals(0.0, defaults.getRequestedHeight());
        assertFalse(defaults.isPreserveRatio());
        assertTrue(defaults.isSmooth());
        assertTrue(defaults.isAutoPlay());

        assertEquals(640.0, configured.getRequestedWidth());
        assertEquals(480.0, configured.getRequestedHeight());
        assertTrue(configured.isPreserveRatio());
        assertFalse(configured.isSmooth());
        assertFalse(configured.isAutoPlay());
        assertNotSame(defaults, configured);

        assertSame(defaults, defaults.withRequestedSize(0, 0));
        assertSame(defaults, defaults.withPreserveRatio(false));
        assertSame(defaults, defaults.withSmooth(true));
        assertSame(defaults, defaults.withAutoPlay(true));
    }

    @BeforeAll
    static void initializeJavaFx() throws Exception {
        CompletableFuture<Void> startup = new CompletableFuture<>();
        try {
            Platform.startup(() -> startup.complete(null));
            startup.get(5, TimeUnit.SECONDS);
        } catch (IllegalStateException ignored) {
            // The JavaFX toolkit can only be started once per JVM.
        }
    }

    @Test
    void javaFxImageFromDecodedStaticImageMatchesPixels() throws Exception {
        WebPImage decoded = WebPImage.read(resource("images/regression-tiny.webp"));

        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(decoded));

        assertNull(callOnFxThread(image::getAnimation));
        assertJavaFxImageEquals(image, "reference/regression-tiny.png");
    }

    @Test
    void javaFxImageUsesPremultipliedDirectFrameThroughPixelBuffer() throws Exception {
        WebPFrame frame;
        try (WebPImageReader reader = WebPImageReader.open(resource("images/regression-tiny.webp"))) {
            int pixelCount = Math.multiplyExact(reader.getWidth(), reader.getHeight());
            IntBuffer storage = ByteBuffer
                    .allocateDirect(Math.multiplyExact(pixelCount, Integer.BYTES))
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            frame = reader.readNextFrame(WebPPixelFormat.INT_ARGB_PRE, storage);
        }

        assertNotNull(frame);
        assertTrue(frame.getPixels().isDirect());
        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(frame));

        assertThrows(UnsupportedOperationException.class, image::getPixelWriter);
        assertJavaFxImageEquals(image, "reference/regression-tiny.png");
    }

    /// Verifies that requested dimensions follow JavaFX's integer-size behavior.
    @Test
    void scaledDimensionsMatchJavaFxImageSemantics() throws Exception {
        WebPImage decoded = WebPImage.read(resource("images/regression-tiny.webp"));

        assertScaledDimensionsMatchJavaFx(decoded, "reference/regression-tiny.png", 180, 0, true, true);
        assertScaledDimensionsMatchJavaFx(decoded, "reference/regression-tiny.png", 0, 96, true, true);
        assertScaledDimensionsMatchJavaFx(decoded, "reference/regression-tiny.png", 180, 120, true, true);
        assertScaledDimensionsMatchJavaFx(decoded, "reference/regression-tiny.png", 37, 29, false, false);
    }

    /// Verifies nearest-neighbor expansion without color interpolation.
    @Test
    void nearestNeighborScalingReplicatesSourcePixels() throws Exception {
        WebPFrame frame = frame(2, 2, 0, RED, GREEN, BLUE, WHITE);

        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(frame, options(4, 4, false, false)));
        WebPFXImage minimumWidth = callOnFxThread(
                () -> WebPFXImage.of(frame, options(0.4, 0, false, false))
        );

        callOnFxThread(() -> {
            PixelReader pixels = image.getPixelReader();
            assertEquals(4, (int) image.getWidth());
            assertEquals(4, (int) image.getHeight());
            assertEquals(RED, pixels.getArgb(0, 0));
            assertEquals(RED, pixels.getArgb(1, 1));
            assertEquals(GREEN, pixels.getArgb(3, 0));
            assertEquals(BLUE, pixels.getArgb(0, 3));
            assertEquals(WHITE, pixels.getArgb(3, 3));
            assertEquals(1, (int) minimumWidth.getWidth());
            assertEquals(2, (int) minimumWidth.getHeight());
            return null;
        });
    }

    /// Verifies that smooth scaling interpolates in premultiplied color space.
    @Test
    void smoothScalingInterpolatesPremultipliedPixels() throws Exception {
        WebPFrame frame = frame(2, 1, 0, 0x00FF0000, BLUE);

        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(frame, options(3, 1, false, true)));
        int center = callOnFxThread(() -> image.getPixelReader().getArgb(1, 0));

        assertEquals(0x80, center >>> 24);
        assertEquals(0, (center >>> 16) & 0xFF);
        assertEquals(0, (center >>> 8) & 0xFF);
        assertEquals(0xFF, center & 0xFF);
    }

    /// Verifies that presentation options reject unsupported floating-point dimensions.
    @Test
    void optionsRejectNonFiniteDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WebPFXImageOptions.DEFAULT.withRequestedSize(Double.NaN, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> WebPFXImageOptions.DEFAULT.withRequestedSize(0, Double.POSITIVE_INFINITY)
        );
    }

    @Test
    void javaFxImageFromDecodedImageStartsPausedOnFirstFrame() throws Exception {
        WebPImage decoded = animatedImage(0, frame(RED, 40), frame(GREEN, 40), frame(BLUE, 40));

        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(decoded));
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        assertNotNull(animation);
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void javaFxImageFromDecodedAnimatedImageMatchesFirstFrame() throws Exception {
        WebPImage decoded = WebPImage.read(resource("images/animated-random_lossless.webp"));

        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(decoded, PAUSED));
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        assertNotNull(animation);
        assertJavaFxImageEquals(image, "reference/animated/random_lossless-1.png");
    }

    /// Verifies that every frame of a scaled animation uses the target dimensions.
    @Test
    void scaledAnimationPresentsEveryFrameAtTargetSize() throws Exception {
        WebPImage decoded = animatedImage(
                0,
                frame(2, 1, 120, RED, GREEN),
                frame(2, 1, 120, BLUE, WHITE)
        );

        WebPFXImage image = callOnFxThread(
                () -> WebPFXImage.of(decoded, options(4, 2, false, false))
        );
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            value.play();
            return value;
        });

        assertEquals(4, (int) image.getWidth());
        assertEquals(2, (int) image.getHeight());
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(3, 1)));
        waitForCondition(() -> callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)) == BLUE, 500);
        assertEquals(WHITE, callOnFxThread(() -> image.getPixelReader().getArgb(3, 1)));

        callOnFxThread(() -> {
            animation.stop();
            return null;
        });
    }

    @Test
    void animatedImageCreatesTimelineLazilyAndReusesIt() throws Exception {
        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(
                animatedImage(0, frame(RED, 40), frame(GREEN, 40))
        ));

        Animation first = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });
        Animation second = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        assertTrue(first == second);
    }

    @Test
    void timelinePlayPauseAndStopControlAnimation() throws Exception {
        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(
                animatedImage(0, frame(RED, 40), frame(GREEN, 40))
        ));
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        callOnFxThread(() -> {
            animation.play();
            return null;
        });
        waitForCondition(() -> callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)) == GREEN, 500);

        callOnFxThread(() -> {
            animation.pause();
            return null;
        });
        assertEquals(Animation.Status.PAUSED, callOnFxThread(animation::getStatus));

        Thread.sleep(120);
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));

        callOnFxThread(() -> {
            animation.stop();
            return null;
        });
        assertEquals(Animation.Status.STOPPED, callOnFxThread(animation::getStatus));
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void timelinePlayFromStartRestartsFromFirstFrame() throws Exception {
        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(
                animatedImage(0, frame(RED, 40), frame(GREEN, 40), frame(BLUE, 40))
        ));
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        callOnFxThread(() -> {
            animation.jumpTo(Duration.millis(80));
            return null;
        });
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));

        callOnFxThread(() -> {
            animation.playFromStart();
            return null;
        });
        assertEquals(Animation.Status.RUNNING, callOnFxThread(animation::getStatus));
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));

        waitForCondition(() -> callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)) == GREEN, 500);
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void timelineRespectsFiniteLoopCount() throws Exception {
        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(
                animatedImage(1, frame(RED, 40), frame(GREEN, 40))
        ));
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        callOnFxThread(() -> {
            animation.play();
            return null;
        });

        waitForCondition(() -> callOnFxThread(animation::getStatus) == Animation.Status.STOPPED, 500);
        assertEquals(GREEN, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void timelineRateControlsPlaybackSpeed() throws Exception {
        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(
                animatedImage(0, frame(RED, 240), frame(GREEN, 240))
        ));
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        callOnFxThread(() -> {
            animation.setRate(4.0);
            animation.play();
            return null;
        });

        waitForCondition(() -> callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)) == GREEN, 180);
        assertEquals(4.0, callOnFxThread(animation::getRate), 0.0001);
    }

    @Test
    void staticImageDoesNotExposeAnimation() throws Exception {
        WebPFXImage image = callOnFxThread(() -> WebPFXImage.of(frame(RED, 0)));
        assertNull(callOnFxThread(image::getAnimation));
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    /// Creates a synthetic eager animation from fully composited frames.
    ///
    /// @param loopCount the declared animation loop count
    /// @param frames the presentation frames
    /// @return the synthetic decoded image
    private static WebPImage animatedImage(int loopCount, WebPFrame... frames) {
        long loopDurationMillis = 0L;
        for (WebPFrame frame : frames) {
            loopDurationMillis += frame.getDurationMillis();
        }

        return new WebPImage(
                frames[0].getWidth(),
                frames[0].getHeight(),
                false,
                true,
                false,
                loopCount,
                loopDurationMillis,
                WebPMetadata.empty(),
                List.of(frames)
        );
    }

    /// Creates a one-pixel synthetic frame.
    ///
    /// @param argb the non-premultiplied pixel
    /// @param durationMillis the presentation duration
    /// @return the synthetic frame
    private static WebPFrame frame(int argb, int durationMillis) {
        return new WebPFrame(1, 1, durationMillis, new int[]{argb});
    }

    /// Creates a synthetic frame from tightly packed pixels.
    ///
    /// @param width the frame width
    /// @param height the frame height
    /// @param durationMillis the presentation duration
    /// @param argb the tightly packed non-premultiplied pixels
    /// @return the synthetic frame
    private static WebPFrame frame(int width, int height, int durationMillis, int... argb) {
        return new WebPFrame(width, height, durationMillis, argb);
    }

    /// Compares adapter dimensions with JavaFX loading of the matching reference image.
    ///
    /// @param decoded the decoded WebP source
    /// @param expectedPath the matching reference image resource
    /// @param requestedWidth the requested width
    /// @param requestedHeight the requested height
    /// @param preserveRatio whether to preserve the aspect ratio
    /// @param smooth whether to enable smooth filtering
    private static void assertScaledDimensionsMatchJavaFx(
            WebPImage decoded,
            String expectedPath,
            double requestedWidth,
            double requestedHeight,
            boolean preserveRatio,
            boolean smooth
    ) throws Exception {
        try (InputStream input = resource(expectedPath)) {
            callOnFxThread(() -> {
                Image expected = new Image(input, requestedWidth, requestedHeight, preserveRatio, smooth);
                WebPFXImage actual = WebPFXImage.of(decoded, options(
                        requestedWidth,
                        requestedHeight,
                        preserveRatio,
                        smooth
                ));
                String parameters = "requested=" + requestedWidth + "x" + requestedHeight
                        + ", preserveRatio=" + preserveRatio
                        + ", JavaFX=" + expected.getWidth() + "x" + expected.getHeight()
                        + ", WebPFXImage=" + actual.getWidth() + "x" + actual.getHeight();
                assertEquals((int) expected.getWidth(), (int) actual.getWidth(), parameters);
                assertEquals((int) expected.getHeight(), (int) actual.getHeight(), parameters);
                return null;
            });
        }
    }

    /// Creates presentation options with deterministic paused animation state.
    ///
    /// @param requestedWidth the requested presentation width
    /// @param requestedHeight the requested presentation height
    /// @param preserveRatio whether to preserve the intrinsic aspect ratio
    /// @param smooth whether to use smooth filtering
    /// @return the configured presentation options
    private static WebPFXImageOptions options(
            double requestedWidth,
            double requestedHeight,
            boolean preserveRatio,
            boolean smooth
    ) {
        return PAUSED
                .withRequestedSize(requestedWidth, requestedHeight)
                .withPreserveRatio(preserveRatio)
                .withSmooth(smooth);
    }

    private static void assertJavaFxImageEquals(WebPFXImage image, String expectedPath) throws Exception {
        BufferedImage expected;
        try (InputStream input = resource(expectedPath)) {
            expected = ImageIO.read(input);
        }

        callOnFxThread(() -> {
            PixelReader pixelReader = image.getPixelReader();
            assertNotNull(pixelReader);
            assertEquals(expected.getWidth(), (int) image.getWidth());
            assertEquals(expected.getHeight(), (int) image.getHeight());

            for (int y = 0; y < expected.getHeight(); y++) {
                for (int x = 0; x < expected.getWidth(); x++) {
                    assertEquals(expected.getRGB(x, y), pixelReader.getArgb(x, y), "Pixel mismatch at (" + x + ", " + y + ")");
                }
            }
            return null;
        });
    }

    private static InputStream resource(String path) {
        InputStream input = WebPFXImageTest.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalArgumentException("Missing test resource: " + path);
        }
        return input;
    }

    private static void waitForCondition(ThrowingBooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not satisfied within " + timeoutMillis + "ms");
    }

    private static <T> T callOnFxThread(ThrowingSupplier<T> supplier) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (TimeoutException ex) {
            throw new AssertionError("Timed out waiting for JavaFX task", ex);
        }
    }

    @NotNullByDefault
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @NotNullByDefault
    private interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
