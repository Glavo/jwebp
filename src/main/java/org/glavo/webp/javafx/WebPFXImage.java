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
package org.glavo.webp.javafx;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// JavaFX image adapter for decoded WebP content.
///
/// The adapter writes packed non-premultiplied `ARGB` pixels from decoded [WebPFrame] instances
/// into a `WritableImage`. When constructed from a fully decoded
/// [WebPImage], it can also play animated WebP content with frame-accurate timing.
@NotNullByDefault
public final class WebPFXImage extends WritableImage {

    private final List<WebPFrame> frames;
    private final long[] frameStartMillis;
    private final int loopCount;
    private @Nullable Timeline timeline;
    private int renderedFrameIndex;

    /// Creates a JavaFX image from one decoded frame.
    ///
    /// @param frame the decoded frame to display
    public WebPFXImage(WebPFrame frame) {
        super(frame.getWidth(), frame.getHeight());
        this.frames = List.of(frame);
        this.frameStartMillis = new long[]{0L};
        this.loopCount = 1;
        this.renderedFrameIndex = 0;
        writeFrame(frame);
    }

    /// Creates a JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is written immediately. Call [#getAnimation()] to control playback.
    ///
    /// @param image the decoded WebP image
    public WebPFXImage(WebPImage image) {
        super(image.getWidth(), image.getHeight());
        this.frames = image.getFrames();
        this.frameStartMillis = computeFrameStartMillis(frames);
        this.loopCount = image.getLoopCount();
        this.renderedFrameIndex = 0;
        writeFrame(frames.get(0));
    }

    /// Returns the JavaFX timeline that drives this image's animation.
    ///
    /// The returned timeline uses frame-start keyframes and one terminal keyframe that preserves
    /// the last frame duration. Callers may control playback directly through the standard
    /// `Timeline` API such as `play()`, `pause()`, `stop()`, `jumpTo(...)`, or `setRate(...)`.
    /// The timeline is created lazily on first access. Static images return `null`.
    public @Nullable Timeline getAnimation() {
        if (frames.size() <= 1) {
            return null;
        }

        Timeline currentTimeline = timeline;
        if (currentTimeline == null) {
            currentTimeline = createTimeline(loopCount);
            timeline = currentTimeline;
        }
        return currentTimeline;
    }

    private void writeFrame(WebPFrame frame) {
        getPixelWriter().setPixels(
                0,
                0,
                frame.getWidth(),
                frame.getHeight(),
                PixelFormat.getIntArgbInstance(),
                frame.getArgbPixels(),
                frame.getScanlineStride()
        );
    }

    private Timeline createTimeline(int loopCount) {
        Timeline timeline = new Timeline();
        timeline.setCycleCount(frames.size() > 1
                ? (loopCount == 0 ? Animation.INDEFINITE : Math.max(1, loopCount))
                : 1);
        timeline.getKeyFrames().setAll(createKeyFrames());
        timeline.currentTimeProperty().addListener((observable, oldValue, newValue) -> renderFrameAt(newValue));
        return timeline;
    }

    private List<KeyFrame> createKeyFrames() {
        if (frames.size() <= 1) {
            return List.of();
        }

        List<KeyFrame> keyFrames = new ArrayList<>(frames.size() + 1);
        long totalDurationMillis = 0L;
        for (int i = 0; i < frames.size(); i++) {
            WebPFrame frame = frames.get(i);
            keyFrames.add(new KeyFrame(Duration.millis(frameStartMillis[i])));
            totalDurationMillis = frameStartMillis[i] + normalizedDurationMillis(frame);
        }

        // The terminal marker keeps the last frame visible for its full duration.
        keyFrames.add(new KeyFrame(Duration.millis(totalDurationMillis)));
        return keyFrames;
    }

    private static long[] computeFrameStartMillis(List<WebPFrame> frames) {
        long[] frameStartMillis = new long[frames.size()];
        long currentStartMillis = 0L;
        for (int i = 0; i < frames.size(); i++) {
            frameStartMillis[i] = currentStartMillis;
            currentStartMillis += normalizedDurationMillis(frames.get(i));
        }
        return frameStartMillis;
    }

    private static int normalizedDurationMillis(WebPFrame frame) {
        return Math.max(1, frame.getDurationMillis());
    }

    private void renderFrameAt(Duration time) {
        if (frames.size() <= 1) {
            return;
        }

        int frameIndex = frameIndexAt(time.toMillis());
        if (frameIndex != renderedFrameIndex) {
            renderedFrameIndex = frameIndex;
            writeFrame(frames.get(frameIndex));
        }
    }

    private int frameIndexAt(double currentMillis) {
        double millis = Math.max(0.0, currentMillis);
        int low = 0;
        int high = frameStartMillis.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (frameStartMillis[mid] <= millis) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return Math.max(0, high);
    }
}
