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
package org.glavo.javafx.webp;

import javafx.scene.image.WritableImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Entry point for decoding WebP content.
///
/// The class offers both eager decoding through [#decodeAll(InputStream, WebPImageLoadOptions)]
/// and forward-only frame consumption through [#open(InputStream, WebPImageLoadOptions)].
public final class WebPDecoder {

    private WebPDecoder() {
    }

    /// Opens a forward-only image reader.
    ///
    /// @param input the WebP byte stream
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return a streaming reader positioned before the first frame
    /// @throws WebPException if the stream cannot be parsed or a decoder cannot be created
    public static WebPImageReader open(InputStream input, WebPImageLoadOptions options) throws WebPException {
        return WebPImageReader.open(input, options);
    }

    /// Opens a forward-only image reader using the default options.
    ///
    /// @param input the WebP byte stream
    /// @return a streaming reader positioned before the first frame
    /// @throws WebPException if the stream cannot be parsed or a decoder cannot be created
    public static WebPImageReader open(InputStream input) throws WebPException {
        return open(input, WebPImageLoadOptions.defaults());
    }

    /// Opens a forward-only image reader for a file on disk.
    ///
    /// @param path the WebP file path
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return a streaming reader positioned before the first frame
    /// @throws WebPException if the file cannot be opened or parsed
    public static WebPImageReader open(Path path, WebPImageLoadOptions options) throws WebPException {
        try {
            return WebPImageReader.open(path, options);
        } catch (IOException ex) {
            throw new WebPException("Failed to open WebP file: " + path, ex);
        }
    }

    /// Opens a forward-only image reader for a file on disk using the default options.
    ///
    /// @param path the WebP file path
    /// @return a streaming reader positioned before the first frame
    /// @throws WebPException if the file cannot be opened or parsed
    public static WebPImageReader open(Path path) throws WebPException {
        return open(path, WebPImageLoadOptions.defaults());
    }

    /// Decodes the entire stream into an immutable [WebPImage].
    ///
    /// @param input the WebP byte stream
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return the fully decoded image
    /// @throws WebPException if parsing or decoding fails
    public static WebPImage decodeAll(InputStream input, WebPImageLoadOptions options) throws WebPException {
        try (WebPImageReader reader = open(input, options)) {
            return collect(reader);
        } catch (IOException ex) {
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            throw new WebPException("Failed to decode WebP stream", ex);
        }
    }

    /// Decodes the entire stream using the default options.
    ///
    /// @param input the WebP byte stream
    /// @return the fully decoded image
    /// @throws WebPException if parsing or decoding fails
    public static WebPImage decodeAll(InputStream input) throws WebPException {
        return decodeAll(input, WebPImageLoadOptions.defaults());
    }

    /// Decodes the entire file into an immutable [WebPImage].
    ///
    /// @param path the WebP file path
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return the fully decoded image
    /// @throws WebPException if the file cannot be parsed or decoded
    public static WebPImage decodeAll(Path path, WebPImageLoadOptions options) throws WebPException {
        try (InputStream input = Files.newInputStream(path)) {
            return decodeAll(input, options);
        } catch (IOException ex) {
            throw new WebPException("Failed to decode WebP file: " + path, ex);
        }
    }

    /// Decodes the entire file using the default options.
    ///
    /// @param path the WebP file path
    /// @return the fully decoded image
    /// @throws WebPException if the file cannot be parsed or decoded
    public static WebPImage decodeAll(Path path) throws WebPException {
        return decodeAll(path, WebPImageLoadOptions.defaults());
    }

    /// Decodes only the first frame and converts it to a JavaFX image.
    ///
    /// @param input the WebP byte stream
    /// @param options scaling options that mirror JavaFX `Image` loading parameters
    /// @return the decoded JavaFX image
    /// @throws WebPException if parsing or decoding fails
    public static WritableImage decodeFirstFrameImage(InputStream input, WebPImageLoadOptions options) throws WebPException {
        try (WebPImageReader reader = open(input, options)) {
            WebPFrame frame = reader.readNextFrame().orElseThrow(() -> new WebPException("WebP stream contains no decodable frames"));
            return frame.toWritableImage();
        } catch (IOException ex) {
            if (ex instanceof WebPException webpException) {
                throw webpException;
            }
            throw new WebPException("Failed to decode WebP stream", ex);
        }
    }

    private static WebPImage collect(WebPImageReader reader) throws IOException {
        List<WebPFrame> frames = new ArrayList<>(Math.max(1, reader.getFrameCount()));
        while (true) {
            var next = reader.readNextFrame();
            if (next.isEmpty()) {
                break;
            }
            frames.add(next.get());
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
