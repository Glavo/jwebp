// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.glavo.webp.WebPException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

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

    /// Verifies reused primary and secondary tables for every possible 15-bit lookahead.
    @Test
    void reusesLookupStorageAcrossDepthsAndFailedBuilds() throws WebPException {
        LosslessHuffmanTree.BuildWorkspace workspace = new LosslessHuffmanTree.BuildWorkspace();
        LosslessHuffmanTree.TableStorage storage = new LosslessHuffmanTree.TableStorage();
        byte[][] alphabets = {
                {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15},
                {2, 2, 2, 2},
                {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10},
                {0, 1},
                {1, 1},
                {15, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1},
        };
        for (byte[] lengths : alphabets) {
            assertEquivalentLookup(lengths, workspace, storage, 1);
            assertThrows(WebPException.class, () -> LosslessHuffmanTree.implicit(
                    new byte[]{2, 2}, 2, workspace, storage
            ));
        }
    }

    /// Verifies table reuse with deterministic complete trees having varied code distributions.
    @Test
    void reusesLookupStorageAcrossRandomCompleteTrees() throws WebPException {
        Random random = new Random(0x48554646);
        LosslessHuffmanTree.BuildWorkspace workspace = new LosslessHuffmanTree.BuildWorkspace();
        LosslessHuffmanTree.TableStorage storage = new LosslessHuffmanTree.TableStorage();
        for (int iteration = 0; iteration < 100; iteration++) {
            ArrayList<Integer> depths = new ArrayList<>();
            depths.add(0);
            int symbols = 2 + random.nextInt(255);
            while (depths.size() < symbols) {
                int index = random.nextInt(depths.size());
                int depth = depths.get(index);
                if (depth < 15) {
                    depths.set(index, depth + 1);
                    depths.add(depth + 1);
                }
            }
            Collections.shuffle(depths, random);
            byte[] lengths = new byte[symbols];
            for (int index = 0; index < symbols; index++) {
                lengths[index] = depths.get(index).byteValue();
            }
            assertEquivalentLookup(lengths, workspace, storage, 127);
        }
    }

    /// Compares reused tables against independent tables, including symbol-consumption counts.
    ///
    /// @param lengths a complete canonical alphabet
    /// @param workspace reusable construction scratch
    /// @param storage lookup arrays populated by earlier builds
    /// @param step the increment between 15-bit lookahead values
    /// @throws WebPException if a complete alphabet unexpectedly fails to build or decode
    private static void assertEquivalentLookup(byte[] lengths, LosslessHuffmanTree.BuildWorkspace workspace,
                                               LosslessHuffmanTree.TableStorage storage, int step) throws WebPException {
        LosslessHuffmanTree expected = LosslessHuffmanTree.implicit(lengths, lengths.length, workspace);
        LosslessHuffmanTree actual = LosslessHuffmanTree.implicit(lengths, lengths.length, workspace, storage);
        byte[] bits = new byte[2];
        for (int value = 0; value < (1 << 15); value += step) {
            bits[0] = (byte) value;
            bits[1] = (byte) (value >>> 8);
            LosslessBitReader expectedInput = new LosslessBitReader(bits);
            LosslessBitReader actualInput = new LosslessBitReader(bits);
            expectedInput.fill();
            actualInput.fill();
            assertEquals(expected.peekSymbol(expectedInput), actual.peekSymbol(actualInput));
            assertEquals(expected.readSymbol(expectedInput), actual.readSymbol(actualInput));
            assertEquals(expectedInput.bitCount(), actualInput.bitCount());
        }
    }
}
