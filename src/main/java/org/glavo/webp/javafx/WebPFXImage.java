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

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// JavaFX image adapter for decoded WebP content.
///
/// The adapter writes packed non-premultiplied `ARGB` pixels from decoded [WebPFrame] instances
/// into a `WritableImage`. When constructed from a fully decoded
/// [WebPImage], it can also play animated WebP content with frame-accurate timing.
@NotNullByDefault
public final class WebPFXImage extends WritableImage {

    private final List<WebPFrame> frames;
    private final boolean animated;
    private final int loopCount;
    private final PixelWriter pixelWriter;

    private @Nullable PauseTransition pauseTransition;
    private int currentFrameIndex;
    private int completedLoops;
    private boolean playing;
    private double playbackRate = 1.0;

    /// Creates a JavaFX image from one decoded frame.
    ///
    /// @param frame the decoded frame to display
    public WebPFXImage(WebPFrame frame) {
        super(frame.getWidth(), frame.getHeight());
        this.frames = List.of(frame);
        this.animated = false;
        this.loopCount = 1;
        this.pixelWriter = getPixelWriter();
        writeFrame(frame);
    }

    /// Creates a JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is written immediately. For animated images, playback starts only when
    /// `autoPlay` is `true` or [#play()] is called later.
    ///
    /// @param image the decoded WebP image
    /// @param autoPlay whether animation playback should start immediately
    public WebPFXImage(WebPImage image, boolean autoPlay) {
        super(image.getWidth(), image.getHeight());
        this.frames = image.getFrames();
        this.animated = image.isAnimated() && frames.size() > 1;
        this.loopCount = image.getLoopCount();
        this.pixelWriter = getPixelWriter();
        writeCurrentFrame();
        if (autoPlay) {
            play();
        }
    }

    /// Returns whether this image exposes animation playback controls.
    ///
    /// @return `true` when the source image contains more than one presentation frame
    public boolean isAnimated() {
        return animated;
    }

    /// Returns the number of decoded presentation frames.
    ///
    /// @return the frame count
    public int getFrameCount() {
        return frames.size();
    }

    /// Returns the declared WebP loop count.
    ///
    /// @return the loop count; `0` means infinite looping
    public int getLoopCount() {
        return loopCount;
    }

    /// Returns the current frame index.
    ///
    /// @return the zero-based frame index currently written into this image
    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    /// Returns whether the animation is currently advancing automatically.
    ///
    /// @return `true` if playback is active
    public boolean isPlaying() {
        return playing;
    }

    /// Returns the current playback rate multiplier.
    ///
    /// @return the playback rate
    public double getPlaybackRate() {
        return playbackRate;
    }

    /// Sets the playback rate multiplier used for subsequent frame scheduling.
    ///
    /// @param playbackRate the new playback rate; values must be positive and finite
    public void setPlaybackRate(double playbackRate) {
        if (!Double.isFinite(playbackRate) || playbackRate <= 0.0) {
            throw new IllegalArgumentException("playbackRate must be positive and finite");
        }
        this.playbackRate = playbackRate;
        if (animated) {
            ensureFxThread();
            if (playing) {
                scheduleCurrentFrame();
            }
        }
    }

    /// Starts or resumes animation playback from the current frame.
    public void play() {
        if (!animated || playing) {
            return;
        }
        ensureFxThread();
        playing = true;
        scheduleCurrentFrame();
    }

    /// Pauses animation playback on the current frame.
    public void pause() {
        if (!animated || !playing) {
            return;
        }
        ensureFxThread();
        playing = false;
        stopTimer();
    }

    /// Stops playback and rewinds to the first frame.
    public void stop() {
        if (animated) {
            ensureFxThread();
            stopTimer();
        }
        playing = false;
        completedLoops = 0;
        currentFrameIndex = 0;
        writeCurrentFrame();
    }

    /// Rewinds to the first frame and starts playback.
    public void playFromStart() {
        if (!animated) {
            stop();
            return;
        }
        ensureFxThread();
        stopTimer();
        playing = false;
        completedLoops = 0;
        currentFrameIndex = 0;
        writeCurrentFrame();
        play();
    }

    /// Jumps to a specific frame.
    ///
    /// The loop-progress counter is reset so subsequent playback starts a fresh cycle from the
    /// chosen frame.
    ///
    /// @param frameIndex the zero-based frame index to display
    public void seekToFrame(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= frames.size()) {
            throw new IllegalArgumentException("frameIndex out of range: " + frameIndex);
        }
        if (animated) {
            ensureFxThread();
        }
        currentFrameIndex = frameIndex;
        completedLoops = 0;
        writeCurrentFrame();
        if (playing) {
            scheduleCurrentFrame();
        }
    }

    private void advanceFrame() {
        if (!playing || !animated) {
            return;
        }

        if (currentFrameIndex + 1 < frames.size()) {
            currentFrameIndex++;
            writeCurrentFrame();
            scheduleCurrentFrame();
            return;
        }

        if (loopCount != 0 && completedLoops + 1 >= loopCount) {
            playing = false;
            stopTimer();
            return;
        }

        completedLoops++;
        currentFrameIndex = 0;
        writeCurrentFrame();
        scheduleCurrentFrame();
    }

    private void scheduleCurrentFrame() {
        int durationMillis = Math.max(1, frames.get(currentFrameIndex).getDurationMillis());
        PauseTransition transition = pauseTransition();
        transition.stop();
        transition.setDuration(Duration.millis(durationMillis / playbackRate));
        transition.playFromStart();
    }

    private PauseTransition pauseTransition() {
        if (pauseTransition == null) {
            PauseTransition transition = new PauseTransition();
            transition.setOnFinished(event -> advanceFrame());
            pauseTransition = transition;
        }
        return pauseTransition;
    }

    private void stopTimer() {
        if (pauseTransition != null) {
            pauseTransition.stop();
        }
    }

    private void writeCurrentFrame() {
        writeFrame(frames.get(currentFrameIndex));
    }

    private void writeFrame(WebPFrame frame) {
        pixelWriter.setPixels(
                0,
                0,
                frame.getWidth(),
                frame.getHeight(),
                PixelFormat.getIntArgbInstance(),
                frame.getArgbPixels(),
                frame.getScanlineStride()
        );
    }

    private static void ensureFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Animated WebPImage controls must be used on the JavaFX application thread");
        }
    }
}
