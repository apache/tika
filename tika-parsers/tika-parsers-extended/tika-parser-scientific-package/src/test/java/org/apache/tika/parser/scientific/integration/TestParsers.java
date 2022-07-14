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
 *
 */
package org.apache.tika.parser.scientific.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.CompositeExternalParser;
import org.apache.tika.parser.ocr.TesseractOCRParser;

/**
 * We fixed parser ordering in 2.4.1.  This confirms going forward that the integration
 * of tika-parsers-standard with the tika-parser-scientific package maintains
 * parser order.
 *
 * This does not currently test parsers added after 2.4.1.
 *
 * We included 2.4.0 for historical reasons to show what the behavior was
 * before the fix.
 */
public class TestParsers {

    @Test
    public void testDiffsFrom241() throws Exception {

        Map<String, String> currentDefault = getDefault();
        String path241 = "/2.4.1-no-tesseract.txt";
        if (new TesseractOCRParser().hasTesseract()) {
            path241 = "/2.4.1-tesseract.txt";
        }

        int checked = 0;
        //The initial lists were developed with exiftool installed.  We have since
        //modified the 2.4.1-* files to act as if no exiftool is installed.
        //However, on systems with ffmpeg or exiftool installed, we need
        //to override those file formats
        CompositeParser externalParser = (CompositeParser) new CompositeExternalParser();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(
                             getClass().getResourceAsStream(path241),
                             StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                String[] data = line.split("\t");
                String mediaType = data[0];
                String parserClass = data[1];

                Parser external = externalParser.getParsers().get(MediaType.parse(mediaType));
                if (external != null) {
                    parserClass = externalParser.getClass().toString();
                }
                assertEquals(parserClass, currentDefault.get(mediaType),
                        "for mediaType '" + mediaType + "'");
                checked++;
                line = reader.readLine();
            }
        }
        assertTrue(checked > 340);
    }

    private Map<String, String> getDefault() throws IOException, TikaException {
        DefaultParser p = new DefaultParser();
        Map<String, String> ret = new HashMap<>();
        for (Map.Entry<MediaType, Parser> e : p.getParsers().entrySet()) {
            ret.put(e.getKey().toString(), e.getValue().getClass().toString());
        }
        return ret;
    }
}
