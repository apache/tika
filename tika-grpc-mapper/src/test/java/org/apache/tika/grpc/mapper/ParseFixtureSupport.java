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

import org.apache.tika.TikaTest;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Shared helpers: parse Tika test fixtures and map their metadata through
 * {@link DocumentBuilder}.
 */
public abstract class ParseFixtureSupport extends TikaTest {

    protected Metadata parseFixture(String fileName) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream input = getResourceAsStream("/test-documents/" + fileName)) {
            AUTO_DETECT_PARSER.parse(input, new BodyContentHandler(-1), metadata,
                    new ParseContext());
        }
        return metadata;
    }

    protected Document map(Metadata primary, String docId, long fetchParseTimeMs) {
        // "PARSE_SUCCESS" mirrors a real org.apache.tika.pipes.api.PipesResult.RESULT_STATUS
        // name -- there is no "OK" constant on that enum.
        return DocumentBuilder.build(primary, docId, "PARSE_SUCCESS", fetchParseTimeMs);
    }

    protected Document map(Metadata primary, String docId) {
        return map(primary, docId, 1L);
    }
}
