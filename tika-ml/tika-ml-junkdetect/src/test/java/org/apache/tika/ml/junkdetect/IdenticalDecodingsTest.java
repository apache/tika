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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class IdenticalDecodingsTest {

    /** E1 is á in windows-1252, windows-1250 and ISO-8859-2 alike: the most frequent charset wins. */
    @Test
    public void identicalLatinDecodesPickMostFrequent() throws Exception {
        byte[] bytes = "the name is ándre".getBytes(StandardCharsets.ISO_8859_1);
        List<EncodingResult> results;
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            results = new DefaultEncodingDetector().detect(tis, new Metadata(), new ParseContext());
        }
        assertEquals("windows-1252", results.get(0).getCharset().name());
    }
}
