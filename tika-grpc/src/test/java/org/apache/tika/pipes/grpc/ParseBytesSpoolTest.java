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
package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ComponentIds;
import org.apache.tika.pipes.core.fetcher.BytesFetcher;
import org.apache.tika.pipes.core.fetcher.InlineBytes;

class ParseBytesSpoolTest {

    private static final int MAX_INLINE_BYTES = 4;

    @TempDir
    Path tmp;

    private Path userConfig;
    private ParseBytesSpool spool;

    @BeforeEach
    void create() throws Exception {
        userConfig = tmp.resolve("tika-config.json");
        Files.writeString(userConfig, "{}");
        spool = ParseBytesSpool.create(userConfig, tmp, MAX_INLINE_BYTES);
    }

    @AfterEach
    void release() {
        spool.release(null);
    }

    @Test
    void theEffectiveConfigAddsTheSpoolFetcherAndLeavesTheUserConfigAlone() throws Exception {
        String basePath = new ObjectMapper().readTree(spool.effectiveConfig().toFile())
                .path("fetchers").path(spool.fetcherId())
                .path("file-system-fetcher").path("basePath").asText();

        assertEquals(spool.directory().toAbsolutePath().toString(), basePath);
        assertTrue(ComponentIds.isSystem(spool.fetcherId()));
        assertEquals("{}", Files.readString(userConfig));
    }

    /** An error message sent back to the caller does not name the spool directory. */
    @Test
    void scrubTakesTheSpoolDirectoryOutOfAMessage() {
        String dir = spool.directory().toAbsolutePath().toString();

        assertEquals("java.io.IOException: <content>/parse-bytes-1 is truncated",
                spool.scrub("java.io.IOException: " + dir + "/parse-bytes-1 is truncated"));
        assertEquals("no path here", spool.scrub("no path here"));
        assertNull(spool.scrub(null));
    }

    @Test
    void eachSpoolHasItsOwnFetcherAndDirectory() throws Exception {
        ParseBytesSpool other = ParseBytesSpool.create(userConfig, tmp, MAX_INLINE_BYTES);
        try {
            assertNotEquals(spool.fetcherId(), other.fetcherId());
            assertNotEquals(spool.directory(), other.directory());
        } finally {
            other.release(null);
        }
    }

    @Test
    void aPayloadUpToTheInlineLimitTravelsWithTheRequest() throws Exception {
        ParseContext context = new ParseContext();
        try (ParseBytesSpool.Payload payload = spool.stage(bytes("<x/>"), " a.html ", context)) {
            assertEquals(BytesFetcher.FETCHER_ID, payload.fetchKey().getFetcherId());
            assertEquals("a.html", payload.fetchKey().getFetchKey());
            assertNotNull(context.get(InlineBytes.class));
            assertEquals(0, spoolFiles());
        }
    }

    @Test
    void aLargerPayloadIsSpooledUntilThePayloadIsClosed() throws Exception {
        ParseContext context = new ParseContext();
        try (ParseBytesSpool.Payload payload = spool.stage(bytes("<x/>!"), "a.html", context)) {
            assertEquals(spool.fetcherId(), payload.fetchKey().getFetcherId());
            Path file = spool.directory().resolve(payload.fetchKey().getFetchKey());
            assertEquals("<x/>!", Files.readString(file));
            assertNull(context.get(InlineBytes.class));
        }
        assertEquals(0, spoolFiles());
    }

    @Test
    void theScrubRemovesWhatTheSpoolFileLeftInTheMetadata() throws Exception {
        try (ParseBytesSpool.Payload payload = spool.stage(bytes("<x/>!"), "", new ParseContext())) {
            Metadata parsed = new Metadata();
            parsed.set(TikaCoreProperties.SOURCE_PATH, "parse-bytes-123");
            parsed.set(TikaCoreProperties.RESOURCE_NAME_KEY, "parse-bytes-123");
            parsed.set(TikaCoreProperties.TITLE, "Kept");
            parsed.set(TikaCoreProperties.TIKA_PARSED_BY, "PDFParser");
            assertNotNull(parsed.get(TikaCoreProperties.SOURCE_PATH));

            Metadata scrubbed = payload.scrub(parsed);

            assertNull(scrubbed.get(TikaCoreProperties.SOURCE_PATH));
            assertNull(scrubbed.get(TikaCoreProperties.RESOURCE_NAME_KEY));
            assertEquals("Kept", scrubbed.get(TikaCoreProperties.TITLE));
            assertEquals("PDFParser", scrubbed.get(TikaCoreProperties.TIKA_PARSED_BY));
        }
    }

    @Test
    void aBlankNameIsTreatedAsNoName() throws Exception {
        // U+2003 is blank, but trim() keeps it.
        try (ParseBytesSpool.Payload payload = spool.stage(bytes("<x/>!"), "\u2003",
                new ParseContext())) {
            Metadata parsed = new Metadata();
            parsed.set(TikaCoreProperties.RESOURCE_NAME_KEY, "parse-bytes-123");

            assertNull(payload.scrub(parsed).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        }
    }

    @Test
    void theScrubKeepsTheCallersName() throws Exception {
        try (ParseBytesSpool.Payload payload = spool.stage(bytes("<x/>!"), "page.html",
                new ParseContext())) {
            Metadata parsed = new Metadata();
            parsed.set(TikaCoreProperties.RESOURCE_NAME_KEY, "page.html");

            assertEquals("page.html",
                    payload.scrub(parsed).get(TikaCoreProperties.RESOURCE_NAME_KEY));
            assertNull(payload.scrub(null));
        }
    }

    @Test
    void releaseRemovesTheDirectoryAndTheEffectiveConfig() throws Exception {
        Path dir = spool.directory();
        Path effective = spool.effectiveConfig();

        spool.release(null);
        spool.release(null);

        assertFalse(Files.exists(dir));
        assertFalse(Files.exists(effective));
    }

    @Test
    void anOpenPayloadKeepsTheDirectoryUntilItCloses() throws Exception {
        ParseBytesSpool.Payload payload = spool.stage(bytes("<x/>!"), "a.html", new ParseContext());

        spool.release(null);

        assertTrue(Files.isDirectory(spool.directory()), "the open payload still uses the directory");
        assertThrows(IllegalStateException.class,
                () -> spool.stage(bytes("<y/>!"), "b.html", new ParseContext()),
                "a released spool must not create spool files");
        payload.close();
        assertFalse(Files.exists(spool.directory()), "the last payload to close deletes the directory");
    }

    /**
     * Closing a payload twice counts once: the second close must not delete the directory
     * while another payload still has a file in it.
     */
    @Test
    void closingAPayloadTwiceCountsOnce() throws Exception {
        ParseBytesSpool.Payload first = spool.stage(bytes("<x/>!"), "a.html", new ParseContext());
        ParseBytesSpool.Payload second = spool.stage(bytes("<y/>!"), "b.html", new ParseContext());
        spool.release(null);

        first.close();
        first.close();

        assertTrue(Files.isDirectory(spool.directory()), "the second payload is still open");
        assertEquals(1, spoolFiles());
        second.close();
        assertFalse(Files.exists(spool.directory()));
    }

    private long spoolFiles() throws IOException {
        try (var files = Files.list(spool.directory())) {
            return files.count();
        }
    }

    private static InputStream bytes(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
