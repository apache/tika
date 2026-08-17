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
package org.apache.tika;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/** {@link Tika#detect} peeks: it must dispose only what it spooled itself. */
public class TikaDetectStreamOwnershipTest {

    private static final byte[] DATA = "hello world\n".getBytes(StandardCharsets.UTF_8);

    private static class CloseCountingInputStream extends ByteArrayInputStream {
        int closes = 0;

        CloseCountingInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closes++;
            super.close();
        }
    }

    @Test
    public void testDetectDoesNotCloseCallersStream() throws Exception {
        Tika tika = new Tika();
        CloseCountingInputStream stream = new CloseCountingInputStream(DATA);
        tika.detect(stream, new Metadata());
        assertEquals(0, stream.closes,
                "detect() must not close a stream it was handed; the javadoc promises this");
    }

    /** Stands in for the container detectors, which call getFile() and so force a spill. */
    private static class SpoolingDetector implements Detector {
        TikaInputStream tis;
        Path spooled;

        @Override
        public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
                throws IOException {
            this.tis = tis;
            this.spooled = tis.getPath();
            return MediaType.OCTET_STREAM;
        }
    }

    @Test
    public void testDetectClosesTheHandlesItOpenedOnItsSpoolFile() throws Exception {
        SpoolingDetector detector = new SpoolingDetector();
        new Tika(detector).detect(new ByteArrayInputStream(DATA), new Metadata());

        assertFalse(Files.exists(detector.spooled), "detect() must delete the file it spooled");
        assertThrows(IOException.class, () -> detector.tis.read(),
                "detect() must close the handles it opened on its spool file, not just delete it");
    }

    /** get() returns a caller-supplied TikaInputStream as-is, so disposing it here
     *  would delete the caller's temp file. */
    @Test
    public void testDetectDoesNotDisposeCallerOwnedTikaInputStream() throws Exception {
        Tika tika = new Tika();
        try (TikaInputStream tis = TikaInputStream.get(DATA)) {
            Path spooled = tis.getPath();
            assertTrue(Files.exists(spooled), "precondition: caller spooled a temp file");

            tika.detect(tis, new Metadata());

            assertTrue(Files.exists(spooled),
                    "detect() must not dispose a caller-owned TikaInputStream's temp file");
            assertTrue(tis.getPath().toFile().exists(), "the stream must still be usable");
        }
    }
}
