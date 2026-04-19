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
package org.glavo.webp;

import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.util.Duration;
import org.glavo.webp.javafx.WebPFXImage;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for the JavaFX WebP image adapter and its animation controls.
@NotNullByDefault
final class WebPFXImageTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

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
    void javaFxImageFromDecodedImageStartsPausedOnFirstFrame() throws Exception {
        WebPImage decoded = animatedImage(0, frame(RED, 40), frame(GREEN, 40), frame(BLUE, 40));

        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(decoded));
        Animation animation = callOnFxThread(() -> {
            Animation value = image.getAnimation();
            assertNotNull(value);
            return value;
        });

        assertEquals(Animation.Status.STOPPED, callOnFxThread(animation::getStatus));
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void animatedImageCreatesTimelineLazilyAndReusesIt() throws Exception {
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
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
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
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
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    @Test
    void timelineJumpToAndPlayFromStartUpdateDisplayedPixels() throws Exception {
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
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
        assertEquals(BLUE, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));

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
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
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
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(
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
        WebPFXImage image = callOnFxThread(() -> new WebPFXImage(frame(RED, 0)));
        assertNull(callOnFxThread(image::getAnimation));
        assertEquals(RED, callOnFxThread(() -> image.getPixelReader().getArgb(0, 0)));
    }

    private static WebPImage animatedImage(int loopCount, WebPFrame... frames) {
        long loopDurationMillis = 0L;
        for (WebPFrame frame : frames) {
            loopDurationMillis += frame.getDurationMillis();
        }

        return new WebPImage(
                1,
                1,
                1,
                1,
                false,
                true,
                false,
                loopCount,
                loopDurationMillis,
                WebPMetadata.empty(),
                List.of(frames)
        );
    }

    private static WebPFrame frame(int argb, int durationMillis) {
        return new WebPFrame(1, 1, durationMillis, new int[]{argb});
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
