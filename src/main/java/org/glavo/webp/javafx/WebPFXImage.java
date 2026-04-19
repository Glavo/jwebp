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
    private final int loopCount;
    private @Nullable Timeline timeline;
    private int renderedFrameIndex = -1;

    /// Creates a JavaFX image from one decoded frame.
    ///
    /// @param frame the decoded frame to display
    public WebPFXImage(WebPFrame frame) {
        super(frame.getWidth(), frame.getHeight());
        this.frames = List.of(frame);
        this.loopCount = 1;

        renderFrame(0);
    }

    /// Creates a JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is written immediately. Call [#getAnimation()] to control playback.
    ///
    /// @param image the decoded WebP image
    public WebPFXImage(WebPImage image) {
        super(image.getWidth(), image.getHeight());
        this.frames = image.getFrames();
        this.loopCount = image.getLoopCount();

        renderFrame(0);
    }

    /// Returns whether this image is animated.
    ///
    /// @return `true` if this image contains multiple frames.
    public boolean isAnimated() {
        return frames.size() > 1;
    }

    /// Returns the JavaFX timeline that drives this image's animation.
    ///
    /// The returned timeline uses frame-start keyframes and one terminal keyframe that preserves
    /// the last frame duration. Callers may control playback directly through the standard
    /// `Timeline` API such as `play()`, `pause()`, `stop()`, `jumpTo(...)`, or `setRate(...)`.
    /// The timeline is created lazily on first access. Static images return `null`.
    public @Nullable Timeline getAnimation() {
        if (isAnimated()) {
            if (timeline == null) {
                timeline = new Timeline();
                timeline.setCycleCount(loopCount == 0 ? Animation.INDEFINITE : loopCount);
                timeline.getKeyFrames().setAll(createKeyFrames());
            }
            return timeline;
        } else {
            return null;
        }
    }

    private void renderFrame(int frameIndex) {
        if (frameIndex != renderedFrameIndex) {
            renderedFrameIndex = frameIndex;
            writeFrame(frames.get(frameIndex));
        }
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

    private KeyFrame[] createKeyFrames() {
        if (frames.size() <= 1) {
            return new KeyFrame[0];
        }

        KeyFrame[] keyFrames = new KeyFrame[frames.size() + 1];
        long currentStartMillis = 0L;
        for (int i = 0; i < frames.size(); i++) {
            final int frameIndex = i;
            keyFrames[i] = new KeyFrame(Duration.millis(currentStartMillis), event -> renderFrame(frameIndex));
            currentStartMillis += Math.max(1, frames.get(i).getDurationMillis());
        }

        // The terminal marker keeps the last frame visible for its full duration.
        keyFrames[frames.size()] = new KeyFrame(Duration.millis(currentStartMillis));
        return keyFrames;
    }

}
