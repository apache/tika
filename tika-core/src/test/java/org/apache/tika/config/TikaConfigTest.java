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

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.tika.ResourceLoggingClassLoader;
import org.apache.tika.config.DummyExecutor;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.TikaConfigTest;
import org.apache.tika.exception.TikaConfigException;
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

/**
 * Tests for the Tika Config, which don't require real parsers /
 *  detectors / etc.
 * There's also {@link TikaParserConfigTest} and {@link TikaDetectorConfigTest}
 *  over in the Tika Parsers project, which do further Tika Config
 *  testing using real parsers and detectors.
 */
public class TikaConfigTest extends AbstractTikaConfigTest {
    /**
     * Make sure that a configuration file can't reference the
     * {@link AutoDetectParser} class a &lt;parser&gt; configuration element.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-866">TIKA-866</a>
     */
    @Test
    public void withInvalidParser() throws Exception {
        try {
            getConfig("TIKA-866-invalid.xml");
            fail("AutoDetectParser allowed in a <parser> element");
        } catch (TikaException expected) {}
    }
    
    /**
     * Make sure that with a service loader given, we can
     * get different configurable behaviour on parser classes
     * which can't be found.
     */
    @Test
    public void testUnknownParser() throws Exception {
        ServiceLoader ignoreLoader = new ServiceLoader(
                getClass().getClassLoader(), LoadErrorHandler.IGNORE);
        ServiceLoader warnLoader = new ServiceLoader(
                getClass().getClassLoader(), LoadErrorHandler.WARN);
        ServiceLoader throwLoader = new ServiceLoader(
                getClass().getClassLoader(), LoadErrorHandler.THROW);
        Path configPath = Paths.get(new URI(getConfigPath("TIKA-1700-unknown-parser.xml")));
        
        TikaConfig ignore = new TikaConfig(configPath, ignoreLoader);
        assertNotNull(ignore);
        assertNotNull(ignore.getParser());
        assertEquals(1, ((CompositeParser)ignore.getParser()).getAllComponentParsers().size());
        
        TikaConfig warn = new TikaConfig(configPath, warnLoader);
        assertNotNull(warn);
        assertNotNull(warn.getParser());
        assertEquals(1, ((CompositeParser)warn.getParser()).getAllComponentParsers().size());
        
        try {
            new TikaConfig(configPath, throwLoader);
            fail("Shouldn't get here, invalid parser class");
        } catch (TikaException expected) {}
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
        try {
            getConfig("TIKA-866-composite.xml");
        } catch (TikaException e) {
            fail("Unexpected TikaException: " + e);
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
        try {
            getConfig("TIKA-866-valid.xml");
        } catch (TikaException e) {
            fail("Unexpected TikaException: " + e);
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
        try {
            TikaConfig config = getConfig("TIKA-1445-default-except.xml");
            
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
        }
    }
    
    /**
     * TIKA-1653 If one parser has child parsers, those child parsers shouldn't
     *  show up at the top level as well
     */
    @Test
    public void parserWithChildParsers() throws Exception {
        try {
            TikaConfig config = getConfig("TIKA-1653-norepeat.xml");
            
            CompositeParser cp = (CompositeParser)config.getParser();
            List<Parser> parsers = cp.getAllComponentParsers();
            Parser p;
            
            // Just 2 top level parsers
            assertEquals(2, parsers.size());
            
            // Should have a CompositeParser with 2 child ones, and
            //  and a wrapped empty parser
            p = parsers.get(0);
            assertTrue(p.toString(), p instanceof CompositeParser);
            assertEquals(2, ((CompositeParser)p).getAllComponentParsers().size());
            
            p = parsers.get(1);
            assertTrue(p.toString(), p instanceof ParserDecorator);
            assertEquals(EmptyParser.class, ((ParserDecorator)p).getWrappedParser().getClass());
            assertEquals("hello/world", p.getSupportedTypes(null).iterator().next().toString());
        } catch (TikaException e) {
            fail("Unexpected TikaException: " + e);
        }
    }
    
    @Test
    public void testDynamicServiceLoaderFromConfig() throws Exception {
        URL url = TikaConfigTest.class.getResource("TIKA-1700-dynamic.xml");
        TikaConfig config = new TikaConfig(url);
        
        DummyParser parser = (DummyParser)config.getParser();

        ServiceLoader loader = parser.getLoader();
        boolean dynamicValue = loader.isDynamic();
        
        assertTrue("Dynamic Service Loading Should be true", dynamicValue);
    }
    
    @Test
    public void testTikaExecutorServiceFromConfig() throws Exception {
        URL url = TikaConfigTest.class.getResource("TIKA-1762-executors.xml");
        
        TikaConfig config = new TikaConfig(url);
        
        ThreadPoolExecutor executorService = (ThreadPoolExecutor)config.getExecutorService();
        
        assertTrue("Should use Dummy Executor", (executorService instanceof DummyExecutor));
        assertEquals("Should have configured Core Threads", 3, executorService.getCorePoolSize());
        assertEquals("Should have configured Max Threads", 10, executorService.getMaximumPoolSize());
    }

    @Test(expected = TikaConfigException.class)
    public void testInitializerBadValue() throws Exception {
        TikaConfig config = getConfig("TIKA-2389-illegal.xml");
    }


    @Test(expected = TikaConfigException.class)
    public void testInitializerPerParserThrow() throws Exception {
        TikaConfig config = getConfig("TIKA-2389-throw-per-parser.xml");
    }

    @Test(expected = TikaConfigException.class)
    public void testInitializerServiceLoaderThrow() throws Exception {
        TikaConfig config = getConfig("TIKA-2389-throw-default.xml");
    }

    @Test
    public void testInitializerServiceLoaderThrowButOverridden() throws Exception {
        //TODO: test that this was logged at INFO level
        TikaConfig config = getConfig("TIKA-2389-throw-default-overridden.xml");
    }


    @Test
    public void testInitializerPerParserWarn() throws Exception {
        //TODO: test that this was logged at WARN level
        TikaConfig config = getConfig("TIKA-2389-warn-per-parser.xml");
    }

}