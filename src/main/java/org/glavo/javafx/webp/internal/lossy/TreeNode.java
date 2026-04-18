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
package org.glavo.javafx.webp.internal.lossy;

/// One node in the VP8 boolean-decoder probability tree.
final class TreeNode {

    static final TreeNode UNINIT = new TreeNode((byte) 0, (byte) 0, 0, (byte) 0);

    final byte left;
    final byte right;
    int prob;
    final byte index;

    TreeNode(byte left, byte right, int prob, byte index) {
        this.left = left;
        this.right = right;
        this.prob = prob;
        this.index = index;
    }

    static byte prepareBranch(int branch) {
        if (branch > 0) {
            return (byte) (branch / 2);
        }
        return (byte) (0x80 | -branch);
    }

    static int valueFromBranch(int branch) {
        return branch & 0x7F;
    }
}
