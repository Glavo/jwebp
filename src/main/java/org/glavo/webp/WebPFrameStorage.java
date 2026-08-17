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

/// Selects where decoded presentation-frame pixels are stored.
///
/// This setting applies to the buffers retained by returned [WebPFrame] instances. Decoder
/// workspaces and animation compositing buffers may still use heap memory independently.
@NotNullByDefault
public enum WebPFrameStorage {

    /// Stores frame pixels in a heap-backed `IntBuffer`.
    HEAP,

    /// Stores frame pixels in a direct `IntBuffer`.
    ///
    /// Direct memory is released after the frame and its buffer views become unreachable and the
    /// JVM reclaims the backing buffer. Allocation remains subject to the JVM's direct-memory limit.
    DIRECT,

    /// Lets the decoder select heap or direct storage from the decoded frame size.
    ///
    /// The selection policy is an implementation detail and may change between releases. A frame
    /// created with this setting reports the resolved [#HEAP] or [#DIRECT] storage kind.
    AUTO
}
