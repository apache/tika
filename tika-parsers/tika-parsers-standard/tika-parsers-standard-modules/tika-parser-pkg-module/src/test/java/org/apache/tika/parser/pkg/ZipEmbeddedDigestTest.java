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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.digest.InputStreamDigester;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Digest-then-parse round trip over zip entries: with a DigesterFactory in the context,
 * every embedded entry must get a correct digest AND its content must still be extracted
 * afterward (the re-openable entry source rewinds by re-opening the entry).
 */
public class ZipEmbeddedDigestTest extends AbstractPkgTest {

    private static final String DIGEST_KEY = "tk:digest:MD5";

    @TempDir
    Path tmp;

    private static byte[] entryBytes(String name, int size) {
        byte[] data = new byte[size];
        byte[] seed = name.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (seed[i % seed.length] + i);
        }
        return data;
    }

    /** name -> content; includes a nested zip to exercise embedded random access. */
    private Map<String, byte[]> buildEntries() throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        entries.put("small.txt", "hello embedded world".getBytes(StandardCharsets.UTF_8));
        entries.put("bigger.dat", entryBytes("bigger.dat", 300_000));
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(inner)) {
            zos.putNextEntry(new ZipEntry("inner.txt"));
            zos.write("nested entry".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        entries.put("nested.zip", inner.toByteArray());
        return entries;
    }

    private Path buildZip(Map<String, byte[]> entries) throws Exception {
        Path zip = Files.createTempFile(tmp, "digest-test", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return zip;
    }

    private static String md5(byte[] data) throws Exception {
        return Hex.encodeHexString(MessageDigest.getInstance("MD5").digest(data));
    }

    private ParseContext digestContext(CacheMemoryBudget budget) {
        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, () -> new InputStreamDigester(
                "MD5", DIGEST_KEY, Hex::encodeHexString));
        if (budget != null) {
            context.set(CacheMemoryBudget.class, budget);
        }
        return context;
    }

    private void assertDigestsAndContent(List<Metadata> metadataList,
                                         Map<String, byte[]> entries) throws Exception {
        Map<String, Metadata> byName = new HashMap<>();
        for (Metadata m : metadataList) {
            String name = m.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (name != null) {
                byName.put(name, m);
            }
        }
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            Metadata m = byName.get(e.getKey());
            assertTrue(m != null, "missing embedded metadata for " + e.getKey());
            assertEquals(md5(e.getValue()), m.get(DIGEST_KEY),
                    "wrong digest for " + e.getKey());
        }
        // Parse-after-digest still works: the container's content lists its entries...
        String containerContent = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertTrue(containerContent != null && containerContent.contains("small.txt"),
                "container content missing after digest rewind");
        // ...and the nested zip was digested AND recursed into (its entry parsed at depth 2),
        // proving the rewind-after-digest of a zip entry yields parseable content
        Metadata inner = byName.get("inner.txt");
        assertTrue(inner != null, "nested zip entry not recursed after digest rewind");
        assertEquals(md5("nested entry".getBytes(StandardCharsets.UTF_8)),
                inner.get(DIGEST_KEY), "wrong digest for inner.txt");
    }

    @Test
    public void testEmbeddedDigestsWithoutBudget() throws Exception {
        Map<String, byte[]> entries = buildEntries();
        Path zip = buildZip(entries);
        List<Metadata> metadataList = getRecursiveMetadata(zip, digestContext(null), false);
        assertDigestsAndContent(metadataList, entries);
    }

    @Test
    public void testEmbeddedDigestsWithBudget() throws Exception {
        Map<String, byte[]> entries = buildEntries();
        Path zip = buildZip(entries);
        CacheMemoryBudget budget = new CacheMemoryBudget(64L * 1024 * 1024);
        List<Metadata> metadataList = getRecursiveMetadata(zip, digestContext(budget), false);
        assertDigestsAndContent(metadataList, entries);
        assertEquals(0, budget.getReservedBytes(),
                "budget reservations must all be released after the parse");
    }

    @Test
    public void testEmbeddedDigestsWithTinyBudget() throws Exception {
        // an effectively exhausted budget must not break digests or extraction
        Map<String, byte[]> entries = buildEntries();
        Path zip = buildZip(entries);
        CacheMemoryBudget budget = new CacheMemoryBudget(1);
        List<Metadata> metadataList = getRecursiveMetadata(zip, digestContext(budget), false);
        assertDigestsAndContent(metadataList, entries);
        assertEquals(0, budget.getReservedBytes());
    }
}
