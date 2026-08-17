// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

import org.glavo.webp.WebPDecoder;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPImageReader;

/// Pure Java WebP decoder library.
///
/// The module exposes a public API in `org.glavo.webp` for reading static and
/// animated WebP images, extracting metadata, and converting decoded frames to optionally scaled
/// JavaFX images.
/// [WebPDecoder] configures the packed integer pixel representation and whether returned frame
/// buffers are direct by default. Three entry points are provided:
///
///   - [WebPDecoder] for immutable, reusable decoding configuration;
///   - [WebPImage] for eager full-image decode;
///   - [WebPImageReader] for forward-only frame-by-frame decode.
///
/// The decoder is implemented in pure Java. It does not depend on `java.desktop` or any
/// external WebP codec.
///
/// `javafx.controls` is only required for the bundled
/// [org.glavo.webp.javafx.WebPViewerApp] demo application, so the dependency remains
/// optional at compile time.
module org.glavo.webp {
    requires static org.jetbrains.annotations;

    // Optional dependencies; if present, functionality in the org.glavo.webp.swing package can be used.
    requires static java.desktop;

    // Optional dependencies; if present, functionality in the org.glavo.webp.javafx package can be used.
    requires static javafx.graphics;

    // Optional dependencies; only used for the Demo application, not required when used as a library.
    requires static javafx.controls;

    exports org.glavo.webp;
    exports org.glavo.webp.javafx;
    exports org.glavo.webp.swing;
}
