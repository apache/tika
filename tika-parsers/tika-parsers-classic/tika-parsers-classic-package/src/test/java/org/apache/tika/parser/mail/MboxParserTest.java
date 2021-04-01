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
package org.apache.tika.parser.mail;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.TypeDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mbox.MboxParser;
import org.apache.tika.sax.BodyContentHandler;

public class MboxParserTest extends TikaTest {

    protected ParseContext recursingContext;
    private Parser autoDetectParser;
    private TypeDetector typeDetector;
    private MboxParser mboxParser;

    @Before
    public void setUp() throws Exception {
        typeDetector = new TypeDetector();
        autoDetectParser = new AutoDetectParser(typeDetector);
        recursingContext = new ParseContext();
        recursingContext.set(Parser.class, autoDetectParser);

        mboxParser = new MboxParser();
        mboxParser.setTracking(true);
    }

    @Test
    public void testOverrideDetector() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = getResourceAsStream("/test-documents/single_mail.mbox")) {
            mboxParser.parse(stream, handler, metadata, context);
        }

        Metadata firstMail = mboxParser.getTrackingMetadata().get(0);
        assertEquals("message/rfc822", firstMail.get(Metadata.CONTENT_TYPE));
    }
}
