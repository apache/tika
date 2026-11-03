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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;

/**
 * Record 0 of a Mobipocket book: the 16-byte PalmDOC header, the {@code MOBI} header
 * and the EXTH metadata records. Offsets are relative to the start of the record, as
 * the format counts them. The KF8 generation (file version 8) has a longer header and
 * the same EXTH, so one reader serves both.
 */
final class MobiHeader {

    /** A header value with a numeric code in the file and the label the metadata reports. */
    interface Coded {
        int code();

        String label();
    }

    /** How the text records are compressed. */
    enum Compression implements Coded {
        NONE(1, "none"), PALMDOC(2, "palmdoc"), HUFF_CDIC(17480, "huffcdic"), UNKNOWN(-1, "unknown");

        private final int code;
        private final String label;

        Compression(int code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** Whether and how the text is encrypted. */
    enum Encryption implements Coded {
        NONE(0, "none"), OLD_MOBIPOCKET(1, "old-mobipocket"), MOBIPOCKET(2, "mobipocket"),
        UNKNOWN(-1, "unknown");

        private final int code;
        private final String label;

        Encryption(int code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** The value with the code, else {@code unknown}. */
    private static <T extends Coded> T byCode(T[] values, int code, T unknown) {
        for (T value : values) {
            if (value.code() == code) {
                return value;
            }
        }
        return unknown;
    }

    // EXTH record types, see the MobileRead wiki's MOBI page
    static final int EXTH_CREATOR = 100;
    static final int EXTH_PUBLISHER = 101;
    static final int EXTH_DESCRIPTION = 103;
    static final int EXTH_ISBN = 104;
    static final int EXTH_SUBJECT = 105;
    static final int EXTH_PUBLISHING_DATE = 106;
    static final int EXTH_CONTRIBUTOR = 108;
    static final int EXTH_RIGHTS = 109;
    static final int EXTH_ASIN = 113;
    static final int EXTH_KF8_BOUNDARY = 121;
    static final int EXTH_COVER_OFFSET = 201;
    static final int EXTH_THUMBNAIL_OFFSET = 202;
    static final int EXTH_CDE_TYPE = 501;
    static final int EXTH_UPDATED_TITLE = 503;
    static final int EXTH_ASIN_2 = 504;
    static final int EXTH_LANGUAGE = 524;

    // MOBI header fields, offsets into record 0
    private static final int HEADER_LENGTH_FIELD = 20;
    private static final int CODEPAGE_FIELD = 28;
    private static final int FILE_VERSION_FIELD = 36;
    private static final int FIRST_NON_BOOK_FIELD = 80;
    private static final int FULL_NAME_OFFSET_FIELD = 84;
    private static final int FULL_NAME_LENGTH_FIELD = 88;
    private static final int LOCALE_FIELD = 92;
    private static final int FIRST_IMAGE_FIELD = 108;
    private static final int HUFF_RECORD_FIELD = 112;
    private static final int HUFF_COUNT_FIELD = 116;
    private static final int EXTH_FLAGS_FIELD = 128;
    private static final int FDST_FIELD = 192;
    private static final int FCIS_FIELD = 200;
    private static final int FLIS_FIELD = 208;
    private static final int TRAILING_FLAGS_FIELD = 242;
    private static final int NCX_FIELD = 244;
    private static final int FRAGMENT_FIELD = 248;
    private static final int SKELETON_FIELD = 252;
    private static final int DATP_FIELD = 256;
    private static final int GUIDE_FIELD = 260;

    /** The fields of a KF8 header that hold record indices counted from the file start. */
    private static final int[] KF8_RECORD_INDEX_FIELDS = {FIRST_NON_BOOK_FIELD, HUFF_RECORD_FIELD,
            FDST_FIELD, FCIS_FIELD, FLIS_FIELD, NCX_FIELD, FRAGMENT_FIELD, SKELETON_FIELD,
            DATP_FIELD, GUIDE_FIELD};

    /** The value writers put into a record index field that points nowhere. */
    static final long NULL_INDEX = 0xffffffffL;

    private static final int MIN_RECORD_0 = 16 + 24;
    private static final int MOBI_OFFSET = 16;
    private static final byte[] MOBI = {'M', 'O', 'B', 'I'};
    private static final byte[] EXTH = {'E', 'X', 'T', 'H'};
    private static final int EXTH_FLAG = 0x40;
    /** The trailing-entry flags are at 0xF2 once the header is at least this long. */
    private static final int HEADER_WITH_TRAILING_FLAGS = 0xE4;
    private static final int CODEPAGE_CP1252 = 1252;
    private static final int CODEPAGE_UTF8 = 65001;

    /**
     * The Windows primary language id in the header's locale, to ISO 639-1. The region in
     * the high byte is left out; only the language is of interest, and only when EXTH 524
     * does not name it.
     */
    private static final Map<Integer, String> LANGUAGES = Map.ofEntries(
            Map.entry(0x01, "ar"), Map.entry(0x02, "bg"), Map.entry(0x03, "ca"),
            Map.entry(0x04, "zh"), Map.entry(0x05, "cs"), Map.entry(0x06, "da"),
            Map.entry(0x07, "de"), Map.entry(0x08, "el"), Map.entry(0x09, "en"),
            Map.entry(0x0a, "es"), Map.entry(0x0b, "fi"), Map.entry(0x0c, "fr"),
            Map.entry(0x0d, "he"), Map.entry(0x0e, "hu"), Map.entry(0x0f, "is"),
            Map.entry(0x10, "it"), Map.entry(0x11, "ja"), Map.entry(0x12, "ko"),
            Map.entry(0x13, "nl"), Map.entry(0x14, "no"), Map.entry(0x15, "pl"),
            Map.entry(0x16, "pt"), Map.entry(0x17, "rm"), Map.entry(0x18, "ro"),
            Map.entry(0x19, "ru"), Map.entry(0x1a, "hr"), Map.entry(0x1b, "sk"),
            Map.entry(0x1c, "sq"), Map.entry(0x1d, "sv"), Map.entry(0x1e, "th"),
            Map.entry(0x1f, "tr"), Map.entry(0x20, "ur"), Map.entry(0x21, "id"),
            Map.entry(0x22, "uk"), Map.entry(0x23, "be"), Map.entry(0x24, "sl"),
            Map.entry(0x25, "et"), Map.entry(0x26, "lv"), Map.entry(0x27, "lt"),
            Map.entry(0x29, "fa"), Map.entry(0x2a, "vi"), Map.entry(0x2b, "hy"),
            Map.entry(0x2d, "eu"), Map.entry(0x2f, "mk"), Map.entry(0x36, "af"),
            Map.entry(0x37, "ka"), Map.entry(0x39, "hi"), Map.entry(0x3e, "ms"),
            Map.entry(0x45, "bn"), Map.entry(0x4e, "mr"), Map.entry(0x4a, "te"),
            Map.entry(0x49, "ta"), Map.entry(0x47, "gu"), Map.entry(0x4b, "kn"),
            Map.entry(0x4c, "ml"), Map.entry(0x52, "cy"), Map.entry(0x56, "gl"));

    private final Compression compression;
    private final long textLength;
    private final int textRecordCount;
    private final int textRecordSize;
    private final Encryption encryption;
    private final int headerLength;
    private final int codepage;
    private final int fileVersion;
    private final long locale;
    private final long firstImageIndex;
    private final long huffRecordIndex;
    private final long huffRecordCount;
    private final int trailingEntryFlags;
    private final String fullName;
    private final List<ExthRecord> exth;
    private final byte[] record0;
    private final Charset charset;

    /** One EXTH record: its numeric type and the raw bytes. */
    record ExthRecord(int type, byte[] data) {

        String asString(Charset charset) {
            return new String(data, charset).trim();
        }

        long asNumber() {
            if (data.length == 4) {
                return EndianUtils.getUIntBE(data, 0);
            }
            return -1;
        }
    }

    private MobiHeader(byte[] r) throws TikaException {
        record0 = r;
        int length = r.length;
        compression = byCode(Compression.values(), EndianUtils.getUShortBE(r, 0),
                Compression.UNKNOWN);
        textLength = EndianUtils.getUIntBE(r, 4);
        textRecordCount = EndianUtils.getUShortBE(r, 8);
        int recordSize = EndianUtils.getUShortBE(r, 10);
        textRecordSize = recordSize == 0 ? 4096 : recordSize;
        encryption = byCode(Encryption.values(), EndianUtils.getUShortBE(r, 12),
                Encryption.UNKNOWN);
        if (!PalmDatabase.hasMagic(r, MOBI_OFFSET, MOBI)) {
            throw new TikaException("Not a Mobipocket book: record 0 has no MOBI header");
        }
        long declaredLength = EndianUtils.getUIntBE(r, HEADER_LENGTH_FIELD);
        if (declaredLength < 24 || MOBI_OFFSET + declaredLength > length) {
            throw new TikaException("Not a Mobipocket book: MOBI header length "
                    + declaredLength + " does not fit record 0");
        }
        headerLength = (int) declaredLength;
        codepage = (int) field(r, CODEPAGE_FIELD);
        charset = charsetOf(codepage);
        fileVersion = (int) field(r, FILE_VERSION_FIELD);
        long nameOffset = field(r, FULL_NAME_OFFSET_FIELD);
        long nameLength = field(r, FULL_NAME_LENGTH_FIELD);
        locale = field(r, LOCALE_FIELD);
        firstImageIndex = field(r, FIRST_IMAGE_FIELD);
        huffRecordIndex = field(r, HUFF_RECORD_FIELD);
        huffRecordCount = field(r, HUFF_COUNT_FIELD);
        long exthFlags = field(r, EXTH_FLAGS_FIELD);
        // the readers the format is known from take the flags only from version 5 on
        trailingEntryFlags = headerLength >= HEADER_WITH_TRAILING_FLAGS && fileVersion >= 5
                ? EndianUtils.getUShortBE(r, TRAILING_FLAGS_FIELD) : 0;
        fullName = nameLength > 0 && nameOffset + nameLength <= length
                ? new String(r, (int) nameOffset, (int) nameLength, charset()).trim() : "";
        exth = (exthFlags & EXTH_FLAG) != 0 ? readExth(r, MOBI_OFFSET + headerLength)
                : List.of();
    }

    /** A 4-byte field of the MOBI header, 0 when the header ends before it. */
    private long field(byte[] r, int offset) {
        return hasField(offset) ? EndianUtils.getUIntBE(r, offset) : 0;
    }

    private boolean hasField(int offset) {
        return offset + 4 <= MOBI_OFFSET + headerLength;
    }

    /**
     * A copy of this KF8 header's record 0 as the first record of a file of its own: the
     * record index fields count from itself, the first image index is cleared so the images
     * it shares with the first half of a hybrid are not emitted twice. {@code base} is the
     * record's index in the file it came from.
     */
    byte[] rebasedRecord0(int base) {
        byte[] copy = record0.clone();
        if (hasField(FIRST_IMAGE_FIELD)) {
            PalmDatabase.putUInt(copy, FIRST_IMAGE_FIELD, 0);
        }
        for (int offset : KF8_RECORD_INDEX_FIELDS) {
            if (!hasField(offset)) {
                continue;
            }
            long value = EndianUtils.getUIntBE(copy, offset);
            if (value == 0 || value == NULL_INDEX) {
                continue;
            }
            PalmDatabase.putUInt(copy, offset, value >= base ? value - base : NULL_INDEX);
        }
        return copy;
    }

    private static List<ExthRecord> readExth(byte[] r, int offset) {
        List<ExthRecord> records = new ArrayList<>();
        if (offset + 12 > r.length || !PalmDatabase.hasMagic(r, offset, EXTH)) {
            return records;
        }
        long count = EndianUtils.getUIntBE(r, offset + 8);
        int pos = offset + 12;
        for (long i = 0; i < count && pos + 8 <= r.length; i++) {
            long type = EndianUtils.getUIntBE(r, pos);
            long recordLength = EndianUtils.getUIntBE(r, pos + 4);
            if (recordLength < 8 || pos + recordLength > r.length) {
                break;
            }
            byte[] data = new byte[(int) recordLength - 8];
            System.arraycopy(r, pos + 8, data, 0, data.length);
            records.add(new ExthRecord((int) type, data));
            pos += (int) recordLength;
        }
        return records;
    }

    static MobiHeader read(PalmDatabase pdb) throws TikaException {
        return read(pdb.record(0));
    }

    static MobiHeader read(byte[] record0) throws TikaException {
        if (record0.length < MIN_RECORD_0) {
            throw new TikaException("Not a Mobipocket book: record 0 is too short");
        }
        return new MobiHeader(record0);
    }

    Compression compression() {
        return compression;
    }

    long textLength() {
        return textLength;
    }

    int textRecordCount() {
        return textRecordCount;
    }

    int textRecordSize() {
        return textRecordSize;
    }

    Encryption encryption() {
        return encryption;
    }

    int headerLength() {
        return headerLength;
    }

    int codepage() {
        return codepage;
    }

    int fileVersion() {
        return fileVersion;
    }

    long locale() {
        return locale;
    }

    long firstImageIndex() {
        return firstImageIndex;
    }

    long huffRecordIndex() {
        return huffRecordIndex;
    }

    long huffRecordCount() {
        return huffRecordCount;
    }

    int trailingEntryFlags() {
        return trailingEntryFlags;
    }

    String fullName() {
        return fullName;
    }

    List<ExthRecord> exth() {
        return exth;
    }

    Charset charset() {
        return charset;
    }

    private static Charset charsetOf(int codepage) {
        if (codepage == CODEPAGE_UTF8 || codepage == 0) {
            return StandardCharsets.UTF_8;
        }
        if (codepage == CODEPAGE_CP1252) {
            return Charset.forName("windows-1252");
        }
        try {
            return Charset.forName("windows-" + codepage);
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }

    boolean isKf8() {
        return fileVersion >= 8;
    }

    /** EXTH 524, else the language of the header's locale, else null. */
    String language() {
        String language = exthString(EXTH_LANGUAGE);
        return language != null ? language : languageOf(locale);
    }

    static String languageOf(long locale) {
        return LANGUAGES.get((int) (locale & 0xff));
    }

    List<String> exthStrings(int type) {
        List<String> values = new ArrayList<>();
        for (ExthRecord record : exth) {
            if (record.type() == type) {
                String value = record.asString(charset());
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    String exthString(int type) {
        List<String> values = exthStrings(type);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * The record index in the first EXTH record of the type, -1 when there is none or it
     * holds {@link #NULL_INDEX}, which writers use for "no such record".
     */
    long exthNumber(int type) {
        for (ExthRecord record : exth) {
            if (record.type() == type) {
                long value = record.asNumber();
                return value == NULL_INDEX ? -1 : value;
            }
        }
        return -1;
    }
}
