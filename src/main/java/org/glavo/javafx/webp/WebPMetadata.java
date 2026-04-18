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
package org.glavo.javafx.webp;

import java.util.Arrays;
import java.util.Optional;

/// Raw metadata chunks extracted from a WebP container.
///
/// The WebP specification stores ICC, EXIF and XMP payloads as opaque byte arrays. This class
/// exposes those payloads without attempting to interpret them, leaving higher-level processing to
/// the caller.
public final class WebPMetadata {

    private static final WebPMetadata EMPTY = new WebPMetadata(null, null, null);

    private final byte[] iccProfile;
    private final byte[] exifMetadata;
    private final byte[] xmpMetadata;

    /// Returns an empty metadata container.
    ///
    /// @return a metadata object with no payloads
    public static WebPMetadata empty() {
        return EMPTY;
    }

    /// Creates a metadata container from raw chunk payloads.
    ///
    /// @param iccProfile the ICC payload, or `null`
    /// @param exifMetadata the EXIF payload, or `null`
    /// @param xmpMetadata the XMP payload, or `null`
    public WebPMetadata(byte[] iccProfile, byte[] exifMetadata, byte[] xmpMetadata) {
        this.iccProfile = copyOrNull(iccProfile);
        this.exifMetadata = copyOrNull(exifMetadata);
        this.xmpMetadata = copyOrNull(xmpMetadata);
    }

    /// Returns the ICC profile chunk payload.
    ///
    /// @return the ICC profile bytes if present
    public Optional<byte[]> getIccProfile() {
        return optionalCopy(iccProfile);
    }

    /// Returns the EXIF metadata chunk payload.
    ///
    /// @return the EXIF bytes if present
    public Optional<byte[]> getExifMetadata() {
        return optionalCopy(exifMetadata);
    }

    /// Returns the XMP metadata chunk payload.
    ///
    /// @return the XMP bytes if present
    public Optional<byte[]> getXmpMetadata() {
        return optionalCopy(xmpMetadata);
    }

    private static byte[] copyOrNull(byte[] value) {
        return value == null ? null : value.clone();
    }

    private static Optional<byte[]> optionalCopy(byte[] value) {
        return value == null ? Optional.empty() : Optional.of(value.clone());
    }

    @Override
    public String toString() {
        return "WebPMetadata[icc=" + lengthOf(iccProfile)
                + ", exif=" + lengthOf(exifMetadata)
                + ", xmp=" + lengthOf(xmpMetadata) + "]";
    }

    private static int lengthOf(byte[] value) {
        return value == null ? 0 : value.length;
    }
}
