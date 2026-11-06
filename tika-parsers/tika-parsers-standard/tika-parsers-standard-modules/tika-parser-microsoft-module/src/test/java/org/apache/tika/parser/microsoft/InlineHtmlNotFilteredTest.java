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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.nodes.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.JsoupFilter;

/** HTML that is part of a document (message body, help topic, rich-text field) never reaches a JsoupFilter. */
public class InlineHtmlNotFilteredTest extends TikaTest {

    private static class Counting implements JsoupFilter {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Document filter(Document document, Metadata metadata, ParseContext context) {
            calls.incrementAndGet();
            return document;
        }
    }

    // each reaches its parser's inline HTML path: msg HTML body, chm topics, accdb rich text
    @ParameterizedTest
    @ValueSource(strings = {"testMSG_chinese.msg", "testChm.chm", "testAccess2.accdb"})
    public void testInlineHtmlIsNotFiltered(String file) throws Exception {
        Counting filter = new Counting();
        ParseContext context = new ParseContext();
        context.set(JsoupFilter.class, filter);
        getRecursiveMetadata(file, context, false);
        assertEquals(0, filter.calls.get());
    }
}
