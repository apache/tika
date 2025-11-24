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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Unit tests for TikaLoader JSON configuration loading.
 */
public class TikaLoaderTest {

    @Test
    public void testBasicParserLoading() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        assertNotNull(configUrl, "Test config not found");

        Path configPath = Path.of(configUrl.toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        Parser parser = loader.loadParsers();
        assertNotNull(parser, "Parser should not be null");
    }

    @Test
    public void testConfigurableParserConfiguration() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.loadParsers();

        // Parse with the composite parser to verify config was applied
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+configurable");

        try (InputStream stream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        // Verify the configured values were used
        assertEquals("configured-parser", metadata.get("parser-name"));
        assertEquals("2048", metadata.get("buffer-size"));
        assertEquals("true", metadata.get("enabled"));
        assertEquals("advanced", metadata.get("mode"));
    }

    @Test
    public void testMimeTypeDecoration() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-decoration-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser parser = loader.loadParsers();

        ParseContext context = new ParseContext();

        // Test that included types are supported
        assertTrue(parser.getSupportedTypes(context).contains(MediaType.parse("application/pdf")),
                "Should support application/pdf");
        assertTrue(parser.getSupportedTypes(context).contains(MediaType.parse("text/plain")),
                "Should support text/plain");
    }

    @Test
    public void testLazyLoading() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);

        // Verify loader created but parsers not yet loaded
        assertNotNull(loader, "Loader should be created");

        // Load parsers
        Parser parser1 = loader.loadParsers();
        assertNotNull(parser1, "First load should return parser");

        // Load again - should return cached instance
        Parser parser2 = loader.loadParsers();
        assertTrue(parser1 == parser2, "Should return same cached instance");
    }

    @Test
    public void testMinimalParser() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.loadParsers();

        // Parse with minimal parser type
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+minimal");

        try (InputStream stream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        // Verify minimal parser was invoked
        assertEquals("minimal", metadata.get("parser-type"));
    }

    @Test
    public void testFallbackConfiguration() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.loadParsers();

        // Parse with fallback parser type
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+fallback");

        try (InputStream stream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        // Verify fallback parser was invoked with correct config
        assertEquals("success", metadata.get("fallback-parser"));
        assertEquals("primary parser", metadata.get("message"));
    }

    @Test
    public void testNoDuplicateParsersFromSpi() throws Exception {
        // Config explicitly configures ConfigurableTestParser but not the others
        URL configUrl = getClass().getResource("/configs/test-no-duplicate-parsers.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.loadParsers();

        // Parse with ConfigurableTestParser - should use the explicitly configured instance
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+configurable");

        try (InputStream stream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        }

        // Verify it used the configured instance (with "explicitly-configured" name)
        // NOT the SPI instance (which would have "default" name from zero-arg constructor)
        assertEquals("explicitly-configured", metadata.get("parser-name"));
        assertEquals("4096", metadata.get("buffer-size"));

        // Verify other parsers (FallbackTestParser, MinimalTestParser) are still available via SPI
        Metadata fallbackMetadata = new Metadata();
        fallbackMetadata.set(Metadata.CONTENT_TYPE, "application/test+fallback");

        try (InputStream stream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(stream, new DefaultHandler(), fallbackMetadata, new ParseContext());
        }

        // FallbackTestParser should be loaded from SPI with default config
        assertEquals("success", fallbackMetadata.get("fallback-parser"));
        assertEquals("default message", fallbackMetadata.get("message"));
    }

    @Test
    public void testWithDefaultParserLoadsSpiParsers() throws Exception {
        // Config has "default-parser" so should load SPI parsers
        URL configUrl = getClass().getResource("/configs/test-with-default-parser.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.loadParsers();

        // Verify ConfigurableTestParser uses the configured instance
        Metadata configurableMetadata = new Metadata();
        configurableMetadata.set(Metadata.CONTENT_TYPE, "application/test+configurable");

        try (InputStream stream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(stream, new DefaultHandler(), configurableMetadata, new ParseContext());
        }

        assertEquals("with-default-config", configurableMetadata.get("parser-name"));
        assertEquals("1024", configurableMetadata.get("buffer-size"));

        // Verify FallbackTestParser was loaded from SPI
        Metadata fallbackMetadata = new Metadata();
        fallbackMetadata.set(Metadata.CONTENT_TYPE, "application/test+fallback");

        try (InputStream stream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(stream, new DefaultHandler(), fallbackMetadata, new ParseContext());
        }

        // FallbackTestParser should be loaded from SPI with default config
        assertEquals("success", fallbackMetadata.get("fallback-parser"));
    }

    @Test
    public void testWithoutDefaultParserSkipsSpiParsers() throws Exception {
        // Config does NOT have "default-parser" so should only load configured parsers
        URL configUrl = getClass().getResource("/configs/test-no-spi-fallback.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.loadParsers();

        ParseContext context = new ParseContext();

        // Verify ConfigurableTestParser is supported (explicitly configured)
        assertTrue(compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+configurable")),
                "Should support application/test+configurable");

        // Verify FallbackTestParser is NOT supported (not configured, SPI skipped)
        assertTrue(!compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+fallback")),
                "Should NOT support application/test+fallback");

        // Verify MinimalTestParser is NOT supported (not configured, SPI skipped)
        assertTrue(!compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+minimal")),
                "Should NOT support application/test+minimal");
    }
}
