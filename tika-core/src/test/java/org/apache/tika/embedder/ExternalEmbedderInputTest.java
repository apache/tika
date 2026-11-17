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
package org.apache.tika.embedder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;

public class ExternalEmbedderInputTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testInputIsGivenAsInputInItsOwnDirectory(@TempDir Path dir) throws Exception {
        Path doc = Files.write(dir.resolve("report.txt"), "hello".getBytes(UTF_8));
        ExternalEmbedder embedder = new ExternalEmbedder();
        embedder.setCommand("echo", ExternalParser.INPUT_FILE_TOKEN);
        embedder.setMetadataCommandArguments(null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TikaInputStream tis = TikaInputStream.get(doc)) {
            embedder.embed(new Metadata(), tis, out, new ParseContext());
        }
        Path given = Paths.get(out.toString(UTF_8).trim());
        assertEquals("input", given.getFileName().toString().replaceFirst("\\..*", ""));
        assertFalse(given.startsWith(dir));
        assertFalse(Files.exists(given.getParent()));
    }
}
