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
///
/// The direct setting applies only to buffers retained by returned [WebPFrame] instances. Decoder
/// workspaces and animation compositing buffers may still use heap memory. Direct allocations are
/// subject to the JVM's direct-memory limit and may remain allocated until their frame and all
/// derived buffer views become unreachable.
@NotNullByDefault
public final class WebPDecoder {

    /// The default decoder, which produces heap-backed non-premultiplied `INT_ARGB` frames.
    public static final WebPDecoder DEFAULT = new WebPDecoder(
            WebPPixelFormat.INT_ARGB,
            false
    );

    /// Pixel representation requested for decoded frames.
    private final WebPPixelFormat pixelFormat;

    /// Whether decoded frames use direct buffers by default.
    private final boolean direct;

    /// Creates an immutable decoder configuration.
    ///
    /// @param pixelFormat the decoded frame pixel format
    /// @param direct whether decoded frames use direct buffers by default
    private WebPDecoder(WebPPixelFormat pixelFormat, boolean direct) {
        this.pixelFormat = pixelFormat;
        this.direct = direct;
    }

    /// Returns the requested decoded frame pixel format.
    ///
    /// @return the pixel format
    public WebPPixelFormat getPixelFormat() {
        return pixelFormat;
    }

    /// Returns whether decoded frames use direct buffers by default.
    ///
    /// A [WebPImageReader] may override this value for an individual frame through
    /// [WebPImageReader#readNextFrame(boolean)].
    ///
    /// @return `true` if the default output is direct
    public boolean isDirect() {
        return direct;
    }

    /// Returns a decoder that produces frames in the supplied pixel format.
    ///
    /// @param pixelFormat the requested pixel format
    /// @return this decoder if the format is unchanged; otherwise an independently reusable decoder
    /// @throws NullPointerException if `pixelFormat` is `null`
    public WebPDecoder withPixelFormat(WebPPixelFormat pixelFormat) {
        Objects.requireNonNull(pixelFormat, "pixelFormat");
        return this.pixelFormat == pixelFormat ? this : new WebPDecoder(pixelFormat, direct);
    }

    /// Returns a decoder that uses heap or direct frame buffers by default.
    ///
    /// @param direct `true` for direct buffers, or `false` for heap buffers
    /// @return this decoder if the default is unchanged; otherwise an independently reusable decoder
    public WebPDecoder withDirect(boolean direct) {
        return this.direct == direct ? this : new WebPDecoder(pixelFormat, direct);
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
    /// @return a frame using this decoder's pixel format and default buffer location
    WebPFrame createFrame(int width, int height, int durationMillis, int[] argbPixels, boolean copyArgb) {
        return createFrame(width, height, durationMillis, argbPixels, direct, copyArgb);
    }

    /// Creates a decoded frame with an explicit buffer-location override.
    ///
    /// @param width the frame width
    /// @param height the frame height
    /// @param durationMillis the frame presentation duration
    /// @param argbPixels the source non-premultiplied pixels
    /// @param direct whether the returned frame uses a direct buffer
    /// @param copyArgb whether heap output must copy rather than take ownership
    /// @return a frame using this decoder's pixel format and the requested buffer location
    WebPFrame createFrame(
            int width,
            int height,
            int durationMillis,
            int[] argbPixels,
            boolean direct,
            boolean copyArgb
    ) {
        return new WebPFrame(
                width,
                height,
                durationMillis,
                argbPixels,
                pixelFormat,
                direct,
                copyArgb
        );
    }
}
