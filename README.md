# JWebP - Pure Java WebP Decoder

[![codecov](https://codecov.io/gh/Glavo/jwebp/graph/badge.svg?token=CPZ7P35UK3)](https://codecov.io/gh/Glavo/jwebp)
[![](https://img.shields.io/maven-central/v/org.glavo/webp?label=Maven%20Central)](https://search.maven.org/artifact/org.glavo/webp)
[![javadoc](https://javadoc.io/badge2/org.glavo/webp/javadoc.svg)](https://javadoc.io/doc/org.glavo/webp)

A dependency-free, pure Java WebP decoder library that supports lossless and lossy compressed WebP images, as well as animated WebP.

This project was ported with Codex assistance from [image-rs/image-webp](https://github.com/image-rs/image-webp).

We have ported test cases from image-rs and libwebp to verify its correctness,
and it has been used in [Hello Minecraft! Launcher](https://github.com/HMCL-dev/HMCL).

## Features

- Pure Java implementation with no native dependencies.
- Only depends on the `java.base` module, no dependency on other modules.
- Supports lossy and lossless compressed WebP images.
- Supports animated WebP.
- Supports heap-backed and direct frame buffers in `INT_ARGB` and `INT_ARGB_PRE` formats.
- Raw ICC, EXIF, and XMP metadata extraction
- Provides optional JavaFX and Swing helper functionality for easily converting WebP images to JavaFX and Swing images.

## Requirements

- Java 17 or newer

## Download

Gradle:

```kotlin
dependencies {
    implementation("org.glavo:webp:0.2.0")
}
```

Maven:

```xml

<dependency>
    <groupId>org.glavo</groupId>
    <artifactId>webp</artifactId>
    <version>0.2.0</version>
</dependency>
```


## Basic Usage

Decode a whole image at once:

```java
WebPImage image = WebPImage.read(Path.of("sample.webp"));
System.out.println(image.getWidth() + "x" + image.getHeight());
System.out.println("frames = " + image.getFrames().size());
System.out.println("pixels = " + image.getFirstFrame().getArgbPixels());
```

Use an immutable decoder configuration when a different pixel representation or buffer location is
needed:

```java
WebPDecoder decoder = WebPDecoder.DEFAULT
        .withPixelFormat(WebPPixelFormat.INT_ARGB_PRE)
        .withDirect(true);

WebPImage image = decoder.read(Path.of("sample.webp"));
```

Stream frames from an animated WebP:

```java
try (InputStream input = Files.newInputStream(Path.of("/animated.webp"));
     WebPImageReader reader = WebPDecoder.DEFAULT.open(input)) {
    while (true) {
        WebPFrame frame = reader.readNextFrame();
        if (frame == null) {
            break;
        }
        System.out.println("duration = " + frame.getDurationMillis());
    }
}
```

`readNextFrame(boolean direct)` can override the `WebPDecoder` default buffer location for one frame
without changing the default used by later calls.

### JavaFX Integration

JWebP's core part only depends on the `java.base` module, which can work normally on the Android platform.

However, JWebP also provides optional components for JavaFX, located in the `org.glavo.webp.javafx` package,
which can easily convert `WebPImage` to JavaFX `Image`:

```java
WebPDecoder fxDecoder = WebPDecoder.DEFAULT
        .withPixelFormat(WebPPixelFormat.INT_ARGB_PRE)
        .withDirect(true);

// Create a JavaFX image from a WebPImage.
// If it is an animated WebP, it will automatically play the animation.
// You can control its presentation by passing WebPFXImageOptions.
javafx.scene.image.Image image = WebPFXImage.of(fxDecoder.read(...));

// Create a JavaFX image from a WebPFrame.
javafx.scene.image.Image frameImage = WebPFXImage.of(fxDecoder.read(...).getFirstFrame());

// Scale into a 640-by-480 bounding box while preserving the aspect ratio.
WebPFXImageOptions fxOptions = WebPFXImageOptions.DEFAULT
        .withRequestedSize(640, 480)
        .withPreserveRatio(true)
        .withSmooth(true);
javafx.scene.image.Image scaledImage = WebPFXImage.of(
        fxDecoder.read(...),
        fxOptions
);
```

`WebPFXImageOptions` configures the requested size, aspect-ratio preservation, smooth filtering,
and animation autoplay without ambiguous positional boolean arguments. Scaling affects only the
JavaFX presentation; decoded `WebPFrame` and `WebPImage` objects retain their intrinsic dimensions.
Intrinsic-size static `INT_ARGB_PRE` frames are used directly as the JavaFX `PixelBuffer` backing
store. When conversion or scaling requires a new buffer, it follows the source frame's heap or
direct storage location.

### Swing Integration

JWebP provides an optional Swing integration component located in the `org.glavo.webp.swing` package, 
which can easily convert `WebPImage` to Swing `BufferedImage`:

```java
// Create a Swing image from a WebPImage.
BufferedImage _ = WebPSwingUtils.fromWebPImage(WebPImage.read(...));

// Create a Swing image from a WebPFrame.
BufferedImage _ = WebPSwingUtils.fromWebPImage(WebPImage.read(...).getFirstFrame());
```

Currently, it only supports creating static `BufferedImage`, does not support animation and `ImageIO`.

### JWebP Image Viewer

We provide a sample application: JWebP Image Viewer.

This is a simple image viewer based on JWebP; you can use any Java environment containing JavaFX, run it via `java -jar webp.jar`.

You can download the latest version of JWebP Image Viewer from [GitHub Releases](https://github.com/Glavo/JWebP/releases).

![](./demo.webp)

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
- regression and conformance fixtures downloaded from pinned Chromium and Firefox commits

`processTestResources` depends on two explicit Gradle download tasks:

- `downloadChromiumWebPTestData` selects fixtures from Chromium commit
  [`8f4baaae073181e7e0fea1807f8db6ad720dbcb7`](https://github.com/chromium/chromium/tree/8f4baaae073181e7e0fea1807f8db6ad720dbcb7/third_party/blink/web_tests/images/resources)
- `downloadFirefoxWebPTestData` selects fixtures from Firefox commit
  [`4272397b835a480b1be6cee142d0fa39e166dbc6`](https://github.com/mozilla-firefox/firefox/tree/4272397b835a480b1be6cee142d0fa39e166dbc6/image/test)

The selected files are cached under `build/downloads` and only enter the test resource set; they
are not packaged in the library artifacts.
