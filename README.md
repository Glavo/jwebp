# JWebP - Pure Java WebP Decoder

[![codecov](https://codecov.io/gh/Glavo/jwebp/graph/badge.svg?token=CPZ7P35UK3)](https://codecov.io/gh/Glavo/jwebp)

A dependency-free, pure Java WebP decoder library that supports lossless and lossy compressed WebP images, as well as animated WebP.

This project was ported with Codex assistance from [image-rs/image-webp](https://github.com/image-rs/image-webp).

## Features

- Pure Java implementation with no native dependencies.
- Only depends on the `java.base` module, no dependency on other modules.
- Supports lossy and lossless compressed WebP images.
- Supports animated WebP.
- Supports image scaling during reading.
- Raw ICC, EXIF, and XMP metadata extraction
- Provides optional JavaFX helper functionality for easily converting WebP images to JavaFX images.

## Requirements

- Java 17 or newer

## Basic Usage

Decode a whole image at once:

```java
WebPImage image = WebPDecoder.decodeAll(Path.of("sample.webp"));
System.out.println(image.getWidth() + "x" + image.getHeight());
System.out.println("frames = " + image.getFrames().size());
```

Create a JavaFX image from decoded WebP content:

```java
try (InputStream input = Files.newInputStream(Path.of("/image.webp"))) {
    WebPFXImage image = new WebPFXImage(WebPDecoder.decodeAll(input, options), new WebPImageLoadOptions(320, 240, true, true));
}
```

Stream frames from an animated WebP:

```java
try (InputStream input = Files.newInputStream(Path.of("/animated.webp"));
     WebPImageReader reader = WebPDecoder.open(input)) {
    while (true) {
        WebPFrame frame = reader.readNextFrame();
        if (frame == null) {
            break;
        }
        System.out.println("duration = " + frame.getDurationMillis());
    }
}
```

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
