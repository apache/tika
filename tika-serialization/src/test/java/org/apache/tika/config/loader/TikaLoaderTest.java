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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.io.TikaInputStream;
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

        Parser parser = loader.get(Parser.class);
        assertNotNull(parser, "Parser should not be null");
    }

    @Test
    public void testConfigurableParserConfiguration() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.get(Parser.class);

        // Parse with the composite parser to verify config was applied
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+configurable");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
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
        Parser parser = loader.get(Parser.class);

        ParseContext context = new ParseContext();

        // Test that included types are supported
        assertTrue(parser.getSupportedTypes(context).contains(MediaType.parse("application/pdf")),
                "Should support application/pdf");
        assertTrue(parser.getSupportedTypes(context).contains(MediaType.parse("text/plain")),
                "Should support text/plain");

        // Test that excluded types are not supported
        assertFalse(parser.getSupportedTypes(context).contains(MediaType.parse("application/pdf+fdf")),
                "Should NOT support application/pdf+fdf (excluded)");
    }

    @Test
    public void testLazyLoading() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);

        // Verify loader created but parsers not yet loaded
        assertNotNull(loader, "Loader should be created");

        // Load parsers
        Parser parser1 = loader.get(Parser.class);
        assertNotNull(parser1, "First load should return parser");

        // Load again - should return cached instance
        Parser parser2 = loader.get(Parser.class);
        assertTrue(parser1 == parser2, "Should return same cached instance");
    }

    @Test
    public void testMinimalParser() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.get(Parser.class);

        // Parse with minimal parser type
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+minimal");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        // Verify minimal parser was invoked
        assertEquals("minimal", metadata.get("parser-type"));
    }

    @Test
    public void testFallbackConfiguration() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.get(Parser.class);

        // Parse with fallback parser type
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+fallback");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
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
        Parser compositeParser = loader.get(Parser.class);

        // Parse with ConfigurableTestParser - should use the explicitly configured instance
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+configurable");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        // Verify it used the configured instance (with "explicitly-configured" name)
        // NOT the SPI instance (which would have "default" name from zero-arg constructor)
        assertEquals("explicitly-configured", metadata.get("parser-name"));
        assertEquals("4096", metadata.get("buffer-size"));

        // Verify other parsers (FallbackTestParser, MinimalTestParser) are still available via SPI
        Metadata fallbackMetadata = new Metadata();
        fallbackMetadata.set(Metadata.CONTENT_TYPE, "application/test+fallback");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), fallbackMetadata, new ParseContext());
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
        Parser compositeParser = loader.get(Parser.class);

        // Verify ConfigurableTestParser uses the configured instance
        Metadata configurableMetadata = new Metadata();
        configurableMetadata.set(Metadata.CONTENT_TYPE, "application/test+configurable");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), configurableMetadata, new ParseContext());
        }

        assertEquals("with-default-config", configurableMetadata.get("parser-name"));
        assertEquals("1024", configurableMetadata.get("buffer-size"));

        // Verify FallbackTestParser was loaded from SPI
        Metadata fallbackMetadata = new Metadata();
        fallbackMetadata.set(Metadata.CONTENT_TYPE, "application/test+fallback");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), fallbackMetadata, new ParseContext());
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
        Parser compositeParser = loader.get(Parser.class);

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

    @Test
    public void testDefaultParserWithExclusions() throws Exception {
        // Config has "default-parser" with exclude list
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.get(Parser.class);

        ParseContext context = new ParseContext();

        // Verify ConfigurableTestParser is supported (explicitly configured)
        assertTrue(compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+configurable")),
                "Should support application/test+configurable");

        // Verify MinimalTestParser is NOT supported (excluded via default-parser config)
        assertTrue(!compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+minimal")),
                "Should NOT support application/test+minimal (excluded)");

        // Verify FallbackTestParser is NOT supported (excluded via default-parser config)
        assertTrue(!compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+fallback")),
                "Should NOT support application/test+fallback (excluded)");
    }

    @Test
    public void testOptInParserExplicitLoad() throws Exception {
        // Config explicitly loads opt-in parser (spi=false)
        URL configUrl = getClass().getResource("/configs/test-opt-in-parser-explicit.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.get(Parser.class);

        // Parse with the opt-in parser
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/test+optin");

        try (TikaInputStream tis = TikaInputStream.get("test".getBytes(StandardCharsets.UTF_8))) {
            compositeParser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        // Verify opt-in parser was loaded
        assertEquals("opt-in", metadata.get("parser-type"));
        assertEquals("success", metadata.get("opt-in-parser"));
    }

    @Test
    public void testOptInParserNotLoadedBySpi() throws Exception {
        // Config uses default-parser - should NOT load opt-in parser (spi=false)
        URL configUrl = getClass().getResource("/configs/test-opt-in-parser-with-default.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        Parser compositeParser = loader.get(Parser.class);

        ParseContext context = new ParseContext();

        // Verify regular SPI parsers are supported
        assertTrue(compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+configurable")),
                "Should support application/test+configurable (SPI)");

        // Verify opt-in parser is NOT supported (spi=false, not explicitly configured)
        assertTrue(!compositeParser.getSupportedTypes(context)
                        .contains(MediaType.parse("application/test+optin")),
                "Should NOT support application/test+optin (opt-in only, not in SPI)");
    }

    @Test
    public void testLoadConfigWithDefaults() throws Exception {
        // Test the loadConfig method that merges JSON config with defaults
        URL configUrl = getClass().getResource("/configs/embedded-limits-test.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);

        // Create defaults - some values will be overridden by JSON, others kept
        EmbeddedLimits defaults = new EmbeddedLimits();
        // Default values from EmbeddedLimits: maxDepth=UNLIMITED, maxCount=UNLIMITED, throwOnMax*=false

        // Load with defaults - JSON has: maxDepth=5, throwOnMaxDepth=true, maxCount=100, throwOnMaxCount=false
        EmbeddedLimits config = loader.loadConfig(EmbeddedLimits.class, defaults);

        assertNotNull(config, "Config should not be null");
        assertEquals(5, config.getMaxDepth(), "maxDepth should be from JSON");
        assertTrue(config.isThrowOnMaxDepth(), "throwOnMaxDepth should be from JSON");
        assertEquals(100, config.getMaxCount(), "maxCount should be from JSON");
        assertFalse(config.isThrowOnMaxCount(), "throwOnMaxCount should be from JSON");

        // Verify original defaults object was NOT modified
        assertEquals(EmbeddedLimits.UNLIMITED, defaults.getMaxDepth(), "Original defaults should be unchanged");
    }

    @Test
    public void testLoadConfigMissingKeyReturnsDefaults() throws Exception {
        // Test that loadConfig returns defaults when key is not in config
        URL configUrl = getClass().getResource("/configs/test-loader-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);

        // Create defaults
        EmbeddedLimits defaults = new EmbeddedLimits(10, true, 500, false);

        // Load with defaults - this config doesn't have embedded-limits
        EmbeddedLimits config = loader.loadConfig(EmbeddedLimits.class, defaults);

        // Should return the defaults since key is missing
        assertEquals(10, config.getMaxDepth(), "Should return defaults when key missing");
        assertTrue(config.isThrowOnMaxDepth(), "Should return defaults when key missing");
        assertEquals(500, config.getMaxCount(), "Should return defaults when key missing");
        assertFalse(config.isThrowOnMaxCount(), "Should return defaults when key missing");
    }

    // TODO: TIKA-SERIALIZATION-FOLLOWUP - Jackson may need configuration to fail on unknown properties
    @Disabled("TIKA-SERIALIZATION-FOLLOWUP")
    @Test
    public void testInvalidBeanPropertyThrowsException() throws Exception {
        // Config with a property that doesn't exist on DefaultDetector
        String invalidConfig = """
                {
                  "detectors": [
                    {
                      "default-detector": {
                        "nonExistentProperty": 12345
                      }
                    }
                  ]
                }
                """;

        Path tempFile = Files.createTempFile("test-invalid-property", ".json");
        try {
            Files.write(tempFile, invalidConfig.getBytes(StandardCharsets.UTF_8));

            TikaLoader loader = TikaLoader.load(tempFile);
            try {
                loader.loadDetectors();
                throw new AssertionError("Expected TikaConfigException for invalid property");
            } catch (org.apache.tika.exception.TikaConfigException e) {
                // Expected - Jackson should fail on unknown property
                assertTrue(e.getMessage().contains("nonExistentProperty") ||
                                e.getCause().getMessage().contains("nonExistentProperty"),
                        "Error should mention the invalid property name");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

}
