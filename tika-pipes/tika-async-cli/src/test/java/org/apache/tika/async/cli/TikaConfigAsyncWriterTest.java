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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.core.async.AsyncConfig;
import org.apache.tika.plugins.TikaConfigs;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.utils.XMLReaderUtils;

public class TikaConfigAsyncWriterTest {


    @Test
    public void testBasic(@TempDir Path dir) throws Exception {
        Path p = Paths.get(TikaConfigAsyncWriter.class.getResource("/configs/TIKA-4508-parsers.xml").toURI());
        SimpleAsyncConfig simpleAsyncConfig = new SimpleAsyncConfig("input", "output", 4,
                10000L, "-Xmx1g", null, p.toAbsolutePath().toString(), null,
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, false, null);
        PluginsWriter pluginsWriter = new PluginsWriter(simpleAsyncConfig, null);

        Path tmp = Files.createTempFile(dir, "plugins-",".json");
        pluginsWriter.write(tmp);
        TikaConfigs configs = TikaConfigs.load(tmp);
        AsyncConfig asyncConfig = AsyncConfig.load(configs);
        assertEquals("-Xmx1g", asyncConfig.getForkedJvmArgs().get(0));
    }

}
