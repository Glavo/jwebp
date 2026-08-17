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
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPFrameStorage;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPPixelFormat;
import org.glavo.webp.internal.Argb;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

/// JavaFX image adapter for decoded WebP content.
///
/// Instances are backed by a JavaFX [PixelBuffer] in `INT_ARGB_PRE` representation. A static
/// [WebPFrame] already decoded as [WebPPixelFormat#INT_ARGB_PRE] is used directly without copying
/// its pixels. Other static frames are converted once. Animated images retain their decoded frames
/// and copy each presentation frame into one reusable `PixelBuffer` during playback.
///
/// Because the superclass is constructed from a `PixelBuffer`, the inherited
/// [#getPixelWriter()] operation is unsupported.
@NotNullByDefault
public final class WebPFXImage extends WritableImage {

    /// JavaFX pixel buffer backing this image.
    private final PixelBuffer<IntBuffer> pixelBuffer;

    /// Mutable presentation storage used only by animated images.
    private final @Nullable IntBuffer animationBuffer;

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
        this(createInitialization(frame, false));
    }

    /// Completes construction of a static image after its pixel buffer has been prepared.
    ///
    /// @param initialization the prepared JavaFX storage
    private WebPFXImage(Initialization initialization) {
        super(initialization.pixelBuffer());
        this.pixelBuffer = initialization.pixelBuffer();
        this.animationBuffer = null;
        this.animationFrames = List.of();
        this.animated = false;
        this.loopCount = 1;
    }

    /// Creates a JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is visible immediately. Call [#getAnimation()] to control playback.
    ///
    /// @param image the decoded WebP image
    public WebPFXImage(WebPImage image) {
        this(image, true);
    }

    /// Creates a JavaFX image from fully decoded WebP content.
    ///
    /// The first frame is visible immediately. Call [#getAnimation()] to control playback.
    ///
    /// @param image the decoded WebP image
    /// @param autoPlay whether to start playing the animation automatically
    public WebPFXImage(WebPImage image, boolean autoPlay) {
        this(image, autoPlay, createInitialization(image.getFirstFrame(), image.isAnimated()));
    }

    /// Completes construction of a decoded image after its pixel buffer has been prepared.
    ///
    /// @param image the decoded WebP image
    /// @param autoPlay whether to start animation playback
    /// @param initialization the prepared JavaFX storage
    private WebPFXImage(WebPImage image, boolean autoPlay, Initialization initialization) {
        super(initialization.pixelBuffer());
        this.pixelBuffer = initialization.pixelBuffer();
        this.animationBuffer = initialization.animationBuffer();
        this.animated = image.isAnimated();
        this.animationFrames = animated ? image.getFrames() : List.of();
        this.loopCount = image.getLoopCount();

        if (animated) {
            renderedFrameIndex = 0;
        }
        if (autoPlay && animated) {
            getAnimation().play();
        }
    }

    /// Returns whether this image is animated.
    ///
    /// @return `true` if this image contains multiple presentation frames
    public boolean isAnimated() {
        return animated;
    }

    /// Returns the JavaFX timeline that drives this image's animation.
    ///
    /// If this image is not [animated][#isAnimated()], `null` is returned. The timeline is created
    /// lazily and reused by subsequent invocations.
    ///
    /// @return the animation controller, or `null` for a static image
    public @Nullable Timeline getAnimation() {
        if (!animated) {
            return null;
        }
        if (timeline == null) {
            timeline = new Timeline();
            timeline.setCycleCount(loopCount == 0 ? Animation.INDEFINITE : loopCount);
            timeline.getKeyFrames().setAll(createKeyFrames());
        }
        return timeline;
    }

    /// Writes an animation frame unless it is already displayed.
    ///
    /// @param frameIndex the animation frame index
    private void renderFrame(int frameIndex) {
        if (frameIndex == renderedFrameIndex) {
            return;
        }
        WebPFrame frame = animationFrames.get(frameIndex);
        IntBuffer target = animationBuffer;
        if (target == null) {
            throw new IllegalStateException("Animated image has no mutable presentation buffer");
        }
        pixelBuffer.updateBuffer(ignored -> {
            copyAsArgbPre(frame, target);
            return null;
        });
        renderedFrameIndex = frameIndex;
    }

    /// Creates timeline entries for all retained animation frames.
    ///
    /// @return the ordered animation keyframes including the terminal duration marker
    private KeyFrame[] createKeyFrames() {
        KeyFrame[] keyFrames = new KeyFrame[animationFrames.size() + 1];
        long currentStartMillis = 0L;
        for (int index = 0; index < animationFrames.size(); index++) {
            final int frameIndex = index;
            keyFrames[index] = new KeyFrame(
                    Duration.millis(currentStartMillis),
                    event -> renderFrame(frameIndex)
            );
            currentStartMillis += Math.max(1, animationFrames.get(index).getDurationMillis());
        }

        // The terminal marker keeps the last frame visible for its full duration.
        keyFrames[animationFrames.size()] = new KeyFrame(Duration.millis(currentStartMillis));
        return keyFrames;
    }

    /// Prepares a JavaFX-compatible premultiplied pixel buffer.
    ///
    /// An immutable premultiplied source can be shared directly. Mutable storage is always
    /// allocated for animation because later frames must replace its contents.
    ///
    /// @param frame the initially displayed frame
    /// @param mutable whether the returned storage must support animation updates
    /// @return the JavaFX pixel buffer and optional mutable animation storage
    private static Initialization createInitialization(WebPFrame frame, boolean mutable) {
        IntBuffer buffer;
        @Nullable IntBuffer animationBuffer = null;
        if (!mutable && frame.getPixelFormat() == WebPPixelFormat.INT_ARGB_PRE) {
            buffer = frame.getPixels();
        } else {
            buffer = allocateBuffer(frame);
            copyAsArgbPre(frame, buffer);
            if (mutable) {
                animationBuffer = buffer;
            }
        }

        PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(
                frame.getWidth(),
                frame.getHeight(),
                buffer,
                PixelFormat.getIntArgbPreInstance()
        );
        return new Initialization(pixelBuffer, animationBuffer);
    }

    /// Allocates writable JavaFX presentation storage matching the frame's storage location.
    ///
    /// @param frame the frame whose dimensions and storage are used
    /// @return a position-zero writable buffer
    private static IntBuffer allocateBuffer(WebPFrame frame) {
        int pixelCount = Math.multiplyExact(frame.getWidth(), frame.getHeight());
        if (frame.getFrameStorage() == WebPFrameStorage.DIRECT) {
            return ByteBuffer.allocateDirect(Math.multiplyExact(pixelCount, Integer.BYTES))
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
        }
        return IntBuffer.allocate(pixelCount);
    }

    /// Copies one frame into writable `INT_ARGB_PRE` storage.
    ///
    /// The destination position is reset to zero before this method returns.
    ///
    /// @param frame the source frame
    /// @param target the destination with capacity for the entire frame
    private static void copyAsArgbPre(WebPFrame frame, IntBuffer target) {
        IntBuffer source = frame.getPixels();
        target.clear();
        if (frame.getPixelFormat() == WebPPixelFormat.INT_ARGB_PRE) {
            target.put(source);
        } else {
            while (source.hasRemaining()) {
                target.put(Argb.premultiply(source.get()));
            }
        }
        target.rewind();
    }

    /// Prepared JavaFX storage used to select the `WritableImage` superclass constructor.
    ///
    /// @param pixelBuffer the JavaFX pixel buffer
    /// @param animationBuffer writable storage for animation updates, or `null` for a static image
    @NotNullByDefault
    private record Initialization(
            PixelBuffer<IntBuffer> pixelBuffer,
            @Nullable IntBuffer animationBuffer
    ) {
    }
}
