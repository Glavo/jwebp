// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.glavo.webp.WebPException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests canonical VP8L Huffman tree construction and workspace reuse.
@NotNullByDefault
final class LosslessHuffmanTreeTest {

    /// Verifies that reused offset storage is overwritten for every active code length.
    @Test
    void reusesBuildWorkspaceAcrossTreeDepths() throws WebPException {
        LosslessHuffmanTree.BuildWorkspace workspace = new LosslessHuffmanTree.BuildWorkspace();
        LosslessHuffmanTree.implicit(new int[]{1, 2, 3, 3}, workspace);

        LosslessHuffmanTree tree = LosslessHuffmanTree.implicit(new int[]{2, 2, 2, 2}, workspace);
        LosslessBitReader reader = new LosslessBitReader(new byte[]{(byte) 0b1101_1000});
        reader.fill();

        assertEquals(0, tree.readSymbol(reader));
        assertEquals(1, tree.readSymbol(reader));
        assertEquals(2, tree.readSymbol(reader));
        assertEquals(3, tree.readSymbol(reader));
    }

    /// Verifies that a lone nonzero code length retains its original symbol index.
    @Test
    void tracksSingleSymbolDuringHistogramConstruction() throws WebPException {
        int[] codeLengths = new int[280];
        codeLengths[279] = 1;

        LosslessHuffmanTree tree = LosslessHuffmanTree.implicit(
                codeLengths,
                new LosslessHuffmanTree.BuildWorkspace()
        );

        assertTrue(tree.isSingleNode());
        assertEquals(279, tree.readSymbol(new LosslessBitReader(new byte[0])));
    }
}
