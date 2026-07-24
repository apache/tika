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
package org.apache.tika.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.MetadataValue;
import org.apache.tika.grpc.v1.ParseStatus;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Coverage of {@link DocumentBuilder}: the envelope, typed Dublin Core metadata (via
 * {@link org.apache.tika.grpc.mapper.transform.DocumentTransformers}), the tagged tail,
 * and status mapping.
 */
class DocumentBuilderTest extends ParseFixtureSupport {

    @Test
    void buildsTypedMetadataFromRealPdf() throws Exception {
        Document document = map(parseFixture("testPDF.pdf"), "doc-1", 42L);

        assertEquals("application/pdf", document.getContentType());
        assertEquals("Apache Tika - Apache Tika", document.getMetadata().getTitle());
        assertTrue(document.getExtraCount() > 0);

        assertEquals(ParseStatus.Status.SUCCESS, document.getStatus().getStatus());
        assertEquals("PARSE_SUCCESS", document.getStatus().getPipesStatus());
        assertEquals(42L, document.getStatus().getFetchParseTimeMs());
        assertFalse(document.getStatus().getParsersUsedList().isEmpty());
    }

    @Test
    void mapsSourceDigestWhenThePipelineRecordedOne() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        // Reserved X-TIKA keys only accept Property writes (TIKA-4769); this mirrors how
        // InputStreamDigester records the digest.
        metadata.set(Property.internalText(TikaCoreProperties.TIKA_META_PREFIX + "digest"
                + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "SHA256"), "0a1b2c3d");

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);
        assertEquals("0a1b2c3d", document.getOrigin().getSha256());

        Metadata undigested = new Metadata();
        undigested.set(Metadata.CONTENT_TYPE, "text/plain");
        Document withoutDigest = DocumentBuilder.build(undigested, "d", "PARSE_SUCCESS", 1L);
        assertTrue(withoutDigest.getOrigin().getSha256().isEmpty());
    }

    @Test
    void mapsDublinCorePublishersAndIdentifiers() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        metadata.add(TikaCoreProperties.PUBLISHER, "Apache Software Foundation");
        metadata.add(TikaCoreProperties.IDENTIFIER, "urn:isbn:9780000000000");

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);

        assertEquals(List.of("Apache Software Foundation"),
                document.getMetadata().getPublishersList());
        assertEquals(List.of("urn:isbn:9780000000000"),
                document.getMetadata().getIdentifiersList());
        // consumed into the typed fields, so the keys must not duplicate into the tail
        assertTrue(document.getExtraList().stream()
                .noneMatch(f -> f.getKey().equals(TikaCoreProperties.PUBLISHER.getName())
                        || f.getKey().equals(TikaCoreProperties.IDENTIFIER.getName())));
    }

    /**
     * Keys the envelope already mapped (Content-Type, resource name) and the flat
     * content body must not ride along in the tagged tail a second time: the tail is
     * for metadata that has no typed home, not a duplicate channel.
     */
    @Test
    void envelopeKeysAndContentAreNotDuplicatedIntoTheTail() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "sample.txt");
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "the whole extracted body");

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);

        assertEquals("text/plain", document.getContentType());
        assertEquals("sample.txt", document.getOrigin().getFilename());
        assertTrue(document.getExtraList().stream()
                .noneMatch(f -> f.getKey().equals(Metadata.CONTENT_TYPE)
                        || f.getKey().equals(TikaCoreProperties.RESOURCE_NAME_KEY.getName())
                        || f.getKey().equals(TikaCoreProperties.TIKA_CONTENT.getName())));
    }

    /**
     * The tagged tail preserves Tika's declared Property types over the contract:
     * {@code xmpTPg:NPages} is declared an integer, so it must arrive typed, not as the
     * string "7".
     */
    @Test
    void taggedTailPreservesDeclaredTypes() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        metadata.set(PagedText.N_PAGES, 7);

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);

        MetadataValue pages = document.getExtraList().stream()
                .filter(f -> f.getKey().equals(PagedText.N_PAGES.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();
        assertEquals(MetadataValue.ValuesCase.INTEGERS, pages.getValuesCase());
        assertEquals(List.of(7L), pages.getIntegers().getValuesList());
    }

    @Test
    void mapsContentLengthToByteSize() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(Metadata.CONTENT_LENGTH, "12345");

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);
        assertEquals(12345L, document.getOrigin().getByteSize());
        assertTrue(document.getExtraList().stream()
                .noneMatch(f -> f.getKey().equals(Metadata.CONTENT_LENGTH)));
    }

    /**
     * A Content-Length that does not parse must not be guessed or dropped: byte_size
     * stays unset and the raw value survives in the tagged tail.
     */
    @Test
    void malformedContentLengthFallsThroughToTheTail() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(Metadata.CONTENT_LENGTH, "not-a-size");

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);
        assertEquals(0L, document.getOrigin().getByteSize());
        assertTrue(document.getExtraList().stream()
                .anyMatch(f -> f.getKey().equals(Metadata.CONTENT_LENGTH)));
    }

    /**
     * parsers_used prefers the full set (X-TIKA:Parsed-By-Full-Set) but must not come
     * back empty just because only X-TIKA:Parsed-By was recorded.
     */
    @Test
    void parsersUsedFallsBackWhenFullSetIsAbsent() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "org.example.OuterParser");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "org.example.InnerParser");

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);
        assertEquals(List.of("org.example.OuterParser", "org.example.InnerParser"),
                document.getStatus().getParsersUsedList());
        assertEquals("org.example.OuterParser", document.getOrigin().getParser());
        // parsed-by keys have a typed home now; they must not duplicate into the tail
        assertTrue(document.getExtraList().stream()
                .noneMatch(f -> f.getKey().equals(TikaCoreProperties.TIKA_PARSED_BY.getName())));
    }

    @Test
    void missingDocIdAndPipesStatusAreOmittedNotInvented() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        Document document = DocumentBuilder.build(metadata, null, null, 1L);
        assertEquals("", document.getId());
        assertEquals(ParseStatus.Status.UNSPECIFIED, document.getStatus().getStatus());
        assertEquals("", document.getStatus().getPipesStatus());
    }

    @Test
    void recordsProducerVersion() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        Document document = DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L);
        assertTrue(document.getStatus().getTikaVersion().startsWith("Apache Tika"));

        // Provenance is recorded even when the parse produced no metadata at all.
        Document failed = DocumentBuilder.build(null, "d", "FETCH_EXCEPTION", 1L);
        assertTrue(failed.getStatus().getTikaVersion().startsWith("Apache Tika"));
    }

    /**
     * {@code primary == null} is the server's signal that the pipes result carried no
     * metadata at all (see TikaGrpcServerImpl). On a failure status that surfaces as an
     * explicit error; on {@code EMPTY_OUTPUT} -- a success with nothing to map -- it must
     * NOT be reported as a failure.
     */
    @Test
    void reportsFailureWhenNoMetadata() {
        Document document = DocumentBuilder.build(null, "doc-1", "FETCH_EXCEPTION", 1L);

        assertEquals("doc-1", document.getId());
        assertEquals(ParseStatus.Status.FAILED, document.getStatus().getStatus());
        assertEquals("FETCH_EXCEPTION", document.getStatus().getPipesStatus());
        assertFalse(document.getStatus().getErrorsList().isEmpty());
    }

    @Test
    void emptyOutputWithNoMetadataIsSuccessNotFailure() {
        Document document = DocumentBuilder.build(null, "doc-1", "EMPTY_OUTPUT", 1L);

        assertEquals(ParseStatus.Status.SUCCESS, document.getStatus().getStatus());
        assertTrue(document.getStatus().getErrorsList().isEmpty());
    }

    /**
     * {@code pipesStatus} is populated from {@code PipesResult.RESULT_STATUS.name()} (see
     * org.apache.tika.pipes.api.PipesResult), never from a plain "OK" -- there is no such
     * enum constant. A real successful parse reports as e.g. "PARSE_SUCCESS" or
     * "EMIT_SUCCESS", and a real timeout reports as "TIMEOUT" (categorized as a process
     * crash, not a partial success). The status mapping must recognize the real names.
     */
    @Test
    void mapsRealPipesResultStatusNames() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        assertEquals(ParseStatus.Status.SUCCESS,
                DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS", 1L).getStatus().getStatus(),
                "PARSE_SUCCESS is a real, clean success status");
        assertEquals(ParseStatus.Status.SUCCESS,
                DocumentBuilder.build(metadata, "d", "EMIT_SUCCESS", 1L).getStatus().getStatus());
        assertEquals(ParseStatus.Status.SUCCESS,
                DocumentBuilder.build(metadata, "d", "EMPTY_OUTPUT", 1L).getStatus().getStatus());

        assertEquals(ParseStatus.Status.PARTIAL,
                DocumentBuilder.build(metadata, "d", "PARSE_SUCCESS_WITH_EXCEPTION", 1L)
                        .getStatus().getStatus(),
                "succeeded, but with a caveat along the way, is a partial success not a clean one");

        assertEquals(ParseStatus.Status.FAILED,
                DocumentBuilder.build(metadata, "d", "TIMEOUT", 1L).getStatus().getStatus(),
                "TIMEOUT is categorized as a process crash, not a partial success");
        assertEquals(ParseStatus.Status.FAILED,
                DocumentBuilder.build(metadata, "d", "OOM", 1L).getStatus().getStatus());
    }
}
