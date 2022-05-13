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
package org.apache.tika.example;

import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.TikaConfigSerializer;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;

public class DumpTikaConfigExampleTest {
    private File configFile;

    @BeforeEach
    public void setUp() {
        try {
            configFile = File.createTempFile("tmp", ".xml");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create tmp file");
        }
    }

    @AfterEach
    public void tearDown() {
        if (configFile != null && configFile.exists()) {
            configFile.delete();
        }
        if (configFile != null && configFile.exists()) {
            throw new RuntimeException("Failed to clean up: " + configFile.getAbsolutePath());
        }
    }

    @Test
    public void testDump() throws Exception {
        DumpTikaConfigExample ex = new DumpTikaConfigExample();
        for (Charset charset : new Charset[]{UTF_8, UTF_16LE}) {
            for (TikaConfigSerializer.Mode mode : TikaConfigSerializer.Mode.values()) {
                Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), charset);
                TikaConfigSerializer
                        .serialize(TikaConfig.getDefaultConfig(), mode, writer, charset);
                writer.flush();
                writer.close();

                TikaConfig c = new TikaConfig(configFile);
                assertTrue(c.getParser() instanceof CompositeParser, c.getParser().toString());
                assertTrue(c.getDetector() instanceof CompositeDetector,
                        c.getDetector().toString());

                CompositeParser p = (CompositeParser) c.getParser();
                assertTrue(p.getParsers().size() > 130, "enough parsers?");

                CompositeDetector d = (CompositeDetector) c.getDetector();
                assertTrue(d.getDetectors().size() > 3, "enough detectors?");

                //just try to load it into autodetect to make sure no errors are thrown
                Parser auto = new AutoDetectParser(c);
                assertNotNull(auto);
            }
        }
    }

}
