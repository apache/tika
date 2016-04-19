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


import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.Ignore;
import org.junit.Test;

public class TikaConfigSerializerTest extends TikaConfigTest {

    /**
     * TIKA-1445 It should be possible to exclude DefaultParser from
     *  certain types, so another parser explicitly listed will take them
     */
    @Test
    public void defaultParserWithExcludes() throws Exception {
        String xml = loadAndSerialize("TIKA-1445-default-except.xml",
                TikaConfigSerializer.Mode.STATIC);
        assertContains(
                "<parser class=\"org.apache.tika.parser.ErrorParser\">" +
                " <mime>fail/world</mime> " +
                "</parser>", xml);
    }

    @Test
    @Ignore("TODO: executor-service info needs to be stored in TikaConfig for serialization")
    public void testExecutors() throws Exception {
        String xml = loadAndSerialize("TIKA-1762-executors.xml",
                TikaConfigSerializer.Mode.STATIC);
        assertContains("<executor-service class=\"org.apache.tika.config.DummyExecutor\">" +
                " <core-threads>3</core-threads>" +
                " <max-threads>10</max-threads>" +
                "</executor-service>", xml);
    }

    String loadAndSerialize(String configFile, TikaConfigSerializer.Mode mode) throws Exception {
        TikaConfig config = getConfig(configFile);
        StringWriter writer = new StringWriter();
        TikaConfigSerializer.serialize(config, mode, writer, StandardCharsets.UTF_8);
        return writer.toString().replaceAll("[\r\n\t ]+", " ");
    }
}
