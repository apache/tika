package org.apache.tika.example;

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


import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class DumpTikaConfigExampleTest {
    private File configFile;
    @Before
    public void setUp() {
        try {
            configFile = File.createTempFile("tmp", ".xml");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create tmp file");
        }
    }

    @After
    public void tearDown() {
        if (configFile != null && configFile.exists()) {
            configFile.delete();
        }
        if (configFile != null && configFile.exists()) {
            throw new RuntimeException("Failed to clean up: "+configFile.getAbsolutePath());
        }
    }

    @Test
    public void testDump() throws Exception {
        DumpTikaConfigExample ex = new DumpTikaConfigExample();
        for (String encoding : new String[]{ "UTF-8", "UTF-16LE"}) {
            Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), encoding);
            ex.dump(TikaConfig.getDefaultConfig(), writer, encoding);
            writer.flush();
            writer.close();

            TikaConfig c = new TikaConfig(configFile);
            assertEquals(CompositeParser.class, c.getParser().getClass());
            assertEquals(CompositeDetector.class, c.getDetector().getClass());

            CompositeParser p = (CompositeParser) c.getParser();
            assertTrue("enough parsers?", p.getParsers().size() > 130);

            CompositeDetector d = (CompositeDetector) c.getDetector();
            assertTrue("enough detectors?", d.getDetectors().size() > 3);
            //just try to load it into autodetect to make sure no errors are thrown
            Parser auto = new AutoDetectParser(c);
        }
    }

}
