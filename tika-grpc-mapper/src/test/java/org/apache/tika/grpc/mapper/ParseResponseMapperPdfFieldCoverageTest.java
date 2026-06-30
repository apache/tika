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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.v1.ParseResponse;
import org.apache.tika.grpc.v1.PdfMetadata;

/**
 * Sweeps PDF fixtures on the classpath and reports which {@link PdfMetadata} fields are populated.
 * Ported from module-parser {@code PdfFieldCoverageTest}.
 */
class ParseResponseMapperPdfFieldCoverageTest extends ParseFixtureSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ParseResponseMapperPdfFieldCoverageTest.class);

    private static final List<String> EXPECTED_CANDIDATES = Arrays.asList(
            "pdf_version", "producer", "is_encrypted", "n_pages", "doc_info_title", "doc_info_creator");

    @Test
    void analyzePdfFieldCoverageAcrossFixtures() throws Exception {
        List<String> pdfFiles = ClasspathTestDocuments.listByExtension(".pdf");
        Assumptions.assumeFalse(pdfFiles.isEmpty(), "No PDF fixtures on classpath");

        Map<String, Integer> fieldPresenceCounts = new TreeMap<>();
        AtomicInteger docsParsed = new AtomicInteger();
        AtomicInteger pdfTypedCount = new AtomicInteger();
        AtomicInteger genericTypedCount = new AtomicInteger();
        AtomicInteger additionalStructNonEmpty = new AtomicInteger();

        for (String fileName : pdfFiles) {
            try {
                ParseResponse response = map(parseBody(fileName), fileName);
                docsParsed.incrementAndGet();
                if (response.hasPdf()) {
                    pdfTypedCount.incrementAndGet();
                    PdfMetadata pdf = response.getPdf();
                    for (Map.Entry<FieldDescriptor, Object> entry : pdf.getAllFields().entrySet()) {
                        fieldPresenceCounts.merge(entry.getKey().getName(), 1, Integer::sum);
                    }
                    if (pdf.hasAdditionalMetadata() && pdf.getAdditionalMetadata().getFieldsCount() > 0) {
                        additionalStructNonEmpty.incrementAndGet();
                    }
                } else if (response.hasGeneric()) {
                    genericTypedCount.incrementAndGet();
                }
            } catch (Exception e) {
                LOG.warn("Skipping PDF fixture {}: {}", fileName, e.toString());
            }
        }

        assertTrue(docsParsed.get() > 0, "Should parse at least one PDF fixture");
        assertTrue(pdfTypedCount.get() >= 1, "Most PDFs should map to typed pdf metadata");

        int hits = 0;
        for (String name : EXPECTED_CANDIDATES) {
            Integer count = fieldPresenceCounts.get(name);
            if (count != null && count > 0) {
                hits++;
            }
        }
        assertTrue(hits >= 2, "At least some common PDF fields should appear across fixtures");

        LOG.info("PDF typed count: {}, generic typed count: {}, additional struct non-empty: {}",
                pdfTypedCount.get(), genericTypedCount.get(), additionalStructNonEmpty.get());
        fieldPresenceCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .forEach(entry -> LOG.info(" - {}: {}", entry.getKey(), entry.getValue()));
    }

}
