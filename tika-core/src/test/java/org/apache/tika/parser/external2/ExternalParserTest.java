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
package org.apache.tika.parser.external2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RegexCaptureParser;

public class ExternalParserTest extends TikaTest {

    @Test
    public void testConfigRegexCaptureParser() throws Exception {
        assumeTrue(org.apache.tika.parser.external.ExternalParser.check(new String[]{
                "file", "--version"
        }));
        try (InputStream is = TikaConfig.class.getResourceAsStream("TIKA-3557.xml")) {

            TikaConfig config = new TikaConfig(is);
            CompositeParser p = (CompositeParser) config.getParser();
            assertEquals(1, p.getAllComponentParsers().size());
            ExternalParser externalParser = (ExternalParser) p.getAllComponentParsers().get(0);

            Parser outputParser = externalParser.getOutputParser();
            assertEquals(RegexCaptureParser.class, outputParser.getClass());

            Metadata m = new Metadata();
            ContentHandler contentHandler = new DefaultHandler();
            String output = "Something\n" +
                    "Title: the quick brown fox\n" +
                    "Author: jumped over\n" +
                    "Created: 10/20/2024";
            try (InputStream stream =
                         TikaInputStream.get(output.getBytes(StandardCharsets.UTF_8))) {
                outputParser.parse(stream, contentHandler, m, new ParseContext());
            }
            assertEquals("the quick brown fox", m.get("title"));
        }
    }

    @Test
    public void testConfigBasic() throws Exception {
        assumeTrue(org.apache.tika.parser.external.ExternalParser.check(new String[]{"file", "--version"}));
        try (InputStream is = TikaConfig.class.getResourceAsStream("TIKA-3557-no-output-parser.xml")) {
            TikaConfig config = new TikaConfig(is);
            CompositeParser p = (CompositeParser) config.getParser();
            assertEquals(1, p.getAllComponentParsers().size());
            ExternalParser externalParser = (ExternalParser) p.getAllComponentParsers().get(0);

            XMLResult xmlResult = getXML("example.xml", externalParser);
            assertContains("<body>text/xml</body>", xmlResult.xml);
        }
    }
}
