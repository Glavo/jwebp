# Changelog

## 0.3.0 (In development)

### Added

- New API: `WebPFrame#getArgb(int, int)`
- New API: `WebPSwingUtils`
- New immutable `WebPDecoder` API for configuring decoded pixel format and frame storage
- Heap-backed, direct, and automatically selected frame storage for `INT_ARGB` and `INT_ARGB_PRE` pixels

### Changed

- Decode images at their intrinsic canvas size and remove the JavaFX-style loading scale options
- Back JavaFX images with `PixelBuffer`, allowing static `INT_ARGB_PRE` frames to be presented without copying; `WebPFXImage#getPixelWriter()` is consequently unsupported

### Performance

- Improve VP8 and VP8L decoding throughput and substantially reduce temporary allocations, especially for animated WebP images

### Fixed

- Ignore `ALPH` chunks when the `VP8X` alpha feature bit is absent
- Reject invalid WebP containers containing a non-leading `VP8X` chunk

## 0.2.0 (2026-04-19)

- Fix the issue of accidentally including OpenJFX dependency in Maven pom.
- Fix broken links in Javadoc documentation.

## 0.1.0 (2026-04-19)

- Initial release
