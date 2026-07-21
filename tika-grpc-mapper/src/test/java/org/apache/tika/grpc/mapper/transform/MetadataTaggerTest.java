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
package org.apache.tika.grpc.mapper.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.MetadataValue;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;

/**
 * The tagging contract: every entry is a typed ARRAY (mirroring Tika's String[]-backed
 * Metadata), tagged by the declared Property element type. Keys with no declared type
 * are emitted as strings, never guessed. Anything the declared type cannot actually
 * parse falls back to strings rather than being dropped or mangled.
 */
class MetadataTaggerTest {

    @Test
    void declaredIntegerTagsAsInteger() {
        Metadata metadata = new Metadata();
        metadata.set(PagedText.N_PAGES, 7);

        MetadataValue value = MetadataTagger.tag(metadata, PagedText.N_PAGES.getName());
        assertEquals(MetadataValue.ValuesCase.INTEGERS, value.getValuesCase());
        assertEquals(List.of(7L), value.getIntegers().getValuesList());
    }

    /**
     * The multivalue case that motivated the array shape: {@code pdf:charsPerPage} is a
     * declared integer SEQUENCE -- one element per page. Every element must arrive as
     * an int64; collapsing a typed sequence into strings would throw the types away
     * exactly where they carry the most information.
     */
    @Test
    void declaredIntegerSequenceStaysTypedPerElement() {
        Metadata metadata = new Metadata();
        metadata.add(PDF.CHARACTERS_PER_PAGE, 1400);
        metadata.add(PDF.CHARACTERS_PER_PAGE, 1379);
        metadata.add(PDF.CHARACTERS_PER_PAGE, 42);

        MetadataValue value = MetadataTagger.tag(metadata, PDF.CHARACTERS_PER_PAGE.getName());
        assertEquals(MetadataValue.ValuesCase.INTEGERS, value.getValuesCase());
        assertEquals(List.of(1400L, 1379L, 42L), value.getIntegers().getValuesList());
    }

    /**
     * tiff:BitsPerSample is the canonical "8 8 8 8"-shaped metadata -- and because Tika
     * DECLARES it an integer sequence, it must come through as integers per element,
     * not as one mushed string.
     */
    @Test
    void bitsPerSampleArrivesAsIntegersBecauseTikaDeclaresIt() {
        Metadata metadata = new Metadata();
        metadata.add(TIFF.BITS_PER_SAMPLE, 8);
        metadata.add(TIFF.BITS_PER_SAMPLE, 8);
        metadata.add(TIFF.BITS_PER_SAMPLE, 8);

        MetadataValue value = MetadataTagger.tag(metadata, TIFF.BITS_PER_SAMPLE.getName());
        assertEquals(MetadataValue.ValuesCase.INTEGERS, value.getValuesCase());
        assertEquals(List.of(8L, 8L, 8L), value.getIntegers().getValuesList());
    }

    @Test
    void declaredBooleanTagsAsBoolean() {
        Metadata metadata = new Metadata();
        metadata.set(PDF.IS_ENCRYPTED, "true");

        MetadataValue value = MetadataTagger.tag(metadata, PDF.IS_ENCRYPTED.getName());
        assertEquals(MetadataValue.ValuesCase.BOOLEANS, value.getValuesCase());
        assertEquals(List.of(Boolean.TRUE), value.getBooleans().getValuesList());
    }

    @Test
    void booleanParsingAcceptsCommonSpellings() {
        assertTrue(MetadataTagger.parseBoolean("true"));
        assertTrue(MetadataTagger.parseBoolean("True"));
        assertTrue(MetadataTagger.parseBoolean("yes"));
        assertTrue(MetadataTagger.parseBoolean("1"));
        assertFalse(MetadataTagger.parseBoolean("false"));
        assertFalse(MetadataTagger.parseBoolean("no"));
        assertFalse(MetadataTagger.parseBoolean("0"));
    }

    @Test
    void declaredRealTagsAsNumber() {
        Metadata metadata = new Metadata();
        metadata.set(XMPDM.DURATION, "12.5");

        MetadataValue value = MetadataTagger.tag(metadata, XMPDM.DURATION.getName());
        assertEquals(MetadataValue.ValuesCase.NUMBERS, value.getValuesCase());
        assertEquals(List.of(12.5d), value.getNumbers().getValuesList());
    }

    @Test
    void declaredDateTagsAsTimestamp() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.CREATED, "2024-05-06T07:08:09Z");

        MetadataValue value = MetadataTagger.tag(metadata, TikaCoreProperties.CREATED.getName());
        assertEquals(MetadataValue.ValuesCase.TIMESTAMPS, value.getValuesCase());
        assertEquals(1, value.getTimestamps().getValuesCount());
        assertEquals(java.time.Instant.parse("2024-05-06T07:08:09Z").getEpochSecond(),
                value.getTimestamps().getValues(0).getSeconds());
    }

    /**
     * The canonical "never guess" case: a value that merely looks numeric-ish under an
     * UNDECLARED key must stay a string -- there is no declared element type to trust.
     */
    @Test
    void undeclaredKeyStaysStringNeverGuessed() {
        Metadata metadata = new Metadata();
        metadata.add("custom:bitsPerSample", "8 8 8 8");

        MetadataValue value = MetadataTagger.tag(metadata, "custom:bitsPerSample");
        assertEquals(MetadataValue.ValuesCase.STRINGS, value.getValuesCase());
        assertEquals(List.of("8 8 8 8"), value.getStrings().getValuesList());
    }

    /**
     * A declared-integer key whose actual values do not all parse must fall back to
     * strings for the WHOLE entry -- lossless beats typed when the two conflict, and a
     * half-typed array would silently drop the elements that failed.
     */
    @Test
    void malformedValueUnderDeclaredTypeFallsBackToStrings() {
        Metadata metadata = new Metadata();
        metadata.add(PagedText.N_PAGES.getName(), "7");
        metadata.add(PagedText.N_PAGES.getName(), "not-a-number");

        MetadataValue value = MetadataTagger.tag(metadata, PagedText.N_PAGES.getName());
        assertEquals(MetadataValue.ValuesCase.STRINGS, value.getValuesCase());
        assertEquals(List.of("7", "not-a-number"), value.getStrings().getValuesList());
    }

    @Test
    void multivalueTextCarriesEveryValue() {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.SUBJECT, "alpha");
        metadata.add(TikaCoreProperties.SUBJECT, "beta");

        MetadataValue value = MetadataTagger.tag(metadata, TikaCoreProperties.SUBJECT.getName());
        assertEquals(MetadataValue.ValuesCase.STRINGS, value.getValuesCase());
        assertEquals(List.of("alpha", "beta"), value.getStrings().getValuesList());
    }

    @Test
    void blankValuesProduceNoField() {
        Metadata metadata = new Metadata();
        metadata.add("custom:empty", "   ");

        assertNull(MetadataTagger.tag(metadata, "custom:empty"));

        Document.Builder document = Document.newBuilder();
        MetadataTagger.appendTail(metadata, new HashSet<>(), document);
        assertEquals(0, document.getExtraCount());
    }

    @Test
    void appendTailSkipsConsumedAndKeepsTheRest() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TITLE, "already typed elsewhere");
        metadata.add("custom:kept", "value");

        HashSet<String> consumed = new HashSet<>();
        consumed.add(TikaCoreProperties.TITLE.getName());

        Document.Builder document = Document.newBuilder();
        MetadataTagger.appendTail(metadata, consumed, document);

        assertEquals(1, document.getExtraCount());
        assertEquals("custom:kept", document.getExtra(0).getKey());
    }
}
