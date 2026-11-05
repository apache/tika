/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.mobi;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;

/**
 * The HUFF/CDIC text compression of Mobipocket (compression type 17480): a canonical Huffman
 * code over a dictionary of phrases. There is no published specification of the two
 * records; their layout is known from the open-source readers of the format and is
 * summarised here, and this decoder is written from that summary.
 * <p>
 * The {@code HUFF} record holds two tables. The first has 256 entries, one per value of
 * the first byte of a code: the code's length, whether that first byte settles the code,
 * and the base from which the dictionary index is counted down. The second has one entry
 * per code length from 1 to 32, the smallest code of that length and the index base, for
 * the codes a first byte does not settle. The {@code CDIC} records hold the phrases, each
 * flagged as literal or as a code stream itself, which is decoded the first time it is
 * used. A text record is a bit stream of codes, most significant bit first; a code is read
 * by taking the next 32 bits, looking up the first byte, and settling the length against
 * the second table where needed.
 * <p>
 * The tables and the dictionary come from the file, so everything they could make the
 * decoder allocate is bounded: the dictionary as a whole, a decoded phrase, and how deep
 * phrases may be defined in terms of other phrases.
 */
final class HuffCdic {

    private static final byte[] HUFF = {'H', 'U', 'F', 'F'};
    private static final byte[] CDIC = {'C', 'D', 'I', 'C'};
    private static final int HUFF_HEADER_SIZE = 24;
    private static final int CDIC_HEADER_SIZE = 16;
    private static final int FIRST_BYTE_VALUES = 256;
    private static final int MAX_CODE_LENGTH = 32;
    private static final int MAX_INDEX_BITS = 16;
    private static final int CODE_WINDOW_BITS = 32;
    /**
     * Literal and decoded phrase bytes together, plus {@link #PHRASE_OVERHEAD} per phrase so
     * that a table of empty phrases counts too; a real dictionary is a few hundred KB.
     */
    private static final int MAX_DICTIONARY_BYTES = 16 * 1024 * 1024;
    /** What an empty phrase costs the budget: its table entry and its object. */
    private static final int PHRASE_OVERHEAD = 64;
    /** A phrase that is a code stream decodes to at most this. */
    private static final int MAX_PHRASE_LENGTH = 64 * 1024;
    /** How deep a phrase may be defined in terms of other code-stream phrases. */
    static final int MAX_DEPTH = 32;

    private final int[] lengthByFirstByte = new int[FIRST_BYTE_VALUES];
    private final boolean[] settledByFirstByte = new boolean[FIRST_BYTE_VALUES];
    private final long[] baseByFirstByte = new long[FIRST_BYTE_VALUES];
    private final long[] smallestCodeByLength = new long[MAX_CODE_LENGTH + 1];
    private final long[] baseByLength = new long[MAX_CODE_LENGTH + 1];
    private final List<Phrase> phrases = new ArrayList<>();
    private long dictionaryBytes;

    /** A dictionary entry: literal bytes, or a code stream until it is decoded once. */
    private static final class Phrase {
        byte[] bytes;
        boolean literal;
        boolean decoding;

        Phrase(byte[] bytes, boolean literal) {
            this.bytes = bytes;
            this.literal = literal;
        }
    }

    HuffCdic(byte[] huff, List<byte[]> cdics) throws TikaException {
        readTables(huff);
        for (byte[] cdic : cdics) {
            readPhrases(cdic);
        }
    }

