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

import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;

public class FictionBookParserTest {

    //not sure why this isn't passing
    @Test
    public void testEmbedded() throws Exception {
        try (InputStream input = FictionBookParserTest.class
                .getResourceAsStream("/test-documents/test.fb2")) {
            ContainerExtractor extractor = new ParserContainerExtractor();
            TikaInputStream stream = TikaInputStream.get(input);

            assertEquals(true, extractor.isSupported(stream));

            // Process it
            TikaTest.TrackingHandler handler = new TikaTest.TrackingHandler();
            extractor.extract(stream, null, handler);

            assertEquals(2, handler.filenames.size());
        }
    }
}
