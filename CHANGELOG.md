# Changelog

## 0.3.0 (In development)

### Breaking Changes

- Change the project license from Apache-2.0 to MPL-2.0 for version 0.3.0
- Remove `WebPImageLoadOptions`, the scale-aware `WebPImage#read` and `WebPImageReader#open` overloads, and the `getSourceWidth()` / `getSourceHeight()` methods; core images are now always decoded at their intrinsic canvas size, with presentation scaling available through `WebPFXImage#of(...)` and `WebPFXImageOptions`
- Replace the public `WebPFXImage` constructors and positional configuration overloads with `WebPFXImage#of(...)` factory methods using `WebPFXImageOptions`
- Remove `WebPDecoder`; `WebPImage` now accepts only a pixel format and always uses heap-backed frames, while `WebPImageReader` selects the format per frame and requires a caller-provided `IntBuffer` for direct storage
- Replace `WebPImageReader#readNextFrame(boolean)` with `readNextFrame(WebPPixelFormat)` and `readNextFrame(WebPPixelFormat, IntBuffer)`
- `WebPFXImage#getPixelWriter()` is now unsupported because `WebPFXImage` is backed by a `PixelBuffer`

### Added

- New API: `WebPFrame#getArgb(int, int)`
- New API: `WebPSwingUtils`
- Heap-backed and caller-provided frame buffers for `INT_ARGB` and `INT_ARGB_PRE` pixels during streaming decode
- New API: `WebPFrame#usesCustomPixelBuffer()`
- New immutable `WebPFXImageOptions` API for JavaFX presentation scaling, filtering, and animation autoplay
- New `WebPFXImage#read(Path, ...)` and `WebPFXImage#read(InputStream, ...)` APIs for direct JavaFX-oriented decoding

### Changed

- Back JavaFX images with adaptive `PixelBuffer` storage: ordinary images use heap memory, while large static images and animations with a large combined retained size use direct memory
- For scaled animated JavaFX images, scale frames once during construction and retain only their target-size pixel storage

### Performance

- Improve VP8 and VP8L decoding throughput and substantially reduce temporary allocations, especially for animated WebP images
- Decode static frames directly into caller-provided storage, avoiding a full-size heap `ARGB` staging array and the subsequent copy
- Pack prepared animation frames into bounded adaptive-memory chunks, reducing allocation objects and moving only large frame sets off heap
- Decode JavaFX images without constructing an intermediate heap-backed `WebPImage`
- Reuse compatible premultiplied frame storage for non-premultiplied pixel access

### Fixed

- Ignore `ALPH` chunks when the `VP8X` alpha feature bit is absent
- Reject invalid WebP containers containing a non-leading `VP8X` chunk
- Reject static extended WebP containers whose VP8 or VP8L dimensions differ from the `VP8X` canvas
- Avoid large eager allocations when a truncated input stream declares an oversized chunk payload

## 0.2.0 (2026-04-19)

- Fix the issue of accidentally including OpenJFX dependency in Maven pom.
- Fix broken links in Javadoc documentation.

## 0.1.0 (2026-04-19)

- Initial release
