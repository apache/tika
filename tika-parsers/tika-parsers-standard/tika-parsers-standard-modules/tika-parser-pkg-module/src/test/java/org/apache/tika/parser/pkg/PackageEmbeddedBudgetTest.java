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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.digest.InputStreamDigester;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * An archive entry's in-memory cache reserves against the shared {@link CacheMemoryBudget}
 * when it is digested (rewind is enabled, the cache grows past the 1 MB threshold). That
 * reservation is released only when the entry's TikaInputStream is closed; disposing the
 * TemporaryResources alone does not reach it. Leaked reservations pin the budget at its
 * maximum after a few thousand documents, after which every cache spills to disk.
 */
public class PackageEmbeddedBudgetTest extends AbstractPkgTest {

    @TempDir
    Path tmp;

    /** Three entries over the 1 MB StreamCache threshold, so each one reserves budget. */
    private Path buildTar() throws Exception {
        Path tar = tmp.resolve("entries.tar");
        try (TarArchiveOutputStream out =
                     new TarArchiveOutputStream(Files.newOutputStream(tar))) {
            for (int i = 0; i < 3; i++) {
                byte[] data = new byte[2 * 1024 * 1024];
                for (int j = 0; j < data.length; j++) {
                    data[j] = (byte) ('a' + (j + i) % 26);
                }
                TarArchiveEntry entry = new TarArchiveEntry("entry-" + i + ".txt");
                entry.setSize(data.length);
                out.putArchiveEntry(entry);
                out.write(data);
                out.closeArchiveEntry();
            }
        }
        return tar;
    }

    @Test
    public void budgetIsReleasedAfterEachEntry() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(64L * 1024 * 1024);
        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, () -> new InputStreamDigester("MD5", "tk:digest:MD5",
                bytes -> Base64.getEncoder().encodeToString(bytes)));
        context.set(CacheMemoryBudget.class, budget);

        List<Metadata> metadataList = getRecursiveMetadata(buildTar(), context, false);

        assertEquals(4, metadataList.size(), "container plus three entries");
        for (int i = 1; i < metadataList.size(); i++) {
            assertNotNull(metadataList.get(i).get("tk:digest:MD5"), "entry " + i + " digested");
        }
        assertEquals(0, budget.getReservedBytes(),
                "every entry's in-memory cache must return its reservation on close");
    }
}
