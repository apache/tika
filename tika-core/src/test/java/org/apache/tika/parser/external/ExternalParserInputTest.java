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
package org.apache.tika.parser.external;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.utils.ProcessUtils;

public class ExternalParserInputTest {

    /** The program is given the document under a fixed name in a directory of its own. */
    @Test
    public void testInputFileNameAndDirectory(@TempDir Path tmp)
            throws Exception {
        assumeTrue(ProcessUtils.checkCommand(new String[]{"sh", "-c", "exit 0"}));
        Path document = tmp.resolve("document.bin");
        Files.write(document, "content".getBytes(UTF_8));
        Files.write(tmp.resolve("other.bin"), "other".getBytes(UTF_8));

        ExternalParserConfig config = new ExternalParserConfig();
        config.setSupportedTypes(List.of("audio/vnd.wave"));
        config.setCommandLine(List.of("sh", "-c",
                "echo \"arg=$0\"; ls \"$(dirname \"$0\")\"; cat \"$0\"", "${INPUT_FILE}"));
        config.setReturnStdout(true);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "audio/vnd.wave");
        try (TikaInputStream tis = TikaInputStream.get(document)) {
            new ExternalParser(config).parse(tis, new BodyContentHandler(), metadata,
                    new ParseContext());
        }
        String stdout = metadata.get(ExternalProcess.STD_OUT);
        assertTrue(stdout.contains("content"), stdout);
        assertTrue(stdout.contains("/input.wav\n"), stdout);
        assertFalse(stdout.contains("document"), stdout);
        assertFalse(stdout.contains("other"), stdout);
        assertFalse(stdout.contains(tmp.toString()), stdout);
    }

    @Test
    public void testInputFileExtension() {
        ExternalParserConfig config = new ExternalParserConfig();
        assertNull(config.getInputFileExtension());
        config.setInputFileExtension(".wav");
        assertEquals(".wav", config.getInputFileExtension());
        config.setInputFileExtension("");
        assertEquals("", config.getInputFileExtension());
        assertThrows(IllegalArgumentException.class,
                () -> config.setInputFileExtension("a.b"));
        assertThrows(IllegalArgumentException.class, () -> config.setInputFileExtension("wav"));
    }
}
