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
package org.apache.tika.parser.pdf;

import static junit.framework.TestCase.assertEquals;

import java.io.InputStream;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

public class PDFPreflightParserTest extends TikaTest {

    private static Parser PREFLIGHT_AUTO_DETECT_PARSER;

    @BeforeClass
    public static void setUp() throws Exception {
        try (InputStream is = PDFPreflightParser.class
                .getResourceAsStream("tika-preflight-config.xml")) {
            PREFLIGHT_AUTO_DETECT_PARSER = new AutoDetectParser(new TikaConfig(is).getParser());
        }
    }

    @Test
    public void testBasic() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPDFFileEmbInAnnotation.pdf",
                PREFLIGHT_AUTO_DETECT_PARSER);
        assertEquals(2, metadataList.size());

        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(PDF.PREFLIGHT_IS_LINEARIZED));
        assertEquals("true", m.get(PDF.PREFLIGHT_IS_VALID));
        assertEquals("PDF_A1B", m.get(PDF.PREFLIGHT_SPECIFICATION));
        assertEquals("2", m.get(PDF.PREFLIGHT_TRAILER_COUNT));
        assertEquals("STREAM", m.get(PDF.PREFLIGHT_XREF_TYPE));
        assertEquals("false", m.get(PDF.PREFLIGHT_INCREMENTAL_UPDATES));
    }
}
