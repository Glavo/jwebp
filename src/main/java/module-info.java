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

import org.glavo.webp.WebPDecoder;
import org.glavo.webp.WebPImageReader;

/// WebP decoding library for JavaFX.
///
/// The module exposes a public API in `org.glavo.webp` for reading static and
/// animated WebP images, extracting metadata, and converting decoded frames to JavaFX images.
/// Decoded frame pixels are exposed as packed non-premultiplied `ARGB` integers.
/// Two entry points are provided:
///
///   - [WebPDecoder] for eager convenience methods;
///   - [WebPImageReader] for forward-only frame-by-frame decode.
///
/// The decoder is implemented in pure Java. It does not depend on `java.desktop` or any
/// external WebP codec, and supports decode-time scaling with the same
/// `requestedWidth/requestedHeight/preserveRatio/smooth` semantics used by
/// [javafx.scene.image.Image].
///
/// `javafx.controls` is only required for the bundled
/// [WebPViewerApp] demo application, so the dependency remains
/// optional at compile time.
module org.glavo.webp {
    requires static org.jetbrains.annotations;

    // Optional dependencies; if present, functionality in the org.glavo.webp.javafx package can be used.
    requires static javafx.graphics;
    requires static javafx.controls;

    exports org.glavo.webp;
    exports org.glavo.webp.javafx;
}
