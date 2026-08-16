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
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// JavaFX image adapter for decoded WebP content.
///
/// The adapter writes packed non-premultiplied `ARGB` pixels from decoded [WebPFrame] instances
/// into a `WritableImage`. When constructed from a fully decoded
/// [WebPImage], it can also play animated WebP content with frame-accurate timing.
/// Static source frames are copied during construction and are not retained; animated frames
/// remain retained for timeline playback.
@NotNullByDefault
public final class WebPFXImage extends WritableImage {

    /// Frames retained for animation playback; static images keep this list empty.
    private final @Unmodifiable List<WebPFrame> animationFrames;

    /// Whether this image has an animated frame sequence.
    private final boolean animated;

    /// Number of animation cycles, or `0` for indefinite playback.
    private final int loopCount;

    /// Lazily created JavaFX animation controller.
    private @Nullable Timeline timeline;

    /// Index of the animation frame most recently written to this image.
    private int renderedFrameIndex = -1;

    /// Creates a JavaFX image from one decoded frame.
    ///
    /// @param frame the decoded frame to display
    public WebPFXImage(WebPFrame frame) {
        super(frame.getWidth(), frame.getHeight());
        this.animationFrames = List.of();
        this.animated = false;
        this.loopCount = 1;

        renderFrame(frame);
    }

    /// Creates a JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is written immediately. Call [#getAnimation()] to control playback.
    ///
    /// @param image the decoded WebP image
    public WebPFXImage(WebPImage image) {
        this(image, true);
    }

    /// Creates a JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is written immediately. Call [#getAnimation()] to control playback.
    ///
    /// @param image    the decoded WebP image
    /// @param autoPlay whether to start playing the animation automatically
    public WebPFXImage(WebPImage image, boolean autoPlay) {
        super(image.getWidth(), image.getHeight());
        this.animated = image.isAnimated();
        this.animationFrames = animated ? image.getFrames() : List.of();
        this.loopCount = image.getLoopCount();

        renderFrame(image.getFirstFrame());
        if (animated) {
            renderedFrameIndex = 0;
        }

        if (autoPlay && isAnimated()) {
            getAnimation().play();
        }
    }

    /// Returns whether this image is animated.
    ///
    /// @return `true` if this image contains multiple frames.
    public boolean isAnimated() {
        return animated;
    }

    /// Returns the JavaFX timeline that drives this image's animation.
    ///
    /// If this image is not [animated][#isAnimated()], `null` is returned.
    public @Nullable Timeline getAnimation() {
        if (animated) {
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

    /// Writes an animation frame unless it is already displayed.
    ///
    /// @param frameIndex the animation frame index
    private void renderFrame(int frameIndex) {
        if (frameIndex != renderedFrameIndex) {
            renderFrame(animationFrames.get(frameIndex));
            renderedFrameIndex = frameIndex;
        }
    }

    /// Copies one decoded frame into this image's JavaFX pixel storage.
    ///
    /// @param frame the decoded frame to copy
    private void renderFrame(WebPFrame frame) {
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

    /// Creates timeline entries for all retained animation frames.
    ///
    /// @return the ordered animation keyframes including the terminal duration marker
    private KeyFrame[] createKeyFrames() {
        KeyFrame[] keyFrames = new KeyFrame[animationFrames.size() + 1];
        long currentStartMillis = 0L;
        for (int i = 0; i < animationFrames.size(); i++) {
            final int frameIndex = i;
            keyFrames[i] = new KeyFrame(Duration.millis(currentStartMillis), event -> renderFrame(frameIndex));
            currentStartMillis += Math.max(1, animationFrames.get(i).getDurationMillis());
        }

        // The terminal marker keeps the last frame visible for its full duration.
        keyFrames[animationFrames.size()] = new KeyFrame(Duration.millis(currentStartMillis));
        return keyFrames;
    }

}
