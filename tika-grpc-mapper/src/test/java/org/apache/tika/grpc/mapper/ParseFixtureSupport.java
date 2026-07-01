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

import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToMarkdownContentHandler;

/**
 * Shared helpers: parse Tika test fixtures and map them through {@link DocumentBuilder}.
 */
public abstract class ParseFixtureSupport extends TikaTest {

    /**
     * Result of parsing a fixture with markdown body extraction enabled.
     */
    public record ParseFixture(Metadata primary, List<Metadata> allMetadata, String body) {
    }

    protected ParseFixture parseBody(String fileName) throws Exception {
        java.io.StringWriter writer = new java.io.StringWriter();
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler(writer);
        Metadata metadata = new Metadata();
        try (TikaInputStream input = getResourceAsStream("/test-documents/" + fileName)) {
            AUTO_DETECT_PARSER.parse(input, handler, metadata, new ParseContext());
        }
        return new ParseFixture(metadata, List.of(metadata), writer.toString());
    }

    protected Document map(ParseFixture fixture, String docId, long parseTimeMs) {
        // "PARSE_SUCCESS" mirrors a real org.apache.tika.pipes.api.PipesResult.RESULT_STATUS
        // name -- there is no "OK" constant on that enum.
        return DocumentBuilder.build(
                fixture.primary(), fixture.allMetadata(), fixture.body(), docId, "PARSE_SUCCESS", parseTimeMs);
    }

    protected Document map(ParseFixture fixture, String docId) {
        return map(fixture, docId, 1L);
    }

}
