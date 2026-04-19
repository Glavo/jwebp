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
- Supports image scaling during reading.
- Raw ICC, EXIF, and XMP metadata extraction
- Provides optional JavaFX helper functionality for easily converting WebP images to JavaFX images.

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
```

Create a JavaFX image from decoded WebP content:

```java
try (InputStream input = Files.newInputStream(Path.of("/image.webp"))) {
    // Create a JavaFX image from a WebPImage.
    // If it is an animated WebP, it will automatically play the animation. 
    // You can control its behavior by passing the autoplay parameter.
    Image image = new WebPFXImage(WebPImage.read(input, options));
}
```

Stream frames from an animated WebP:

```java
try (InputStream input = Files.newInputStream(Path.of("/animated.webp"));
     WebPImageReader reader = WebPImageReader.open(input)) {
    while (true) {
        WebPFrame frame = reader.readNextFrame();
        if (frame == null) {
            break;
        }
        System.out.println("duration = " + frame.getDurationMillis());

        // Create a JavaFX image from a WebPFrame.
        Image image = new WebPFXImage(frame);
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
