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

package org.apache.tika.parser;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.junit.Test;

import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ParserExcludeTest {

    /**
     * TIKA-1558 It should be possible to exclude Parsers from being picked up by
     * DefaultParser.
     */
    @Test
    public void defaultParserBlacklist() throws Exception {
        // The default TikaConfig should discover two parsers (DummyParser and DummyParserSubclass)
        // in the META-INF services file.
        TikaConfig config = new TikaConfig();
        CompositeParser cp = (CompositeParser) config.getParser();
        List<Parser> parsers = cp.getAllComponentParsers();
        assertEquals("Should have two available parsers.", 2, parsers.size());

        // This custom TikaConfig should have the DefaultParser and its subclass excluded
        // (by only specifying DefaultParser).
        URL url = ParserExcludeTest.class.getResource("TIKA-1558-blacklist.xml");
        System.setProperty("tika.config", url.toExternalForm());
        try {
            config = new TikaConfig();
            cp = (CompositeParser) config.getParser();
            parsers = cp.getAllComponentParsers();
            assertEquals("Should have no available parsers.", 0, parsers.size());
        } catch (TikaException e) {
            fail("Unexpected TikaException: " + e);
        } finally {
            System.clearProperty("tika.config");
        }
    }
}
