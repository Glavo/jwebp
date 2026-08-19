// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossless;

import org.glavo.webp.internal.ArrayUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import org.glavo.webp.WebPException;

import java.util.Arrays;

/// Huffman tree implementation for VP8L.
@NotNullByDefault
public final class LosslessHuffmanTree {

    /// Maximum code length permitted by the VP8L format.
    private static final int MAX_ALLOWED_CODE_LENGTH = 15;

    /// Maximum width of the primary lookup table.
    private static final int MAX_TABLE_BITS = 9;

    private final boolean singleNode;
    private final int symbol;
    private final int tableMask;
    private final char @Nullable @Unmodifiable [] primaryTable;
    private final char @Nullable @Unmodifiable [] secondaryTable;

    private LosslessHuffmanTree(int symbol) {
        this.singleNode = true;
        this.symbol = symbol;
        this.tableMask = 0;
        this.primaryTable = null;
        this.secondaryTable = null;
    }

    private LosslessHuffmanTree(int tableMask, char[] primaryTable, char[] secondaryTable) {
        this.singleNode = false;
        this.symbol = 0;
        this.tableMask = tableMask;
        this.primaryTable = primaryTable;
        this.secondaryTable = secondaryTable;
    }

    /// Builds a single-symbol Huffman tree.
    ///
    /// @param symbol the only symbol in the tree
    /// @return the resulting tree
    public static LosslessHuffmanTree single(int symbol) {
        return new LosslessHuffmanTree(symbol);
    }

    /// Builds a two-symbol Huffman tree for the simplest explicit form.
    ///
    /// @param zero the symbol selected by bit `0`
    /// @param one the symbol selected by bit `1`
    /// @return the resulting tree
    public static LosslessHuffmanTree pair(int zero, int one) {
        return new LosslessHuffmanTree(
                0x1,
                new char[]{(char) ((1 << 12) | zero), (char) ((1 << 12) | one)},
                ArrayUtils.EMPTY_CHAR_ARRAY
        );
    }

    /// Builds a canonical Huffman tree while reusing temporary construction arrays.
    ///
    /// @param codeLengths the code lengths indexed by symbol
    /// @param workspace the mutable construction workspace
    /// @return the resulting tree
    /// @throws WebPException if the code lengths do not form a valid canonical tree
    static LosslessHuffmanTree implicit(int[] codeLengths, BuildWorkspace workspace) throws WebPException {
        int[] histogram = workspace.histogram;
        Arrays.fill(histogram, 0);
        int symbolCount = 0;
        int singleSymbol = -1;
        for (int symbol = 0; symbol < codeLengths.length; symbol++) {
            int length = codeLengths[symbol];
            if (length < 0 || length > MAX_ALLOWED_CODE_LENGTH) {
                throw new WebPException("Invalid Huffman code length");
            }
            histogram[length]++;
            if (length != 0) {
                symbolCount++;
                singleSymbol = symbol;
            }
        }

        if (symbolCount == 0) {
            throw new WebPException("Invalid Huffman code");
        }
        if (symbolCount == 1) {
            return single(singleSymbol);
        }

        int maxLength = MAX_ALLOWED_CODE_LENGTH;
        while (maxLength > 1 && histogram[maxLength] == 0) {
            maxLength--;
        }

        int[] offsets = workspace.offsets;
        int codeSpaceUsed = 0;
        offsets[1] = 0;
        for (int i = 1; i < maxLength; i++) {
            offsets[i + 1] = offsets[i] + histogram[i];
            codeSpaceUsed = (codeSpaceUsed << 1) + histogram[i];
        }
        codeSpaceUsed = (codeSpaceUsed << 1) + histogram[maxLength];
        if (codeSpaceUsed != (1 << maxLength)) {
            throw new WebPException("Invalid Huffman code");
        }

        int tableBits = Math.min(maxLength, MAX_TABLE_BITS);
        int tableSize = 1 << tableBits;
        char[] primaryTable = new char[tableSize];
        char[] sortedSymbols = workspace.acquireSortedSymbols(symbolCount);
        for (int symbol = 0; symbol < codeLengths.length; symbol++) {
            int length = codeLengths[symbol];
            if (length != 0) {
                sortedSymbols[offsets[length]++] = (char) symbol;
            }
        }

        int codeword = 0;
        int i = 0;
        int primaryTableMask = tableSize - 1;
        for (int length = 1; length <= tableBits; length++) {
            int currentTableEnd = 1 << length;
            for (int j = 0; j < histogram[length]; j++) {
                int symbol = sortedSymbols[i++];
                primaryTable[codeword] = (char) ((length << 12) | symbol);
                codeword = nextCodeword(codeword, currentTableEnd);
            }

            if (length < tableBits) {
                System.arraycopy(primaryTable, 0, primaryTable, currentTableEnd, currentTableEnd);
            }
        }

        char[] secondaryTable = ArrayUtils.EMPTY_CHAR_ARRAY;
        int secondaryLength = 0;
        if (maxLength > tableBits) {
            int firstSecondaryCodeword = codeword;
            secondaryLength = secondaryTableSize(
                    histogram,
                    tableBits,
                    maxLength,
                    firstSecondaryCodeword,
                    primaryTableMask
            );
            secondaryTable = new char[secondaryLength];

            codeword = firstSecondaryCodeword;
            secondaryLength = 0;
            int subtableStart = 0;
            int subtablePrefix = -1;

            for (int length = tableBits + 1; length <= maxLength; length++) {
                int subtableSize = 1 << (length - tableBits);
                for (int j = 0; j < histogram[length]; j++) {
                    if ((codeword & primaryTableMask) != subtablePrefix) {
                        subtablePrefix = codeword & primaryTableMask;
                        subtableStart = secondaryLength;
                        primaryTable[subtablePrefix] = (char) ((length << 12) | subtableStart);
                        secondaryLength += subtableSize;
                    }

                    int symbol = sortedSymbols[i++];
                    secondaryTable[subtableStart + (codeword >> tableBits)] = (char) ((symbol << 4) | length);
                    codeword = nextCodeword(codeword, 1 << length);
                }

                if (length < maxLength && (codeword & primaryTableMask) == subtablePrefix) {
                    int copyLength = secondaryLength - subtableStart;
                    System.arraycopy(secondaryTable, subtableStart, secondaryTable, secondaryLength, copyLength);
                    primaryTable[subtablePrefix] = (char) (((length + 1) << 12) | subtableStart);
                    secondaryLength += copyLength;
                }
            }
        }

        return new LosslessHuffmanTree(primaryTableMask, primaryTable, secondaryTable);
    }

