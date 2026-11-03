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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;

/**
 * A Palm Database (PDB) file: a 78-byte header, a table of record offsets and the
 * records themselves. Mobipocket, Kindle AZW and KF8 (AZW3) books are all PDB files of
 * type {@code BOOK} and creator {@code MOBI}; the generations differ only in what the
 * records hold.
 */
final class PalmDatabase {

    static final int HEADER_SIZE = 78;
    private static final int TYPE_OFFSET = 60;
    private static final int RECORD_COUNT_OFFSET = 76;
    private static final int RECORD_ENTRY_SIZE = 8;

    private final byte[] file;
    private final int[] recordOffsets;

    /** A record's bytes in place: the file, where the record starts and how long it is. */
    record Slice(byte[] data, int offset, int length) {

        /** The same record without its last {@code bytes} bytes. */
        Slice trimmed(int bytes) {
            return new Slice(data, offset, Math.max(0, length - bytes));
        }
    }

    private PalmDatabase(byte[] file, int[] recordOffsets) {
        this.file = file;
        this.recordOffsets = recordOffsets;
    }

    static PalmDatabase read(byte[] file) throws TikaException {
        if (file.length < HEADER_SIZE) {
            throw new TikaException("Not a Palm Database: shorter than its header");
        }
        int count = EndianUtils.getUShortBE(file, RECORD_COUNT_OFFSET);
        if (count == 0 || HEADER_SIZE + count * RECORD_ENTRY_SIZE > file.length) {
            throw new TikaException("Not a Palm Database: " + count
                    + " records do not fit the file");
        }
        int[] offsets = new int[count];
        int previous = HEADER_SIZE + count * RECORD_ENTRY_SIZE;
        for (int i = 0; i < count; i++) {
            long offset = EndianUtils.getUIntBE(file, HEADER_SIZE + i * RECORD_ENTRY_SIZE);
            if (offset < previous || offset > file.length) {
                throw new TikaException("Not a Palm Database: record " + i
                        + " starts at " + offset + ", out of order or past the end");
            }
            offsets[i] = (int) offset;
            previous = (int) offset;
        }
        return new PalmDatabase(file, offsets);
    }

    /** The 8-byte type and creator, {@code BOOKMOBI} for a Mobipocket book. */
    String typeAndCreator() {
        return new String(file, TYPE_OFFSET, 8, StandardCharsets.ISO_8859_1);
    }

    int recordCount() {
        return recordOffsets.length;
    }

    int recordLength(int index) {
        int end = index + 1 < recordOffsets.length ? recordOffsets[index + 1] : file.length;
        return end - recordOffsets[index];
    }

    Slice slice(int index) {
        return new Slice(file, recordOffsets[index], recordLength(index));
    }

    byte[] record(int index) {
        byte[] record = new byte[recordLength(index)];
        System.arraycopy(file, recordOffsets[index], record, 0, record.length);
        return record;
    }

    boolean recordStartsWith(int index, byte[] magic) {
        return magic.length <= recordLength(index) && hasMagic(file, recordOffsets[index], magic);
    }

    /** Whether the bytes at the offset are the magic; false when the array ends first. */
    static boolean hasMagic(byte[] data, int at, byte[] magic) {
        if (at < 0 || at + magic.length > data.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[at + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    static void putUInt(byte[] data, int at, long value) {
        data[at] = (byte) (value >> 24);
        data[at + 1] = (byte) (value >> 16);
        data[at + 2] = (byte) (value >> 8);
        data[at + 3] = (byte) value;
    }

    /**
     * A new Palm Database with this one's header (name, type and creator) and the given
     * records, the way a reader application writes one: header, record table, two padding
     * bytes, records.
     */
    byte[] rebuild(List<byte[]> records) {
        int tableSize = records.size() * RECORD_ENTRY_SIZE;
        int size = HEADER_SIZE + tableSize + 2;
        for (byte[] record : records) {
            size += record.length;
        }
        byte[] out = new byte[size];
        System.arraycopy(file, 0, out, 0, HEADER_SIZE);
        out[RECORD_COUNT_OFFSET] = (byte) (records.size() >> 8);
        out[RECORD_COUNT_OFFSET + 1] = (byte) records.size();
        int offset = HEADER_SIZE + tableSize + 2;
        for (int i = 0; i < records.size(); i++) {
            int entry = HEADER_SIZE + i * RECORD_ENTRY_SIZE;
            putUInt(out, entry, offset);
            putUInt(out, entry + 4, 2L * i);
            System.arraycopy(records.get(i), 0, out, offset, records.get(i).length);
            offset += records.get(i).length;
        }
        return out;
    }
}
