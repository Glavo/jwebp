# Changelog

## 0.3.0 (In development)

### Added

- New API: `WebPFrame#getArgb(int, int)`
- New API: `WebPSwingUtils`

### Fixed

- Ignore `ALPH` chunks when the `VP8X` alpha feature bit is absent
- Reject invalid WebP containers containing a non-leading `VP8X` chunk

## 0.2.0 (2026-04-19)

- Fix the issue of accidentally including OpenJFX dependency in Maven pom.
- Fix broken links in Javadoc documentation.

## 0.1.0 (2026-04-19)

- Initial release
