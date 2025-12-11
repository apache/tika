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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.EmptyTranslator;

/**
 * Junit test class for translator configuration via JSON.
 */
public class TikaTranslatorConfigTest {

    private TikaLoader getLoader(String config) throws Exception {
        Path path = Paths.get(TikaTranslatorConfigTest.class.getResource(config).toURI());
        return TikaLoader.load(path);
    }

    @Test
    public void testDefaultBehaviour() throws Exception {
        TikaLoader loader = TikaLoader.loadDefault();
        assertNotNull(loader.loadTranslator());
        assertEquals(DefaultTranslator.class, loader.loadTranslator().getClass());
    }

    @Test
    public void testRequestsDefault() throws Exception {
        TikaLoader loader = getLoader("TIKA-1702-translator-default.json");
        assertNotNull(loader.loadParsers());
        assertNotNull(loader.loadDetectors());
        assertNotNull(loader.loadTranslator());

        assertEquals(DefaultTranslator.class, loader.loadTranslator().getClass());
    }

    @Test
    public void testRequestsEmpty() throws Exception {
        TikaLoader loader = getLoader("TIKA-1702-translator-empty.json");
        assertNotNull(loader.loadParsers());
        assertNotNull(loader.loadDetectors());
        assertNotNull(loader.loadTranslator());

        assertEquals(EmptyTranslator.class, loader.loadTranslator().getClass());
    }
}
