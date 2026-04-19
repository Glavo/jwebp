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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Fully decoded WebP content.
///
/// This type is the eager counterpart of [WebPImageReader]. It materializes all decoded
/// frames and exposes the associated metadata and animation timing information in immutable form.
@NotNullByDefault
public final class WebPImage {

    /// Reads and fully decodes a WebP stream.
    ///
    /// @param input the WebP byte stream
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return the fully decoded image
    /// @throws WebPException if parsing or decoding fails
    public static WebPImage read(InputStream input, WebPImageLoadOptions options) throws WebPException {
        try (WebPImageReader reader = WebPImageReader.open(input, options)) {
            return collect(reader);
        } catch (IOException ex) {
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            throw new WebPException("Failed to decode WebP stream", ex);
        }
    }

    /// Reads and fully decodes a WebP stream using the default options.
    ///
    /// @param input the WebP byte stream
    /// @return the fully decoded image
    /// @throws WebPException if parsing or decoding fails
    public static WebPImage read(InputStream input) throws WebPException {
        return read(input, WebPImageLoadOptions.DEFAULT);
    }

    /// Reads and fully decodes a WebP file.
    ///
    /// @param path the WebP file path
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return the fully decoded image
    /// @throws WebPException if the file cannot be parsed or decoded
    public static WebPImage read(Path path, WebPImageLoadOptions options) throws WebPException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            return read(input, options);
        } catch (IOException ex) {
            throw new WebPException("Failed to decode WebP file: " + path, ex);
        }
    }

    /// Reads and fully decodes a WebP file using the default options.
    ///
    /// @param path the WebP file path
    /// @return the fully decoded image
    /// @throws WebPException if the file cannot be parsed or decoded
    public static WebPImage read(Path path) throws WebPException {
        return read(path, WebPImageLoadOptions.DEFAULT);
    }

    private final int sourceWidth;
    private final int sourceHeight;
    private final int width;
    private final int height;
    private final boolean alpha;
    private final boolean animated;
    private final boolean lossy;
    private final int loopCount;
    private final long loopDurationMillis;
    private final WebPMetadata metadata;
    private final List<WebPFrame> frames;

    /// Creates a fully decoded WebP image.
    ///
    /// @param sourceWidth the source canvas width
    /// @param sourceHeight the source canvas height
    /// @param width the decoded output width after applying load options
    /// @param height the decoded output height after applying load options
    /// @param alpha whether any frame carries transparency
    /// @param animated whether the source contains animation
    /// @param lossy whether any decoded frame uses lossy VP8 compression
    /// @param loopCount the animation loop count; `0` means infinite looping
    /// @param loopDurationMillis the total duration of one animation cycle
    /// @param metadata the extracted metadata
    /// @param frames the decoded frames in presentation order
    WebPImage(
            int sourceWidth,
            int sourceHeight,
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

        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
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

    /// Returns the source canvas width before scaling.
    ///
    /// @return the intrinsic canvas width
    public int getSourceWidth() {
        return sourceWidth;
    }

    /// Returns the source canvas height before scaling.
    ///
    /// @return the intrinsic canvas height
    public int getSourceHeight() {
        return sourceHeight;
    }

    /// Returns the decoded output width after applying load options.
    ///
    /// @return the output width in pixels
    public int getWidth() {
        return width;
    }

    /// Returns the decoded output height after applying load options.
    ///
    /// @return the output height in pixels
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

    private static WebPImage collect(WebPImageReader reader) throws IOException {
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
                reader.getSourceWidth(),
                reader.getSourceHeight(),
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
