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
package org.apache.tika.extractor;

import org.xml.sax.ContentHandler;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Registered-by-name test double, needed only so {@code WireRestrictedParseContextTest} has a
 * real, resolvable {@link EmbeddedDocumentExtractor} to prove the wire gate actually blocks it.
 */
@TikaComponent(name = "mock-embedded-document-extractor")
public class MockEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    @Override
    public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
        return false;
    }

    @Override
    public void parseEmbedded(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                              ParseContext parseContext, boolean outputHtml) {
        // never invoked: this double exists only to be name-resolvable for the wire-block test
    }
}
