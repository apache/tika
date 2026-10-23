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
package org.apache.tika.digest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/** The translate branch of DigestHelper, through the service-registered test translator. */
public class DigestHelperTest {

    private static final String MD5_KEY = "tk:digest:MD5";
    private static final Encoder HEX = bytes -> HexFormat.of().formatHex(bytes);
    private static final byte[] SOURCE = "hello, digest helper".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TRANSLATED = "HELLO, DIGEST HELPER".getBytes(StandardCharsets.US_ASCII);

    private static ParseContext contextWithDigester() {
        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, new DigesterFactory() {
            @Override
            public Digester build() {
                return new InputStreamDigester("MD5", MD5_KEY, HEX);
            }

            @Override
            public boolean isSkipContainerDocumentDigest() {
                return false;
            }
        });
        return context;
    }

    private static String md5Of(byte[] bytes) throws IOException {
        Metadata m = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            new InputStreamDigester("MD5", MD5_KEY, HEX).digest(tis, m, new ParseContext());
        }
        return m.get(MD5_KEY);
    }

    @Test
    public void testDigestIsOfTheTranslatedBytesAndStreamIsRewound() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(FailingTestTranslator.MODE, "upper");
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            DigestHelper.maybeDigest(tis, metadata, contextWithDigester());
            assertEquals(md5Of(TRANSLATED), metadata.get(MD5_KEY), "digest of what the translator wrote");
            assertEquals(0, tis.getPosition(), "caller gets the stream back at 0");
            assertArrayEquals(SOURCE, tis.readAllBytes(), "and intact");
        }
    }

    /** A failed translation must publish nothing -- not a digest of the fragment. */
    @Test
    public void testFailedTranslationPublishesNoDigest() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(FailingTestTranslator.MODE, "fail");
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            IOException e = assertThrows(IOException.class,
                    () -> DigestHelper.maybeDigest(tis, metadata, contextWithDigester()));
            assertEquals("translator gave up half way", e.getMessage());
            assertNull(metadata.get(MD5_KEY), "no digest of a partial translation");
            assertNull(metadata.get(HttpHeaders.CONTENT_LENGTH));
            assertEquals(0, tis.getPosition(), "stream still rewound on failure");
        }
    }

    @Test
    public void testNoTranslationDigestsTheStreamItself() throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            DigestHelper.maybeDigest(tis, metadata, contextWithDigester());
            assertEquals(md5Of(SOURCE), metadata.get(MD5_KEY));
        }
    }

    /**
     * A translator that claims the stream and writes nothing must publish nothing. Otherwise
     * every such object gets the digest of zero bytes -- the same wrong value for all of them.
     */
    @Test
    public void testTranslatorThatWritesNothingPublishesNoDigest() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(FailingTestTranslator.MODE, "silent");
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            DigestHelper.maybeDigest(tis, metadata, contextWithDigester());
            assertNull(metadata.get(MD5_KEY), "no digest of the zero bytes it produced");
            assertNull(metadata.get(HttpHeaders.CONTENT_LENGTH));
        }
    }

    /** A translator that closes the sink must not publish on our behalf, nor break the parse. */
    @Test
    public void testTranslatorClosingTheStreamStillDigestsWhatItWrote() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(FailingTestTranslator.MODE, "closes");
        try (TikaInputStream tis = TikaInputStream.get(SOURCE, new Metadata())) {
            DigestHelper.maybeDigest(tis, metadata, contextWithDigester());
            assertEquals(md5Of(TRANSLATED), metadata.get(MD5_KEY));
        }
    }
}
