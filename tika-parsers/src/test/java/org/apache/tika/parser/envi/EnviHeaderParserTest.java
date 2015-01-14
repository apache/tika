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

package org.apache.tika.parser.envi;

import static org.apache.tika.TikaTest.assertContains;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.Test;

/**
 * Test cases to exercise the {@link EnviHeaderParser}.
 */
public class EnviHeaderParserTest {
    @Test
    public void testParseGlobalMetadata() throws Exception {
        if (System.getProperty("java.version").startsWith("1.5")) {
            return;
        }

        Parser parser = new EnviHeaderParser();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = EnviHeaderParser.class
                .getResourceAsStream("/test-documents/envi_test_header.hdr");
        assertNotNull("Test ENVI file not found", stream);
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        // Check content of test file
        String content = handler.toString();
        assertContains("<body><p>ENVI</p>", content);
        assertContains("<p>samples = 2400</p>", content);
        assertContains("<p>lines   = 2400</p>", content);
        assertContains("<p>map info = {Sinusoidal, 1.5000, 1.5000, -10007091.3643, 5559289.2856, 4.6331271653e+02, 4.6331271653e+02, , units=Meters}</p>", content);
        assertContains("content=\"application/envi.hdr\"", content);
        assertContains("projection info = {16, 6371007.2, 0.000000, 0.0, 0.0, Sinusoidal, units=Meters}", content);
    }
}
