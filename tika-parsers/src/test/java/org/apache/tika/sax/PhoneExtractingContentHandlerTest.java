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

package org.apache.tika.sax;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.Test;

import java.io.InputStream;

import static org.apache.tika.TikaTest.assertContains;

/**
 * Test class for the {@link org.apache.tika.sax.PhoneExtractingContentHandler}
 * class. This demonstrates how to parse a document and retrieve any phone numbers
 * found within.
 *
 * The phone numbers are added to a multivalued Metadata object under the key, "phonenumbers".
 * You can get an array of phone numbers by calling metadata.getValues("phonenumber").
 */
public class PhoneExtractingContentHandlerTest {
    @Test
    public void testExtractPhoneNumbers() throws Exception {
        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        // The PhoneExtractingContentHandler will examine any characters for phone numbers before passing them
        // to the underlying Handler.
        PhoneExtractingContentHandler handler = new PhoneExtractingContentHandler(new BodyContentHandler(), metadata);
        InputStream stream = PhoneExtractingContentHandlerTest.class.getResourceAsStream("/test-documents/testPhoneNumberExtractor.odt");
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        }
        finally {
            stream.close();
        }
        String[] phoneNumbers = metadata.getValues("phonenumbers");
        assertContains("9498888888", phoneNumbers[0]);
        assertContains("9497777777", phoneNumbers[1]);
        assertContains("9496666666", phoneNumbers[2]);
        assertContains("9495555555", phoneNumbers[3]);
        assertContains("4193404645", phoneNumbers[4]);
        assertContains("9044687081", phoneNumbers[5]);
        assertContains("2604094811", phoneNumbers[6]);
    }
}
