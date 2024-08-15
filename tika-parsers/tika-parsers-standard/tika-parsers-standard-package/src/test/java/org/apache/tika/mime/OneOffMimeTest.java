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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class OneOffMimeTest extends TikaTest {

    @Disabled("use for development purposes on local files that " +
            "cannot be added to Tika's repo.")
    @Test
    public void testOne() throws Exception {
        Path baseDir = Paths.get("");
        Path p = baseDir.resolve("");
        String mime = "application/vnd.tcpdump.pcapng";
        assertByData(mime, p);
        assertByName(mime, p);
    }

    @Test
    @Disabled("again for development purposes with files that aren't suitable for the repo")
    public void testDir() throws Exception {
        Path root = Paths.get("");
        Tika tika = new Tika();
        for (File f : root.toFile().listFiles()) {
            String fileMime = tika.detect(f);
            String streamMime = "";
            try (InputStream is = Files.newInputStream(f.toPath())) {
                streamMime = tika.detect(is);
            }
            System.out.println(f.getName() + " fileMime=" + fileMime + " stream=" + streamMime);
        }
    }

    private void assertByName(String expected, Path p) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, p.getFileName().toString());
        assertEquals(expected,
                getRecursiveMetadata(UnsynchronizedByteArrayInputStream.builder().setByteArray(new byte[0]).get(),
                        metadata,
                        new ParseContext(), true).get(0).get(Metadata.CONTENT_TYPE));
    }

    private void assertByData(String expected, Path p) throws Exception {
        try (InputStream is = Files.newInputStream(p)) {
            List<Metadata> metadataList = getRecursiveMetadata(is, true);
            assertEquals(expected, metadataList.get(0).get(Metadata.CONTENT_TYPE));
        }
    }

}
