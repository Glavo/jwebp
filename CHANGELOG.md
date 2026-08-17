# Changelog

## 0.3.0 (In development)

### Breaking Changes

- Change the project license from Apache-2.0 to MPL-2.0 for version 0.3.0
- Remove `WebPImageLoadOptions`, the scale-aware `WebPImage#read` and `WebPImageReader#open` overloads, and the `getSourceWidth()` / `getSourceHeight()` methods; core images are now always decoded at their intrinsic canvas size, with presentation scaling available through `WebPFXImage#of(...)` and `WebPFXImageOptions`
- Replace the public `WebPFXImage` constructors and positional configuration overloads with `WebPFXImage#of(...)` factory methods using `WebPFXImageOptions`
- `WebPFXImage#getPixelWriter()` is now unsupported because `WebPFXImage` is backed by a `PixelBuffer`

### Added

- New API: `WebPFrame#getArgb(int, int)`
- New API: `WebPSwingUtils`
- New immutable `WebPDecoder` API for configuring decoded pixel format and the default frame buffer location
- Heap-backed and direct frame buffers for `INT_ARGB` and `INT_ARGB_PRE` pixels, with per-frame overrides during streaming decode
- New immutable `WebPFXImageOptions` API for JavaFX presentation scaling, filtering, and animation autoplay

### Changed

- Back JavaFX images with `PixelBuffer`, allowing intrinsic-size static `INT_ARGB_PRE` frames to be presented without copying
- For scaled animated JavaFX images, scale frames once during construction and retain only their target-size pixel storage

### Performance

- Improve VP8 and VP8L decoding throughput and substantially reduce temporary allocations, especially for animated WebP images
- Decode static direct-buffer frames into their final storage, avoiding a full-size heap `ARGB` staging array and the subsequent copy

### Fixed

- Ignore `ALPH` chunks when the `VP8X` alpha feature bit is absent
- Reject invalid WebP containers containing a non-leading `VP8X` chunk

## 0.2.0 (2026-04-19)

- Fix the issue of accidentally including OpenJFX dependency in Maven pom.
- Fix broken links in Javadoc documentation.

## 0.1.0 (2026-04-19)

- Initial release
