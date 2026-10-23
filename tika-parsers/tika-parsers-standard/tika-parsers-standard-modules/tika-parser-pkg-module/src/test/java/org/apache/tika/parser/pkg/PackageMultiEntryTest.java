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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Every entry of a multi-entry archive must be parsed. The per-entry TikaInputStream wraps the
 * archive's own stream; closing it must not close the archive (cpio's stream refuses further
 * reads once closed, zip's silently returns no more entries).
 */
public class PackageMultiEntryTest extends AbstractPkgTest {

    @TempDir
    Path tmp;

    private static final String[] NAMES = {"a.txt", "b.txt", "c.txt"};

    private static byte[] body(String name) {
        return ("entry " + name + " body\n").repeat(64).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] cpio() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CpioArchiveOutputStream out = new CpioArchiveOutputStream(bos)) {
            for (String n : NAMES) {
                byte[] b = body(n);
                CpioArchiveEntry e = new CpioArchiveEntry(n);
                e.setSize(b.length);
                put(out, e, b);
            }
        }
        return bos.toByteArray();
    }

    private static byte[] ar() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ArArchiveOutputStream out = new ArArchiveOutputStream(bos)) {
            for (String n : NAMES) {
                byte[] b = body(n);
                put(out, new ArArchiveEntry(n, b.length), b);
            }
        }
        return bos.toByteArray();
    }

    private static byte[] tar() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(bos)) {
            for (String n : NAMES) {
                byte[] b = body(n);
                TarArchiveEntry e = new TarArchiveEntry(n);
                e.setSize(b.length);
                put(out, e, b);
            }
        }
        return bos.toByteArray();
    }

    private static byte[] zip() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
            for (String n : NAMES) {
                put(out, new ZipArchiveEntry(n), body(n));
            }
        }
        return bos.toByteArray();
    }

    private static <E extends org.apache.commons.compress.archivers.ArchiveEntry> void put(
            ArchiveOutputStream<E> out, E entry, byte[] b) throws IOException {
        out.putArchiveEntry(entry);
        out.write(b);
        out.closeArchiveEntry();
    }

    /** The module's test classpath has no text parser, so entries are checked by name and
     *  declared size rather than by content. */
    private void assertAllEntries(String ext, byte[] archive) throws Exception {
        Path p = tmp.resolve("multi." + ext);
        Files.write(p, archive);
        List<Metadata> list = getRecursiveMetadata(p, false);
        assertEquals(NAMES.length + 1, list.size(), ext + ": container + one per entry");
        assertNull(list.get(0).get(TikaCoreProperties.EMBEDDED_EXCEPTION), ext);
        for (int i = 0; i < NAMES.length; i++) {
            Metadata m = list.get(i + 1);
            assertEquals(NAMES[i], m.get(TikaCoreProperties.RESOURCE_NAME_KEY), ext);
            assertEquals(Integer.toString(body(NAMES[i]).length), m.get(HttpHeaders.CONTENT_LENGTH),
                    ext + ": declared size of " + NAMES[i]);
        }
    }

    @Test
    public void testCpio() throws Exception {
        assertAllEntries("cpio", cpio());
    }

    @Test
    public void testAr() throws Exception {
        assertAllEntries("ar", ar());
    }

    @Test
    public void testTar() throws Exception {
        assertAllEntries("tar", tar());
    }

    @Test
    public void testZip() throws Exception {
        assertAllEntries("zip", zip());
    }
}
