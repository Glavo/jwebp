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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/// Immutable configuration and entry point for decoding WebP images.
///
/// Instances are safe to reuse concurrently. Each call to [#open(InputStream)] or [#open(Path)]
/// creates an independent stateful [WebPImageReader]. Configuration methods return a new decoder
/// and never change the receiver.
@NotNullByDefault
public final class WebPDecoder {

    /// Minimum decoded frame size at which the automatic policy currently selects direct storage.
    ///
    /// The threshold is intentionally private because [WebPFrameStorage#AUTO] does not guarantee a
    /// particular selection algorithm.
    private static final long AUTO_DIRECT_THRESHOLD_BYTES = 4L * 1024L * 1024L;

    /// The default decoder, which produces heap-backed non-premultiplied `INT_ARGB` frames.
    public static final WebPDecoder DEFAULT = new WebPDecoder(
            WebPPixelFormat.INT_ARGB,
            WebPFrameStorage.HEAP
    );

    /// Pixel representation requested for decoded frames.
    private final WebPPixelFormat pixelFormat;

    /// Storage policy requested for decoded frames.
    private final WebPFrameStorage frameStorage;

    /// Creates an immutable decoder configuration.
    ///
    /// @param pixelFormat the decoded frame pixel format
    /// @param frameStorage the decoded frame storage policy
    private WebPDecoder(WebPPixelFormat pixelFormat, WebPFrameStorage frameStorage) {
        this.pixelFormat = pixelFormat;
        this.frameStorage = frameStorage;
    }

    /// Returns the requested decoded frame pixel format.
    ///
    /// @return the pixel format
    public WebPPixelFormat getPixelFormat() {
        return pixelFormat;
    }

    /// Returns the requested decoded frame storage policy.
    ///
    /// @return the frame storage policy
    public WebPFrameStorage getFrameStorage() {
        return frameStorage;
    }

    /// Returns a decoder that produces frames in the supplied pixel format.
    ///
    /// @param pixelFormat the requested pixel format
    /// @return this decoder if the format is unchanged; otherwise an independently reusable decoder
    /// @throws NullPointerException if `pixelFormat` is `null`
    public WebPDecoder withPixelFormat(WebPPixelFormat pixelFormat) {
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        return this.pixelFormat == pixelFormat ? this : new WebPDecoder(pixelFormat, frameStorage);
    }

    /// Returns a decoder that uses the supplied frame storage policy.
    ///
    /// @param frameStorage the requested storage policy
    /// @return this decoder if the policy is unchanged; otherwise an independently reusable decoder
    /// @throws NullPointerException if `frameStorage` is `null`
    public WebPDecoder withFrameStorage(WebPFrameStorage frameStorage) {
        Objects.requireNonNull(frameStorage, "frameStorage");
        return this.frameStorage == frameStorage ? this : new WebPDecoder(pixelFormat, frameStorage);
    }

    /// Opens a forward-only reader for a WebP stream.
    ///
    /// Ownership of `source` transfers to this operation. The returned reader closes it when the
    /// reader is closed, and this method closes it before throwing if opening fails. The stream is
    /// consumed during this call so encoded frame payloads can be retained for on-demand decoding.
    ///
    /// @param source the WebP byte stream
    /// @return a new reader configured by this decoder
    /// @throws WebPException if the stream cannot be parsed
    /// @throws NullPointerException if `source` is `null`
    public WebPImageReader open(InputStream source) throws WebPException {
        Objects.requireNonNull(source, "source");
        return WebPImageReader.open(source, this);
    }

    /// Opens a forward-only reader for a WebP file.
    ///
    /// The returned reader owns its file channel and closes it when the reader is closed.
    ///
    /// @param path the WebP file path
    /// @return a new reader configured by this decoder
    /// @throws IOException if the file cannot be opened or read
    /// @throws WebPException if the file cannot be parsed
    /// @throws NullPointerException if `path` is `null`
    public WebPImageReader open(Path path) throws IOException, WebPException {
        Objects.requireNonNull(path, "path");
        return WebPImageReader.open(path, this);
    }

    /// Reads and fully decodes a WebP stream.
    ///
    /// The supplied stream is closed before this method returns, including when decoding fails.
    ///
    /// @param source the WebP byte stream
    /// @return the fully decoded image
    /// @throws WebPException if the stream cannot be parsed, decoded, or closed
    /// @throws NullPointerException if `source` is `null`
    public WebPImage read(InputStream source) throws WebPException {
        try (WebPImageReader reader = open(source)) {
            return WebPImage.collect(reader);
        } catch (IOException ex) {
            if (ex instanceof WebPException webPException) {
                throw webPException;
            }
            throw new WebPException("Failed to decode WebP stream", ex);
        }
    }

    /// Reads and fully decodes a WebP file.
    ///
    /// @param path the WebP file path
    /// @return the fully decoded image
    /// @throws WebPException if the file cannot be opened, parsed, decoded, or closed
    /// @throws NullPointerException if `path` is `null`
    public WebPImage read(Path path) throws WebPException {
        try (WebPImageReader reader = open(path)) {
            return WebPImage.collect(reader);
        } catch (WebPException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new WebPException("Failed to decode WebP file: " + path, ex);
        }
    }

    /// Creates a decoded frame from non-premultiplied `ARGB` pixels.
    ///
    /// @param width the frame width
    /// @param height the frame height
    /// @param durationMillis the frame presentation duration
    /// @param argbPixels the source non-premultiplied pixels
    /// @param copyArgb whether heap output must copy rather than take ownership
    /// @return a frame using this decoder's resolved pixel format and storage
    WebPFrame createFrame(int width, int height, int durationMillis, int[] argbPixels, boolean copyArgb) {
        WebPFrameStorage resolvedStorage = resolveFrameStorage(argbPixels.length);
        return new WebPFrame(
                width,
                height,
                durationMillis,
                argbPixels,
                pixelFormat,
                resolvedStorage,
                copyArgb
        );
    }

    /// Resolves the configured storage policy for one decoded frame.
    ///
    /// @param pixelCount the number of pixels retained by the frame
    /// @return either [WebPFrameStorage#HEAP] or [WebPFrameStorage#DIRECT]
    private WebPFrameStorage resolveFrameStorage(int pixelCount) {
        if (frameStorage != WebPFrameStorage.AUTO) {
            return frameStorage;
        }
        long byteCount = (long) pixelCount * Integer.BYTES;
        return byteCount >= AUTO_DIRECT_THRESHOLD_BYTES
                ? WebPFrameStorage.DIRECT
                : WebPFrameStorage.HEAP;
    }
}
