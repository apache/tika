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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.CorruptedFileException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * A corrupt Access database must surface as a plain TikaException. CorruptedFileException is
 * reserved for "stop everything": thrown by an embedded document it aborts the whole container,
 * and a bad .mdb inside an archive must not cost the archive's remaining entries (TIKA-4830).
 */
public class JackcessCorruptTest extends TikaTest {

    private byte[] original() throws Exception {
        try (InputStream is = getResourceAsStream("/test-documents/testAccess2_2000.mdb")) {
            return is.readAllBytes();
        }
    }

    private void assertPlainTikaException(byte[] data) {
        TikaException e = assertThrows(TikaException.class, () -> {
            try (TikaInputStream tis = TikaInputStream.get(data)) {
                new JackcessParser().parse(tis, new DefaultHandler(), new Metadata(),
                        new ParseContext());
            }
        });
        assertFalse(e instanceof CorruptedFileException, "must not abort a container: " + e);
        assertTrue(e.getMessage().startsWith("Corrupt Access database"), e.getMessage());
    }

    @Test
    public void indexOutOfBoundsBecomesTikaException() throws Exception {
        // page pointers after the header page overwritten: Jackcess throws IndexOutOfBounds
        byte[] data = original();
        Arrays.fill(data, 4096 + 8, 4096 + 8 + 64, (byte) 0xff);
        assertPlainTikaException(data);
    }

    @Test
    public void invalidPageNumberBecomesTikaException() throws Exception {
        // truncated to three pages: Jackcess throws IllegalStateException("invalid page number")
        byte[] data = Arrays.copyOf(original(), 4096 * 3);
        assertPlainTikaException(data);
    }
}
