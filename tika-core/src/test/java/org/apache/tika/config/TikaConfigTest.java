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

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.DefaultParser;

public class TikaConfigTest extends TestCase {

    /**
     * Make sure that a configuration file can't reference to composite
     * parser classes like {@link DefaultParser} in the &lt;parser&gt;
     * configuration elements.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-866">TIKA-866</a>
     */
    public void testInvalidParser() throws Exception {
        InputStream xml = TikaConfigTest.class.getResourceAsStream(
                "TIKA-866-invalid.xml");
        try {
            new TikaConfig(xml);
            fail("Composite parser class was allowed in <parser>");
        } catch (TikaException expected) {
            // OK
        } finally {
            xml.close();
        }
    }

    /**
     * Make sure that a valid configuration file without mimetypes or
     * detector entries can be loaded without problems.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-866">TIKA-866</a>
     */
    public void testValidParser() throws Exception {
        InputStream xml = TikaConfigTest.class.getResourceAsStream(
                "TIKA-866-valid.xml");
        try {
            new TikaConfig(xml);
            // OK
        } finally {
            xml.close();
        }
    }

}