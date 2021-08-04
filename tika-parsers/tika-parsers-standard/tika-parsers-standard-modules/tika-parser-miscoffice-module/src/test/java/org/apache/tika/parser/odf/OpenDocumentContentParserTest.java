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
package org.apache.tika.parser.odf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.XMLReaderUtils;

public class OpenDocumentContentParserTest extends TikaTest {

    @Test
    public void testEmbeddedLists() throws Exception {
        Parser p = new OpenDocumentContentParser();
        Metadata metadata = new Metadata();
        XMLResult r = null;
        try (InputStream is = new GzipCompressorInputStream(getResourceAsStream(
                "/test-documents/testODTBodyListOpenClose.xml.gz"))) {
            r = getXML(is, p, metadata);
        }
        String xml = r.xml;
        //extract all the tags
        Matcher m = Pattern.compile("(<[^>]+>)").matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(m.group(1)).append(" ");
        }
        String tags = sb.toString();
        assertContains("</p> <ol> <li> <p> </p> <ul> <li> <p> </p> </li> <li> <p> </p> </li> " +
                        "</ul> </li> </ol>",
                tags);
        assertContains("Exercice", xml);

        m = Pattern.compile("<p class=\"annotation\">[^<]+<\\/p>").matcher(xml);
        assertTrue(m.find());

        m = Pattern.compile("<p class=\"annotation\">[^<]+<\\/annotation>").matcher(xml);
        assertFalse(m.find());

        //just make sure this doesn't throw any exceptions
        XMLReaderUtils.parseSAX(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                new DefaultHandler(), new ParseContext());
    }
}
