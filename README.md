# JavaFX WebP

[![codecov](https://codecov.io/gh/Glavo/javafx-webp/graph/badge.svg?token=CPZ7P35UK3)](https://codecov.io/gh/Glavo/javafx-webp)

Pure Java WebP decoding library for JavaFX.

The project can decode static and animated WebP
images without using `java.desktop` or any external WebP codec at runtime. Decoded frames are
available as tightly packed non-premultiplied `ARGB` `int[]` buffers and can be written directly
to JavaFX `WritableImage` instances through `PixelFormat.getIntArgbInstance()`.

This project was ported with Codex assistance from [image-rs/image-webp](https://github.com/image-rs/image-webp).

## Features

- Pure Java decoder for WebP container parsing, VP8L lossless decoding, VP8 lossy decoding, alpha, and animation composition
- Streaming-style API via `WebPImageReader`
- Eager convenience API via `WebPDecoder`
- Decode-time scaling with JavaFX `Image`-compatible `requestedWidth`, `requestedHeight`, `preserveRatio`, and `smooth` semantics
- Raw ICC, EXIF, and XMP metadata extraction
- No dependency on `java.desktop`

## Requirements

- Java 17 or newer
- JavaFX runtime for `javafx-base`, `javafx-graphics`

## Basic Usage

Decode a whole image at once:

```java
WebPImage image = WebPDecoder.decodeAll(Path.of("sample.webp"));
System.out.println(image.getWidth() + "x" + image.getHeight());
System.out.println("frames = " + image.getFrames().size());
```

Decode the first frame as a JavaFX image:

```java
WebPImageLoadOptions options = WebPImageLoadOptions.builder()
        .requestedWidth(320)
        .requestedHeight(240)
        .preserveRatio(true)
        .smooth(true)
        .build();

try (InputStream input = Files.newInputStream(Path.of("/image.webp"))) {
    WritableImage image = WebPDecoder.decodeFirstFrameImage(input, options);
}
```

Stream frames from an animated WebP:

```java
try (InputStream input = Files.newInputStream(Path.of("/animated.webp"));
     WebPImageReader reader = WebPDecoder.open(input)) {
    while (true) {
        var next = reader.readNextFrame();
        if (next.isEmpty()) {
            break;
        }

        WebPFrame frame = next.get();
        System.out.println("duration = " + frame.getDurationMillis());
    }
}
```

## Public API

- `WebPDecoder`: eager decode helpers and convenience methods
- `WebPImageReader`: forward-only reader for frame-by-frame decode
- `WebPImage`: immutable fully decoded result
- `WebPFrame`: one decoded presentation frame
  exposes packed non-premultiplied `ARGB` pixels via `getArgbPixels()` and `getArgbArray()`
- `WebPImageLoadOptions`: JavaFX-style scaling options
- `WebPMetadata`: raw ICC, EXIF, and XMP payloads
- `WebPException`: checked exception for parse and decode failures

## Testing

Run all tests:

```powershell
./gradlew test
```

The test suite includes:

- project-local decoder regression tests
- tests ported from `image-rs`
- tests ported from `libwebp`
- tests backed by the downloaded `libwebp-test-data` corpus
