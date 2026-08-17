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
package org.glavo.webp;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes the packed integer representation used by decoded frame pixels.
///
/// Both formats store one pixel per `int` as `0xAARRGGBB`. They differ only in whether the color
/// channels have been multiplied by the alpha channel.
@NotNullByDefault
public enum WebPPixelFormat {

    /// Stores non-premultiplied red, green, and blue channels.
    INT_ARGB(false),

    /// Stores red, green, and blue channels premultiplied by alpha.
    ///
    /// Converting to this format may discard color information from translucent pixels, especially
    /// pixels whose alpha channel is zero.
    INT_ARGB_PRE(true);

    /// Whether this format uses premultiplied color channels.
    private final boolean premultiplied;

    /// Creates a pixel-format constant.
    ///
    /// @param premultiplied whether the color channels are premultiplied by alpha
    WebPPixelFormat(boolean premultiplied) {
        this.premultiplied = premultiplied;
    }

    /// Returns whether the color channels are premultiplied by alpha.
    ///
    /// @return `true` for a premultiplied format
    public boolean isPremultiplied() {
        return premultiplied;
    }
}
