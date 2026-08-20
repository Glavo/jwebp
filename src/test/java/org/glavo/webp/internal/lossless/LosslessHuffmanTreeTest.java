// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.glavo.webp.WebPException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests canonical VP8L Huffman tree construction and workspace reuse.
@NotNullByDefault
final class LosslessHuffmanTreeTest {

    /// Verifies that the packed single-node state rejects values that overlap the marker bit.
    @Test
    void rejectsNegativeSingleSymbol() {
        assertThrows(IllegalArgumentException.class, () -> LosslessHuffmanTree.single(-1));
    }

    /// Verifies that reused offset storage is overwritten for every active code length.
    @Test
    void reusesBuildWorkspaceAcrossTreeDepths() throws WebPException {
        LosslessHuffmanTree.BuildWorkspace workspace = new LosslessHuffmanTree.BuildWorkspace();
        LosslessHuffmanTree.implicit(new byte[]{1, 2, 3, 3}, 4, workspace);

        LosslessHuffmanTree tree = LosslessHuffmanTree.implicit(new byte[]{2, 2, 2, 2}, 4, workspace);
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
        byte[] codeLengths = new byte[280];
        codeLengths[279] = 1;

        LosslessHuffmanTree tree = LosslessHuffmanTree.implicit(
                codeLengths,
                codeLengths.length,
                new LosslessHuffmanTree.BuildWorkspace()
        );

        assertTrue(tree.isSingleNode());
        assertEquals(279, tree.readSymbol(new LosslessBitReader(new byte[0])));
    }

    /// Verifies that stale entries beyond the active alphabet are not inspected.
    @Test
    void ignoresUnusedCodeLengthStorage() throws WebPException {
        byte[] codeLengths = {1, 1, Byte.MAX_VALUE};

        LosslessHuffmanTree tree = LosslessHuffmanTree.implicit(
                codeLengths,
                2,
                new LosslessHuffmanTree.BuildWorkspace()
        );
        LosslessBitReader reader = new LosslessBitReader(new byte[]{0});
        reader.fill();

        assertEquals(0, tree.readSymbol(reader));
    }

    /// Verifies that codes longer than the primary lookup width retain their symbol and length.
    @Test
    void readsSymbolsFromSecondaryTable() throws WebPException {
        byte[] codeLengths = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10};
        LosslessHuffmanTree tree = LosslessHuffmanTree.implicit(
                codeLengths,
                codeLengths.length,
                new LosslessHuffmanTree.BuildWorkspace()
        );

        LosslessBitReader reader = new LosslessBitReader(new byte[]{(byte) 0xFF, 0x01});
        reader.fill();

        assertEquals(9, tree.readSymbol(reader));
        assertEquals(6, reader.bitCount());

        reader = new LosslessBitReader(new byte[]{(byte) 0xFF, 0x03});
        reader.fill();

        assertEquals(10, tree.readSymbol(reader));
        assertEquals(6, reader.bitCount());
    }
}
