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

import java.util.Objects;

/// Describes how many times an animated WebP should loop.
///
/// The WebP container stores loop count as the number of animation repetitions.
/// A value of `0` means that the animation repeats forever. The library exposes
/// that state explicitly to avoid sentinel integers in user code.
public final class LoopCount {

    private static final LoopCount FOREVER = new LoopCount(true, 0);

    private final boolean forever;
    private final int repetitions;

    private LoopCount(boolean forever, int repetitions) {
        this.forever = forever;
        this.repetitions = repetitions;
    }

    /// Returns a loop count that repeats forever.
    ///
    /// @return the singleton representation for infinite looping
    public static LoopCount forever() {
        return FOREVER;
    }

    /// Returns a finite loop count.
    ///
    /// @param repetitions the number of animation repetitions, must be positive
    /// @return a loop count with the requested number of repetitions
    /// @throws IllegalArgumentException if `repetitions <= 0`
    public static LoopCount of(int repetitions) {
        if (repetitions <= 0) {
            throw new IllegalArgumentException("repetitions must be positive");
        }
        return new LoopCount(false, repetitions);
    }

    /// Returns whether the animation loops forever.
    ///
    /// @return `true` if the loop count is infinite
    public boolean isForever() {
        return forever;
    }

    /// Returns the finite repetition count.
    ///
    /// This method is only valid for finite loop counts.
    ///
    /// @return the number of animation repetitions
    /// @throws IllegalStateException if the animation loops forever
    public int getRepetitions() {
        if (forever) {
            throw new IllegalStateException("loop count is infinite");
        }
        return repetitions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LoopCount that)) {
            return false;
        }
        return forever == that.forever && repetitions == that.repetitions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(forever, repetitions);
    }

    @Override
    public String toString() {
        return forever ? "LoopCount[forever]" : "LoopCount[" + repetitions + "]";
    }
}
