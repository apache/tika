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
import java.util.List;
import java.util.Map;

import org.apache.tika.ResourceLoggingClassLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ErrorParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TikaConfigTest {

    /**
     * Make sure that a configuration file can't reference the
     * {@link AutoDetectParser} class a &lt;parser&gt; configuration element.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-866">TIKA-866</a>
     */
    @Test
    public void withInvalidParser() throws Exception {
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
    @Test
    public void asCompositeParser() throws Exception {
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
    @Test
    public void onlyValidParser() throws Exception {
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

    /**
     * TIKA-1145 If the TikaConfig has a ClassLoader set on it,
     * that should be used when loading the mimetypes and when
     * discovering services
     */
    @Test
    public void ensureClassLoaderUsedEverywhere() throws Exception {
        ResourceLoggingClassLoader customLoader = 
                new ResourceLoggingClassLoader(getClass().getClassLoader());
        TikaConfig config;
        
        // Without a classloader set, normal one will be used
        config = new TikaConfig();
        config.getMediaTypeRegistry();
        config.getParser();
        assertEquals(0, customLoader.getLoadedResources().size());
        
        // With a classloader set, resources will come through it
        config = new TikaConfig(customLoader);
        config.getMediaTypeRegistry();
        config.getParser();
        
        Map<String,List<URL>> resources = customLoader.getLoadedResources();
        int resourcesCount = resources.size();
        assertTrue(
                "Not enough things used the classloader, found only " + resourcesCount,
                resourcesCount > 3
        );
        
        // Ensure everything that should do, did use it
        // - Parsers
        assertNotNull(resources.get("META-INF/services/org.apache.tika.parser.Parser"));
        // - Detectors
        assertNotNull(resources.get("META-INF/services/org.apache.tika.detect.Detector"));
        // - Built-In Mimetypes
        assertNotNull(resources.get("org/apache/tika/mime/tika-mimetypes.xml"));
        // - Custom Mimetypes
        assertNotNull(resources.get("org/apache/tika/mime/custom-mimetypes.xml"));
    }
    
    /**
     * TIKA-1445 It should be possible to exclude DefaultParser from
     *  certain types, so another parser explicitly listed will take them
     */
    @Test
    public void defaultParserWithExcludes() throws Exception {
        URL url = TikaConfigTest.class.getResource("TIKA-1445-default-except.xml");
        System.setProperty("tika.config", url.toExternalForm());
        try {
            TikaConfig config = new TikaConfig();
            
            CompositeParser cp = (CompositeParser)config.getParser();
            List<Parser> parsers = cp.getAllComponentParsers();
            Parser p;
            
            // Will be the three parsers defined in the xml
            assertEquals(3, parsers.size());
            
            // Should have a wrapped DefaultParser, not the main DefaultParser,
            //  as it is excluded from handling certain classes
            p = parsers.get(0);
            assertTrue(p.toString(), p instanceof ParserDecorator);
            assertEquals(DefaultParser.class, ((ParserDecorator)p).getWrappedParser().getClass());
            
            // Should have two others which claim things, which they wouldn't
            //  otherwise handle
            p = parsers.get(1);
            assertTrue(p.toString(), p instanceof ParserDecorator);
            assertEquals(EmptyParser.class, ((ParserDecorator)p).getWrappedParser().getClass());
            assertEquals("hello/world", p.getSupportedTypes(null).iterator().next().toString());
            
            p = parsers.get(2);
            assertTrue(p.toString(), p instanceof ParserDecorator);
            assertEquals(ErrorParser.class, ((ParserDecorator)p).getWrappedParser().getClass());
            assertEquals("fail/world", p.getSupportedTypes(null).iterator().next().toString());
        } catch (TikaException e) {
            fail("Unexpected TikaException: " + e);
        } finally {
            System.clearProperty("tika.config");
        }
    }
}