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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.EmptyTranslator;

/**
 * Junit test class for {@link TikaConfig}, which cover things
 * that {@link TikaConfigTest} can't do due to a need for the
 * full set of translators
 */
public class TikaTranslatorConfigTest extends AbstractTikaConfigTest {

    @Test
    public void testDefaultBehaviour() throws Exception {
        TikaConfig config = TikaConfig.getDefaultConfig();
        assertNotNull(config.getTranslator());
        assertEquals(DefaultTranslator.class, config.getTranslator().getClass());
    }

    @Test
    public void testRequestsDefault() throws Exception {
        TikaConfig config = getConfig("TIKA-1702-translator-default.xml");
        assertNotNull(config.getParser());
        assertNotNull(config.getDetector());
        assertNotNull(config.getTranslator());

        assertEquals(DefaultTranslator.class, config.getTranslator().getClass());
    }

    @Test
    public void testRequestsEmpty() throws Exception {
        TikaConfig config = getConfig("TIKA-1702-translator-empty.xml");
        assertNotNull(config.getParser());
        assertNotNull(config.getDetector());
        assertNotNull(config.getTranslator());

        assertEquals(EmptyTranslator.class, config.getTranslator().getClass());
    }

    /**
     * Currently, Translators don't support Composites, so
     * if multiple translators are given, throw a TikaConfigException
     */
    @Test
    public void testRequestsMultiple() throws Exception {
        assertThrows(TikaConfigException.class, () -> {
            TikaConfig config = getConfig("TIKA-1702-translator-empty-default.xml");
        });
    }
}
