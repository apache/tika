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
package org.apache.tika.async.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class TikaConfigAsyncWriterTest {


    @Test
    public void testBasic(@TempDir Path dir) throws Exception {
        Path p = Paths.get(TikaConfigAsyncWriter.class.getResource("/configs/TIKA-4508-parsers.json").toURI());
        SimpleAsyncConfig simpleAsyncConfig = new SimpleAsyncConfig("input", "output", 4,
                10000L, "-Xmx1g", null,
                p.toAbsolutePath().toString().replace("\\", "/"),
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, SimpleAsyncConfig.ExtractBytesMode.NONE, null);

        PluginsWriter pluginsWriter = new PluginsWriter(simpleAsyncConfig, null);

        Path tmp = Files.createTempFile(dir, "plugins-",".json");
        pluginsWriter.write(tmp);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tmp);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        assertEquals("-Xmx1g", pipesConfig.getForkedJvmArgs().get(0));
    }

    // TIKA-4735: --content-only --handler m must produce parseMode=CONTENT_ONLY and fileExtension=md
    @Test
    public void testTIKA4735_ContentOnlyMarkdownConfig(@TempDir Path dir) throws Exception {
        SimpleAsyncConfig simpleAsyncConfig = new SimpleAsyncConfig("input", "output", 1,
                30000L, "-Xmx1g", null, null,
                BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN, SimpleAsyncConfig.ExtractBytesMode.NONE, null,
                true, true, null, null, false);

        PluginsWriter pluginsWriter = new PluginsWriter(simpleAsyncConfig, null);
        Path tmp = Files.createTempFile(dir, "plugins-content-only-", ".json");
        pluginsWriter.write(tmp);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tmp);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        assertEquals(ParseMode.CONTENT_ONLY, pipesConfig.getParseMode(),
                "TIKA-4735: parseMode must be CONTENT_ONLY when --content-only is set");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(tmp.toFile());
        JsonNode fileExt = root.at("/emitters/fse/file-system-emitter/fileExtension");
        assertEquals("md", fileExt.asText(),
                "TIKA-4735: file extension must be 'md' when --handler m and --content-only");
    }

    // TIKA-4735: --content-only without a handler should produce parseMode=CONTENT_ONLY and fileExtension=md (MARKDOWN default)
    @Test
    public void testTIKA4735_ContentOnlyDefaultMarkdownConfig(@TempDir Path dir) throws Exception {
        SimpleAsyncConfig simpleAsyncConfig = new SimpleAsyncConfig("input", "output", 1,
                30000L, "-Xmx1g", null, null,
                BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN, SimpleAsyncConfig.ExtractBytesMode.NONE, null,
                true, true, null, null, false);

        PluginsWriter pluginsWriter = new PluginsWriter(simpleAsyncConfig, null);
        Path tmp = Files.createTempFile(dir, "plugins-content-only-default-", ".json");
        pluginsWriter.write(tmp);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tmp);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        assertEquals(ParseMode.CONTENT_ONLY, pipesConfig.getParseMode(),
                "TIKA-4735: parseMode must be CONTENT_ONLY when --content-only is set");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(tmp.toFile());
        JsonNode fileExt = root.at("/emitters/fse/file-system-emitter/fileExtension");
        assertEquals("md", fileExt.asText(),
                "TIKA-4735: file extension must be 'md' for MARKDOWN handler with --content-only");
    }

}
