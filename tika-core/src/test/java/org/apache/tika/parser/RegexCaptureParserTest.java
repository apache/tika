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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class RegexCaptureParserTest {

    @Test
    public void testBasic() throws Exception {
        Metadata m = new Metadata();
        ContentHandler contentHandler = new DefaultHandler();
        String output = "Something\n" +
                "Title: the quick brown fox\n" +
                "Author: jumped over\n" +
                "Created: 10/20/2024";
        RegexCaptureParser parser = new RegexCaptureParser();
        Map<String, String> regexes = new HashMap<>();
        regexes.put("title", "^Title: ([^\r\n]+)");
        parser.setCaptureMap(regexes);

        try (InputStream stream =
                     TikaInputStream.get(output.getBytes(StandardCharsets.UTF_8))) {
            parser.parse(stream, contentHandler, m, new ParseContext());
        }
        assertEquals("the quick brown fox", m.get("title"));
    }
}
