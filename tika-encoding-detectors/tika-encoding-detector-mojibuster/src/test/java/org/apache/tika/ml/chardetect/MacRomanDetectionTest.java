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
package org.apache.tika.ml.chardetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class MacRomanDetectionTest {

    /**
     * MacRoman curly quotes (0xD2-0xD5) are letters (Ò Ó Ô Õ) in windows-1252; the model's
     * MacRoman call must not be rewritten to windows-1252 on that account.
     */
    @Test
    public void curlyQuotesStayMacRoman() throws Exception {
        Charset mac = Charset.forName("x-MacRoman");
        byte[] probe = ("<html><body><p>The Earth’s climate system is driven by energy from the Sun."
                + " Scientists call this the “greenhouse effect”, and it’s why the planet"
                + " doesn’t freeze at night. The ‘carbon cycle’ moves carbon between the"
                + " atmosphere, the oceans and the land; it isn’t a closed loop, and today’s"
                + " emissions add to it faster than it can respond.</p></body></html>").getBytes(mac);
        List<EncodingResult> results;
        try (TikaInputStream tis = TikaInputStream.get(probe)) {
            results = new MojibusterEncodingDetector().detect(tis, new Metadata(), new ParseContext());
        }
        assertFalse(results.isEmpty());
        assertEquals(mac, results.get(0).getCharset(), results.toString());
    }
}
