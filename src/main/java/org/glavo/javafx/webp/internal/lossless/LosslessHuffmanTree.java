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
package org.glavo.javafx.webp.internal.lossless;

import org.glavo.javafx.webp.WebPException;

import java.util.Arrays;

/// Huffman tree implementation for VP8L.
public final class LosslessHuffmanTree {

    private static final int MAX_ALLOWED_CODE_LENGTH = 15;
    private static final int MAX_TABLE_BITS = 10;

    private final boolean singleNode;
    private final int symbol;
    private final int tableMask;
    private final int[] primaryTable;
    private final int[] secondaryTable;

    private LosslessHuffmanTree(int symbol) {
        this.singleNode = true;
        this.symbol = symbol;
        this.tableMask = 0;
        this.primaryTable = null;
        this.secondaryTable = null;
    }

    private LosslessHuffmanTree(int tableMask, int[] primaryTable, int[] secondaryTable) {
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
        return new LosslessHuffmanTree(0x1, new int[]{(1 << 12) | zero, (1 << 12) | one}, new int[0]);
    }

    /// Builds a canonical Huffman tree from code lengths.
    ///
    /// @param codeLengths the code lengths indexed by symbol
    /// @return the resulting tree
    /// @throws WebPException if the code lengths do not form a valid canonical tree
    public static LosslessHuffmanTree implicit(int[] codeLengths) throws WebPException {
        int[] histogram = new int[MAX_ALLOWED_CODE_LENGTH + 1];
        int symbolCount = 0;
        for (int length : codeLengths) {
            if (length < 0 || length > MAX_ALLOWED_CODE_LENGTH) {
                throw new WebPException("Invalid Huffman code length");
            }
            histogram[length]++;
            if (length != 0) {
                symbolCount++;
            }
        }

        if (symbolCount == 0) {
            throw new WebPException("Invalid Huffman code");
        }
        if (symbolCount == 1) {
            for (int symbol = 0; symbol < codeLengths.length; symbol++) {
                if (codeLengths[symbol] != 0) {
                    return single(symbol);
                }
            }
        }

        int maxLength = MAX_ALLOWED_CODE_LENGTH;
        while (maxLength > 1 && histogram[maxLength] == 0) {
            maxLength--;
        }

        int[] offsets = new int[16];
        int codeSpaceUsed = 0;
        offsets[1] = histogram[0];
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
        int[] primaryTable = new int[tableSize];
        int[] sortedSymbols = new int[codeLengths.length];
        int[] nextIndex = Arrays.copyOf(offsets, offsets.length);
        for (int symbol = 0; symbol < codeLengths.length; symbol++) {
            int length = codeLengths[symbol];
            sortedSymbols[nextIndex[length]] = symbol;
            nextIndex[length]++;
        }

        int codeword = 0;
        int i = histogram[0];
        int primaryTableMask = tableSize - 1;
        for (int length = 1; length <= tableBits; length++) {
            int currentTableEnd = 1 << length;
            for (int j = 0; j < histogram[length]; j++) {
                int symbol = sortedSymbols[i++];
                primaryTable[codeword] = (length << 12) | symbol;
                codeword = nextCodeword(codeword, currentTableEnd);
            }

            if (length < tableBits) {
                System.arraycopy(primaryTable, 0, primaryTable, currentTableEnd, currentTableEnd);
            }
        }

        int[] secondaryTable = new int[0];
        int secondaryLength = 0;
        if (maxLength > tableBits) {
            int subtableStart = 0;
            int subtablePrefix = -1;
            secondaryTable = new int[4096];

            for (int length = tableBits + 1; length <= maxLength; length++) {
                int subtableSize = 1 << (length - tableBits);
                for (int j = 0; j < histogram[length]; j++) {
                    if ((codeword & primaryTableMask) != subtablePrefix) {
                        subtablePrefix = codeword & primaryTableMask;
                        subtableStart = secondaryLength;
                        primaryTable[subtablePrefix] = (length << 12) | subtableStart;
                        secondaryLength += subtableSize;
                    }

                    int symbol = sortedSymbols[i++];
                    secondaryTable[subtableStart + (codeword >> tableBits)] = (symbol << 4) | length;
                    codeword = nextCodeword(codeword, 1 << length);
                }

                if (length < maxLength && (codeword & primaryTableMask) == subtablePrefix) {
                    int copyLength = secondaryLength - subtableStart;
                    System.arraycopy(secondaryTable, subtableStart, secondaryTable, secondaryLength, copyLength);
                    primaryTable[subtablePrefix] = ((length + 1) << 12) | subtableStart;
                    secondaryLength += copyLength;
                }
            }
            secondaryTable = Arrays.copyOf(secondaryTable, secondaryLength);
        }

        return new LosslessHuffmanTree(primaryTableMask, primaryTable, secondaryTable);
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
    /// @return the peeked symbol, or `null` if a secondary table lookup would be required
    public PeekedSymbol peekSymbol(LosslessBitReader bitReader) {
        if (singleNode) {
            return new PeekedSymbol(0, symbol);
        }
        int value = (int) bitReader.peekFull();
        int entry = primaryTable[value & tableMask];
        int length = entry >>> 12;
        if (length <= MAX_TABLE_BITS) {
            return new PeekedSymbol(length, entry & 0xFFF);
        }
        return null;
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

    /// Primary-table peek result.
    ///
    /// @param bits the number of bits that would be consumed
    /// @param symbol the decoded symbol value
    public record PeekedSymbol(int bits, int symbol) {
    }
}
