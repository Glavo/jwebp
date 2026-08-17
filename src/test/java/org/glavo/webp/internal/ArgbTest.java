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
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests packed `ARGB` channel arithmetic.
@NotNullByDefault
final class ArgbTest {

    /// Verifies that packed addition matches independent modulo-256 channel arithmetic.
    @Test
    void addsChannelsIndependently() {
        Random random = new Random(0x4A_57_45_42_50L);
        for (int iteration = 0; iteration < 100_000; iteration++) {
            int left = random.nextInt();
            int right = random.nextInt();
            int expected = Argb.pack(
                    Argb.alpha(left) + Argb.alpha(right),
                    Argb.red(left) + Argb.red(right),
                    Argb.green(left) + Argb.green(right),
                    Argb.blue(left) + Argb.blue(right)
            );
            assertEquals(expected, Argb.add(left, right));
        }
    }
}
