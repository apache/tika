/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.strings;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class Latin1StringsParserTest {

    @Test
    public void testParse() throws Exception {

        String testStr =
                "These are Latin1 accented scripts: \u00C2 \u00C3 \u00C9 \u00DC \u00E2 " +
                        "\u00E3 \u00E9 \u00FC";
        String smallStr = "ab";

        byte[] iso8859Bytes = testStr.getBytes(ISO_8859_1);
        byte[] utf8Bytes = testStr.getBytes(UTF_8);
        byte[] utf16Bytes = testStr.getBytes(UTF_16);
        byte[] zeros = new byte[10];
        byte[] smallString = smallStr.getBytes(ISO_8859_1);
        byte[] trashBytes = {0x00, 0x01, 0x02, 0x03, 0x1E, 0x1F, (byte) 0xFF};

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(iso8859Bytes);
        baos.write(zeros);
        baos.write(utf8Bytes);
        baos.write(trashBytes);
        baos.write(utf16Bytes);
        baos.write(zeros);
        baos.write(smallString);

        Parser parser = new Latin1StringsParser();
        ContentHandler handler = new BodyContentHandler();

        try (InputStream stream = new ByteArrayInputStream(baos.toByteArray())) {
            parser.parse(stream, handler, new Metadata(), new ParseContext());
        }

        String result = handler.toString();
        String expected = testStr + "\n" + testStr + "\n" + testStr + "\n";

        // Test if result contains only the test string appended 3 times
        assertTrue(result.equals(expected));
    }
}
