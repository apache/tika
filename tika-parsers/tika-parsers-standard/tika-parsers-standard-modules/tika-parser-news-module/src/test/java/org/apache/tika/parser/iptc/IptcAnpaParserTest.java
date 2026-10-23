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
package org.apache.tika.parser.iptc;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class IptcAnpaParserTest extends TikaTest {

    private static final String REUTERS = message("Markets rallied, Reuters reported.");
    private static final String UNMARKED = message("Markets rallied on Tuesday.");

    @Test
    public void testFormatIsNotCarriedAcrossParses() throws Exception {
        String expected = parse(new IptcAnpaParser(), UNMARKED).get(TikaCoreProperties.PUBLISHER);

        IptcAnpaParser parser = new IptcAnpaParser();
        assertEquals("Reuters", parse(parser, REUTERS).get(TikaCoreProperties.PUBLISHER));
        assertEquals(expected, parse(parser, UNMARKED).get(TikaCoreProperties.PUBLISHER));
    }

    private static Metadata parse(IptcAnpaParser parser, String message) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(message.getBytes(US_ASCII))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }
        return metadata;
    }

    // SYN SYN SOH header STX body ETX footer EOT
    private static String message(String body) {
        return "\u0016\u0016\u0001a0001 \u0013\u0011BC-Markets 10-21 0000\u0002\n" + body +
                "\n\u0003 21-10-26 0000GMT\u0004";
    }
}