    /// Reusable temporary arrays for canonical-tree construction.
    @NotNullByDefault
    static final class BuildWorkspace {
        /// Symbol count for each permitted code length.
        private final int[] histogram = new int[MAX_ALLOWED_CODE_LENGTH + 1];

        /// Starting output offset for each permitted code length.
        private final int[] offsets = new int[MAX_ALLOWED_CODE_LENGTH + 1];

        /// Symbols ordered by code length for the current tree.
        private char[] sortedSymbols = ArrayUtils.EMPTY_CHAR_ARRAY;

        /// Returns scratch storage large enough for the requested symbol count.
        ///
        /// @param symbolCount the number of non-zero-length symbols
        /// @return reusable symbol-order storage
        private char[] acquireSortedSymbols(int symbolCount) {
            if (sortedSymbols.length < symbolCount) {
                sortedSymbols = new char[symbolCount];
            }
            return sortedSymbols;
        }
    }

    /// Returns whether this tree contains only a single symbol.
    ///
    /// @return `true` for a degenerate one-symbol tree
    public boolean isSingleNode() {
        return singleNode;
    }

    /// Reads one symbol from the bitstream.
    ///
    /// @param bitReader the lossless bit reader
    /// @return the decoded symbol
    /// @throws WebPException if the bitstream is invalid
    public int readSymbol(LosslessBitReader bitReader) throws WebPException {
        if (singleNode) {
            return symbol;
        }

        int value = (int) bitReader.peekFull();
        int entry = primaryTable[value & tableMask];
        int length = entry >>> 12;
        if (length <= MAX_TABLE_BITS) {
            bitReader.consume(length);
            return entry & 0xFFF;
        }

        int mask = (1 << (length - MAX_TABLE_BITS)) - 1;
        int secondaryIndex = (entry & 0xFFF) + ((value >>> MAX_TABLE_BITS) & mask);
        int secondaryEntry = secondaryTable[secondaryIndex];
        bitReader.consume(secondaryEntry & 0xF);
        return secondaryEntry >>> 4;
    }

    /// Peeks at the next symbol if it can be resolved entirely from the primary table.
    ///
    /// @param bitReader the lossless bit reader
    /// @return the bit count in bits 12 and above plus the symbol in the low 12 bits, or `-1` if a
    ///         secondary table lookup would be required
    public int peekSymbol(LosslessBitReader bitReader) {
        if (singleNode) {
            return symbol;
        }
        int value = (int) bitReader.peekFull();
        int entry = primaryTable[value & tableMask];
        int length = entry >>> 12;
        if (length <= MAX_TABLE_BITS) {
            return entry;
        }
        return -1;
    }

    private static int nextCodeword(int codeword, int tableSize) {
        if (codeword == tableSize - 1) {
            return codeword;
        }
        int adv = 31 - Integer.numberOfLeadingZeros(codeword ^ (tableSize - 1));
        int bit = 1 << adv;
        codeword &= bit - 1;
        codeword |= bit;
        return codeword;
    }

    /// Computes the exact secondary-table size for the canonical long codes.
    ///
    /// @param histogram the number of symbols for each code length
    /// @param tableBits the primary lookup width
    /// @param maxLength the longest code length
    /// @param codeword the first codeword not represented directly by the primary table
    /// @param primaryTableMask the primary-table index mask
    /// @return the required number of secondary-table entries
    private static int secondaryTableSize(
            int[] histogram,
            int tableBits,
            int maxLength,
            int codeword,
            int primaryTableMask
    ) {
        int secondaryLength = 0;
        int subtableStart = 0;
        int subtablePrefix = -1;

        for (int length = tableBits + 1; length <= maxLength; length++) {
            int subtableSize = 1 << (length - tableBits);
            for (int i = 0; i < histogram[length]; i++) {
                if ((codeword & primaryTableMask) != subtablePrefix) {
                    subtablePrefix = codeword & primaryTableMask;
                    subtableStart = secondaryLength;
                    secondaryLength += subtableSize;
                }
                codeword = nextCodeword(codeword, 1 << length);
            }

            if (length < maxLength && (codeword & primaryTableMask) == subtablePrefix) {
                secondaryLength += secondaryLength - subtableStart;
            }
        }
        return secondaryLength;
    }

}
