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

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.junit.Test;

public class FictionBookParserTest extends TikaTest {
  
    @Test
    public void testFB2() throws Exception {
        XMLResult r = getXML("test.fb2", new FictionBookParser(), new Metadata(), new ParseContext());
        assertContains("1812", r.xml);
    }

    @Test
    public void testEmbedded() throws Exception {
        try (InputStream input = getTestDocumentAsStream("test.fb2")) {
            ContainerExtractor extractor = new ParserContainerExtractor();
            TikaInputStream stream = TikaInputStream.get(input);

            assertEquals(true, extractor.isSupported(stream));

            // Process it
            TrackingHandler handler = new TrackingHandler();
            extractor.extract(stream, null, handler);

            assertEquals(2, handler.filenames.size());
        }
    }
}
