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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Fully decoded WebP content.
///
/// This type is the eager counterpart of [WebPImageReader]. It materializes all decoded
/// frames and exposes the associated metadata and animation timing information in immutable form.
@NotNullByDefault
public final class WebPImage {

    /// Reads and fully decodes a WebP stream using [WebPDecoder#DEFAULT].
    ///
    /// The supplied stream is closed before this method returns, including when decoding fails.
    ///
    /// @param input the WebP byte stream
    /// @return the fully decoded image
    /// @throws WebPException if parsing or decoding fails
    /// @throws NullPointerException if `input` is `null`
    public static WebPImage read(InputStream input) throws WebPException {
        return WebPDecoder.DEFAULT.read(input);
    }

    /// Reads and fully decodes a WebP file using [WebPDecoder#DEFAULT].
    ///
    /// @param path the WebP file path
    /// @return the fully decoded image
    /// @throws WebPException if the file cannot be parsed or decoded
    /// @throws NullPointerException if `path` is `null`
    public static WebPImage read(Path path) throws WebPException {
        return WebPDecoder.DEFAULT.read(path);
    }

    /// Image canvas width in pixels.
    private final int width;

    /// Image canvas height in pixels.
    private final int height;

    /// Whether the source declares or contains transparency.
    private final boolean alpha;

    /// Whether the source container is animated.
    private final boolean animated;

    /// Whether any frame uses lossy VP8 compression.
    private final boolean lossy;

    /// Animation loop count, or zero for indefinite looping.
    private final int loopCount;

    /// Duration of one animation cycle in milliseconds.
    private final long loopDurationMillis;

    /// Metadata extracted from the source container.
    private final WebPMetadata metadata;

    /// Immutable presentation frames in playback order.
    private final List<WebPFrame> frames;

    /// Creates a fully decoded WebP image.
    ///
    /// @param width the image canvas width
    /// @param height the image canvas height
    /// @param alpha whether any frame carries transparency
    /// @param animated whether the source contains animation
    /// @param lossy whether any decoded frame uses lossy VP8 compression
    /// @param loopCount the animation loop count; `0` means infinite looping
    /// @param loopDurationMillis the total duration of one animation cycle
    /// @param metadata the extracted metadata
    /// @param frames the decoded frames in presentation order
    WebPImage(
            int width,
            int height,
            boolean alpha,
            boolean animated,
            boolean lossy,
            int loopCount,
            long loopDurationMillis,
            WebPMetadata metadata,
            List<WebPFrame> frames
    ) {
        assert !frames.isEmpty();

        this.width = width;
        this.height = height;
        this.alpha = alpha;
        this.animated = animated;
        this.lossy = lossy;
        this.loopCount = loopCount;
        this.loopDurationMillis = loopDurationMillis;
        this.metadata = metadata;
        this.frames = List.copyOf(frames);
    }

    /// Returns the image canvas width.
    ///
    /// @return the canvas width in pixels
    public int getWidth() {
        return width;
    }

    /// Returns the image canvas height.
    ///
    /// @return the canvas height in pixels
    public int getHeight() {
        return height;
    }

    /// Returns whether the image contains transparency.
    ///
    /// @return `true` if at least one pixel may carry alpha
    public boolean hasAlpha() {
        return alpha;
    }

    /// Returns whether the source container is animated.
    ///
    /// @return `true` for animated WebP containers
    public boolean isAnimated() {
        return animated;
    }

    /// Returns whether any decoded frame uses lossy VP8 compression.
    ///
    /// @return `true` if the image contains lossy frame data
    public boolean isLossy() {
        return lossy;
    }

    /// Returns the loop count declared by the source animation.
    ///
    /// Static images report `1`. A value of `0` means the animation loops forever.
    ///
    /// @return the loop count
    public int getLoopCount() {
        return loopCount;
    }

    /// Returns the total duration of one full animation cycle.
    ///
    /// Static images report `0`.
    ///
    /// @return the total cycle duration in milliseconds
    public long getLoopDurationMillis() {
        return loopDurationMillis;
    }

    /// Returns the extracted metadata.
    ///
    /// @return the metadata container, never `null`
    public WebPMetadata getMetadata() {
        return metadata;
    }

    /// Returns all decoded frames in presentation order.
    ///
    /// @return an immutable frame list
    public @Unmodifiable List<WebPFrame> getFrames() {
        return frames;
    }

    /// Returns the first frame, if present.
    ///
    /// @return the first frame for still images or animations
    public WebPFrame getFirstFrame() {
        return frames.get(0);
    }

    /// Collects all remaining frames and metadata from a reader into an eager image.
    ///
    /// The reader remains open and positioned at end of input when this method returns.
    ///
    /// @param reader the reader to exhaust
    /// @return the fully decoded image
    /// @throws WebPException if any frame cannot be decoded
    static WebPImage collect(WebPImageReader reader) throws WebPException {
        List<WebPFrame> frames;
        if (reader.getFrameCount() == 1) {
            //noinspection DataFlowIssue
            frames = List.of(reader.readNextFrame());
        } else {
            frames = new ArrayList<>(Math.max(1, reader.getFrameCount()));
            while (true) {
                WebPFrame next = reader.readNextFrame();
                if (next == null) {
                    break;
                }
                frames.add(next);
            }
        }

        return new WebPImage(
                reader.getWidth(),
                reader.getHeight(),
                reader.hasAlpha(),
                reader.isAnimated(),
                reader.isLossy(),
                reader.getLoopCount(),
                reader.getLoopDurationMillis(),
                reader.getMetadata(),
                frames
        );
    }
}
