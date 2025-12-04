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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class TikaConfigAsyncWriterTest {


    @Test
    public void testBasic(@TempDir Path dir) throws Exception {
        Path p = Paths.get(TikaConfigAsyncWriter.class.getResource("/configs/TIKA-4508-parsers.json").toURI());
        SimpleAsyncConfig simpleAsyncConfig = new SimpleAsyncConfig("input", "output", 4,
                10000L, "-Xmx1g", null,
                p.toAbsolutePath().toString().replace("\\", "/"),
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, false, null);

        PluginsWriter pluginsWriter = new PluginsWriter(simpleAsyncConfig, null);

        Path tmp = Files.createTempFile(dir, "plugins-",".json");
        pluginsWriter.write(tmp);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tmp);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        assertEquals("-Xmx1g", pipesConfig.getForkedJvmArgs().get(0));
    }

}
