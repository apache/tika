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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.v1.OfficeMetadata;
import org.apache.tika.grpc.v1.ParseResponse;

/**
 * Sweeps Office fixtures and reports {@link OfficeMetadata} field presence.
 * Ported from module-parser {@code OfficeFieldCoverageTest}.
 */
class ParseResponseMapperOfficeFieldCoverageTest extends ParseFixtureSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ParseResponseMapperOfficeFieldCoverageTest.class);

    @Test
    void analyzeOfficeFieldCoverageAcrossFixtures() throws Exception {
        List<String> officeFiles = ClasspathTestDocuments.listByExtension(".docx");
        officeFiles.addAll(ClasspathTestDocuments.listByExtension(".doc"));
        officeFiles.addAll(ClasspathTestDocuments.listByExtension(".xlsx"));
        officeFiles.addAll(ClasspathTestDocuments.listByExtension(".pptx"));
        Assumptions.assumeFalse(officeFiles.isEmpty(), "No Office fixtures on classpath");

        Map<String, Integer> fieldCounts = new TreeMap<>();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger typedOffice = new AtomicInteger();

        for (String fileName : officeFiles) {
            try {
                ParseResponse response = map(parseBody(fileName), fileName);
                processed.incrementAndGet();
                if (response.hasOffice()) {
                    typedOffice.incrementAndGet();
                    OfficeMetadata office = response.getOffice();
                    for (Map.Entry<FieldDescriptor, Object> entry : office.getAllFields().entrySet()) {
                        fieldCounts.merge(entry.getKey().getName(), 1, Integer::sum);
                    }
                    if (response.getMetadataCount() > 0) {
                        fieldCounts.merge("_metadata_mirror_entries", response.getMetadataCount(), Integer::sum);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Skipping Office fixture {}: {}", fileName, e.toString());
            }
        }

        assertTrue(processed.get() > 0, "Should process Office samples");
        assertTrue(typedOffice.get() >= 1, "At least one Office doc should be typed");

        fieldCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(25)
                .forEach(entry -> LOG.info(" - {}: {}", entry.getKey(), entry.getValue()));
    }

}
