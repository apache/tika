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
package org.apache.tika.mime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

public class RFC822DetectionTest {

    private static final MimeTypes MIME_TYPES = TikaConfig.getDefaultConfig().getMimeRepository();

    @Test
    public void testBasic() throws Exception {
        for (String txt : new String[]{
                "Date: blah\nSent: someone\r\nthis is a test",
                "date: blah\nSent: someone\r\nthis is a test",
                "date: blah\nDelivered-To: someone\r\nthis is a test"
        }) {
            assertMime("message/rfc822", txt);
        }
        for (String txt : new String[]{
                //test missing colon
                "Date blah\nSent: someone\r\nthis is a test",
                //test precursor junk
                "some precursor junk Date: blah\nSent: someone\r\nthis is a test",
                "some precursor junk\nDate: blah\nSent: someone\r\nthis is a test",
                "some precursor junk:\nDate: blah\nSent: someone\r\nthis is a test",
                //confirm that date is case-insensitive, but delivered-to is case-sensitive
                "date: blah\ndelivered-To: someone\r\nthis is a test",
                //test that a file that starts only with "Subject:" and no other header is
                //detected as text/plain
                "Subject: this is a subject\nand there's some other text",
                "To: someone\nand there's some other text",
                "To: someone or other"
        }) {
            assertMime("text/plain", txt);
        }

        //TIKA-4153, specifically
        String txt = "Some text here 1.\n" + "Some text here 2.\n" + "Some text here 3.\n" +
                "Original Message-----\n" + "From: some_mail@abc.com\n" +
                "Sent: Thursday, October 31, 2019 9:52 AM\n" +
                "To: Some person, (The XYZ group)\n" +
                "Subject: RE: Mr. Random person phone call: MESSAGE\n" + "Hi,\n" +
                "I am available now to receive the call.\n" + "Some text here 4.\n" +
                "Some text here 5.\n" + "Some text here 6.";
        assertMime("text/plain", txt);
    }

    private void assertMime(String expected, String txt) throws IOException {

        MediaType mediaType =
                MIME_TYPES.detect(UnsynchronizedByteArrayInputStream.builder()
                        .setByteArray(txt.getBytes(StandardCharsets.UTF_8)).get(), new Metadata());
        assertEquals(expected, mediaType.toString(), txt);
    }
}
