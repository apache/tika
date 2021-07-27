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
package org.apache.tika.parser.xml;

import static org.apache.tika.TikaTest.assertContains;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class FictionBookParserTest {

    @Test
    public void testFB2() throws Exception {
        try (InputStream input = FictionBookParserTest.class
                .getResourceAsStream("/test-documents/test.fb2")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new FictionBookParser().parse(input, handler, metadata, new ParseContext());
            String content = handler.toString();

            assertContains("1812", content);
        }
    }
}
