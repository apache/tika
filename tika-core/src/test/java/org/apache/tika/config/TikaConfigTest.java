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

import java.net.URL;

import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;

public class TikaConfigTest extends TestCase {

    /**
     * Make sure that a configuration file can't reference the
     * {@link AutoDetectParser} class a &lt;parser&gt; configuration element.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-866">TIKA-866</a>
     */
    public void testInvalidParser() throws Exception {
        URL url = TikaConfigTest.class.getResource("TIKA-866-invalid.xml");
        System.setProperty("tika.config", url.toExternalForm());
        try {
            new TikaConfig();
            fail("AutoDetectParser allowed in a <parser> element");
        } catch (TikaException expected) {
        } finally {
            System.clearProperty("tika.config");
        }
    }

    /**
     * Make sure that a configuration file can reference also a composite
     * parser class like {@link DefaultParser} in a &lt;parser&gt;
     * configuration element.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-866">TIKA-866</a>
     */
    public void testCompositeParser() throws Exception {
        URL url = TikaConfigTest.class.getResource("TIKA-866-composite.xml");
        System.setProperty("tika.config", url.toExternalForm());
        try {
            new TikaConfig();
        } catch (TikaException e) {
            fail("Unexpected TikaException: " + e);
        } finally {
            System.clearProperty("tika.config");
        }
    }

    /**
     * Make sure that a valid configuration file without mimetypes or
     * detector entries can be loaded without problems.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-866">TIKA-866</a>
     */
    public void testValidParser() throws Exception {
        URL url = TikaConfigTest.class.getResource("TIKA-866-valid.xml");
        System.setProperty("tika.config", url.toExternalForm());
        try {
            new TikaConfig();
        } catch (TikaException e) {
            fail("Unexpected TikaException: " + e);
        } finally {
            System.clearProperty("tika.config");
        }
    }

}