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