    /**
     * Reads the HUFF record and the CDIC records that follow it, {@code count} records in all
     * as the MOBI header counts them.
     */
    static HuffCdic read(PalmDatabase pdb, long huffIndex, long count) throws TikaException {
        if (huffIndex <= 0 || count < 2 || huffIndex + count > pdb.recordCount()) {
            throw new TikaException("The MOBI header names no usable HUFF/CDIC records");
        }
        List<byte[]> cdics = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            cdics.add(pdb.record((int) (huffIndex + i)));
        }
        return new HuffCdic(pdb.record((int) huffIndex), cdics);
    }

    private void readTables(byte[] huff) throws TikaException {
        if (huff.length < HUFF_HEADER_SIZE || !PalmDatabase.hasMagic(huff, 0, HUFF)) {
            throw new TikaException("The HUFF record has no HUFF header");
        }
        long firstByteTable = EndianUtils.getUIntBE(huff, 8);
        long lengthTable = EndianUtils.getUIntBE(huff, 12);
        if (firstByteTable + FIRST_BYTE_VALUES * 4L > huff.length
                || lengthTable + MAX_CODE_LENGTH * 8L > huff.length) {
            throw new TikaException("The HUFF record is shorter than its tables");
        }
        for (int first = 0; first < FIRST_BYTE_VALUES; first++) {
            long entry = EndianUtils.getUIntBE(huff, (int) firstByteTable + first * 4);
            int length = (int) (entry & 0x1f);
            boolean settled = (entry & 0x80) != 0;
            if (length == 0 || (length <= 8 && !settled)) {
                throw new TikaException("The HUFF table has an invalid entry for first byte "
                        + first);
            }
            lengthByFirstByte[first] = length;
            settledByFirstByte[first] = settled;
            baseByFirstByte[first] = indexBase(entry >>> 8, length);
        }
        for (int length = 1; length <= MAX_CODE_LENGTH; length++) {
            int at = (int) lengthTable + (length - 1) * 8;
            smallestCodeByLength[length] = EndianUtils.getUIntBE(huff, at) << (CODE_WINDOW_BITS - length);
            baseByLength[length] = indexBase(EndianUtils.getUIntBE(huff, at + 4), length);
        }
    }

    /**
     * The table stores the base as a code of the given length; the decoder compares it
     * with a 32-bit window, so it is widened to one, with every bit below the code set.
     */
    private static long indexBase(long base, int length) {
        return ((base + 1) << (CODE_WINDOW_BITS - length)) - 1;
    }

    private void readPhrases(byte[] cdic) throws TikaException {
        if (cdic.length < CDIC_HEADER_SIZE || !PalmDatabase.hasMagic(cdic, 0, CDIC)) {
            throw new TikaException("A CDIC record has no CDIC header");
        }
        long total = EndianUtils.getUIntBE(cdic, 8);
        long bits = EndianUtils.getUIntBE(cdic, 12);
        if (bits > MAX_INDEX_BITS) {
            throw new TikaException("A CDIC record claims " + bits + " index bits");
        }
        long count = Math.min(1L << bits, total - phrases.size());
        for (int i = 0; i < count; i++) {
            int offsetAt = CDIC_HEADER_SIZE + i * 2;
            if (offsetAt + 2 > cdic.length) {
                throw new TikaException("A CDIC record ends inside its offset table");
            }
            int start = CDIC_HEADER_SIZE + EndianUtils.getUShortBE(cdic, offsetAt);
            if (start + 2 > cdic.length) {
                throw new TikaException("A CDIC phrase starts past the record");
            }
            int lengthAndFlag = EndianUtils.getUShortBE(cdic, start);
            int length = lengthAndFlag & 0x7fff;
            if (start + 2 + length > cdic.length) {
                throw new TikaException("A CDIC phrase ends past the record");
            }
            charge(PHRASE_OVERHEAD + length);
            phrases.add(new Phrase(Arrays.copyOfRange(cdic, start + 2, start + 2 + length),
                    (lengthAndFlag & 0x8000) != 0));
        }
    }

    private void charge(int bytes) throws TikaException {
        dictionaryBytes += bytes;
        if (dictionaryBytes > MAX_DICTIONARY_BYTES) {
            throw new TikaException("The CDIC dictionary is larger than " + MAX_DICTIONARY_BYTES
                    + " bytes");
        }
    }

    /**
     * Decodes one text record, at most {@code maxLength} bytes of it; a record holds no
     * more text than the header's record size, so the cut is where the text ends anyway.
     */
    byte[] decode(PalmDatabase.Slice record, int maxLength) throws TikaException {
        return decode(record, maxLength, 0);
    }

    /**
     * A text record (depth 0) is cut at {@code maxLength}, since it holds no more text than
     * the header's record size anyway; a phrase (deeper) that reaches it is refused.
     */
    private byte[] decode(PalmDatabase.Slice stream, int maxLength, int depth)
            throws TikaException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxLength, 8192));
        long totalBits = stream.length() * 8L;
        long bitPosition = 0;
        while (true) {
            long window = next32Bits(stream, bitPosition);
            int first = (int) (window >>> (CODE_WINDOW_BITS - 8));
            int codeLength = lengthByFirstByte[first];
            long base = baseByFirstByte[first];
            if (!settledByFirstByte[first]) {
                while (codeLength <= MAX_CODE_LENGTH && window < smallestCodeByLength[codeLength]) {
                    codeLength++;
                }
                if (codeLength > MAX_CODE_LENGTH) {
                    throw new TikaException("A HUFF code matches no code length");
                }
                base = baseByLength[codeLength];
            }
            bitPosition += codeLength;
            if (bitPosition > totalBits) {
                // the rest is the padding of the last byte
                break;
            }
            long index = (base - window) >> (CODE_WINDOW_BITS - codeLength);
            if (index < 0 || index >= phrases.size()) {
                throw new TikaException("A HUFF code points outside the CDIC dictionary");
            }
            byte[] phrase = phrase((int) index, depth);
            if (out.size() + phrase.length > maxLength) {
                if (depth > 0) {
                    throw new TikaException("A CDIC phrase decodes to more than " + maxLength
                            + " bytes");
                }
                out.write(phrase, 0, maxLength - out.size());
                break;
            }
            out.write(phrase, 0, phrase.length);
        }
        return out.toByteArray();
    }

    /**
     * The 32 bits of the stream from the bit position on, most significant first, zero
     * past the end of the stream.
     */
    private static long next32Bits(PalmDatabase.Slice stream, long bitPosition) {
        int byteIndex = (int) (bitPosition >>> 3);
        int bitInByte = (int) (bitPosition & 7);
        long bits = 0;
        for (int i = 0; i < 5; i++) {
            int b = byteIndex + i < stream.length()
                    ? stream.data()[stream.offset() + byteIndex + i] & 0xff : 0;
            bits = (bits << 8) | b;
        }
        return (bits >>> (8 - bitInByte)) & 0xffffffffL;
    }

    /** The phrase's bytes, decoded first if it is a code stream itself. */
    private byte[] phrase(int index, int depth) throws TikaException {
        Phrase phrase = phrases.get(index);
        if (!phrase.literal) {
            if (phrase.decoding) {
                throw new TikaException("A CDIC phrase is defined in terms of itself");
            }
            if (depth >= MAX_DEPTH) {
                throw new TikaException("CDIC phrases are nested deeper than " + MAX_DEPTH);
            }
            phrase.decoding = true;
            byte[] decoded = decode(new PalmDatabase.Slice(phrase.bytes, 0, phrase.bytes.length),
                    MAX_PHRASE_LENGTH, depth + 1);
            charge(decoded.length - phrase.bytes.length);
            phrase.bytes = decoded;
            phrase.literal = true;
            phrase.decoding = false;
        }
        return phrase.bytes;
    }
}
