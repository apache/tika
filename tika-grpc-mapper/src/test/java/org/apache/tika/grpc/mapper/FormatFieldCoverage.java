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

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.v1.ParseResponse;

/**
 * Shared helpers for format field-coverage sweeps across classpath fixtures.
 */
final class FormatFieldCoverage {

    private static final Logger LOG = LoggerFactory.getLogger(FormatFieldCoverage.class);

    private FormatFieldCoverage() {
    }

    record SweepResult(int processed, int typed, Map<String, Integer> fieldCounts) {
    }

    static SweepResult sweep(ParseFixtureSupport support, Iterable<String> fileNames,
                             Predicate<ParseResponse> isTyped,
                             java.util.function.Function<ParseResponse, Message> typedMessage) {
        Map<String, Integer> fieldCounts = new TreeMap<>();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger typed = new AtomicInteger();

        for (String fileName : fileNames) {
            try {
                ParseResponse response = support.map(support.parseBody(fileName), fileName);
                processed.incrementAndGet();
                if (!isTyped.test(response)) {
                    continue;
                }
                typed.incrementAndGet();
                Message message = typedMessage.apply(response);
                for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
                    fieldCounts.merge(entry.getKey().getName(), 1, Integer::sum);
                }
            } catch (Exception e) {
                LOG.warn("Skipping fixture {}: {}", fileName, e.toString());
            }
        }
        return new SweepResult(processed.get(), typed.get(), fieldCounts);
    }

    static void logTopFields(Logger log, Map<String, Integer> fieldCounts, int limit) {
        fieldCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .forEach(entry -> log.info(" - {}: {}", entry.getKey(), entry.getValue()));
    }

}
