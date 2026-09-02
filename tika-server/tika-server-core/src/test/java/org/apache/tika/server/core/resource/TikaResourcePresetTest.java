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
package org.apache.tika.server.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.server.core.ServerStatus;

public class TikaResourcePresetTest {

    private static final String CONFIG = """
            {
              "presets": {
                "xml-content": {"basic-content-handler-factory": {"type": "XML"}}
              }
            }
            """;

    @TempDir
    Path tmp;

    private TikaResource newTikaResource(String configJson, boolean allowPerRequestConfig)
            throws Exception {
        Path configPath = tmp.resolve("tika-config-" + configJson.hashCode() + ".json");
        Files.writeString(configPath, configJson);
        return new TikaResource(TikaLoader.load(configPath), new ServerStatus(), null,
                allowPerRequestConfig);
    }

    @Test
    public void testPresetExpandsIntoRequestContext() throws Exception {
        ParseContext context =
                newTikaResource(CONFIG, true).createPresetContext("xml-content");
        BasicContentHandlerFactory chf =
                (BasicContentHandlerFactory) context.get(ContentHandlerFactory.class);
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.XML, chf.getType());
    }

    @Test
    public void testPresetWorksWithPerRequestConfigDisabled() throws Exception {
        // presets are admin/Tika-vetted: selecting one must not require the
        // free-form per-request-config privilege
        ParseContext context =
                newTikaResource(CONFIG, false).createPresetContext("xml-content");
        BasicContentHandlerFactory chf =
                (BasicContentHandlerFactory) context.get(ContentHandlerFactory.class);
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.XML, chf.getType());
    }

    @Test
    public void testUnknownPresetIs404() throws Exception {
        TikaResource resource = newTikaResource(CONFIG, true);
        assertThrows(NotFoundException.class, () -> resource.createPresetContext("nope"));
    }

    @Test
    public void testInvalidPresetsConfigFailsStartup() {
        assertThrows(IllegalStateException.class,
                () -> newTikaResource("{\"presets\": {\"bad\": \"a string\"}}", true));
    }
}
