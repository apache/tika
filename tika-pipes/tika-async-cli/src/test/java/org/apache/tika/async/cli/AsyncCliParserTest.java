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
package org.apache.tika.async.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.sax.BasicContentHandlerFactory;

public class AsyncCliParserTest {

    @Test
    public void testBasic() throws Exception {
        // Simple two-argument form sets defaults
        SimpleAsyncConfig simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"input", "output"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertEquals(1, simpleAsyncConfig.getNumClients());
        assertEquals(30000L, simpleAsyncConfig.getTimeoutMs());
        assertEquals("-Xmx1g", simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"-o", "output", "-i", "input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"-output", "output", "-input", "input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"--output", "output", "--input", "input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());

        simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(new String[]{"--output=output", "--input=input"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertNull(simpleAsyncConfig.getNumClients());
        assertNull(simpleAsyncConfig.getTimeoutMs());
        assertNull(simpleAsyncConfig.getXmx());
    }

    @Test
    public void testAll() throws Exception {
        SimpleAsyncConfig simpleAsyncConfig = TikaAsyncCLI.parseCommandLine(
                new String[]{"-i", "input", "-o", "output", "-n", "5", "-T", "30000", "--Xmx", "1g", "--handler", "x"});
        assertEquals("input", simpleAsyncConfig.getInputDir());
        assertEquals("output", simpleAsyncConfig.getOutputDir());
        assertNull(simpleAsyncConfig.getFileList());
        assertEquals(5, simpleAsyncConfig.getNumClients());
        assertEquals(30000L, simpleAsyncConfig.getTimeoutMs());
        assertEquals("1g", simpleAsyncConfig.getXmx());
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.XML, simpleAsyncConfig.getHandlerType());
    }

    @Test
    public void testFileListWithInputDir(@TempDir Path tmp) throws Exception {
        Path fileList = tmp.resolve("files.txt");
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);
        Files.writeString(fileList, "doc1.pdf\ndoc2.pdf\n");

        SimpleAsyncConfig config = TikaAsyncCLI.parseCommandLine(
                new String[]{"-l", fileList.toString(), "-i", inputDir.toString(), "-o", "out"});
        assertEquals(fileList.toString(), config.getFileList());
        assertEquals(inputDir.toString(), config.getInputDir());
        assertEquals("out", config.getOutputDir());
    }

    @Test
    public void testFileListWithoutInputDir(@TempDir Path tmp) throws Exception {
        Path fileList = tmp.resolve("files.txt");
        Files.writeString(fileList, "/absolute/path/doc1.pdf\n");

        SimpleAsyncConfig config = TikaAsyncCLI.parseCommandLine(
                new String[]{"-l", fileList.toString(), "-o", "out"});
        assertEquals(fileList.toString(), config.getFileList());
        assertNull(config.getInputDir());
        assertEquals("out", config.getOutputDir());
    }

    @Test
    public void testFileListDefaultsOutputDir(@TempDir Path tmp) throws Exception {
        Path fileList = tmp.resolve("files.txt");
        Files.writeString(fileList, "doc1.pdf\n");

        SimpleAsyncConfig config = TikaAsyncCLI.parseCommandLine(
                new String[]{"-l", fileList.toString()});
        assertEquals(fileList.toString(), config.getFileList());
        assertNotNull(config.getOutputDir(), "outputDir should default when fileList is used");
    }

    @TempDir
    Path tempDir;

    @Test
    public void testEnsurePluginRootsAddsDefault() throws Exception {
        // Create a config without plugin-roots
        Path configPath = tempDir.resolve("config-no-plugins.json");
        Files.writeString(configPath, """
            {
              "pipes": {
                "numClients": 2
              }
            }
            """);

        // ensurePluginRoots should create a new config with plugin-roots added
        Path result = TikaAsyncCLI.ensurePluginRoots(configPath, null);

        // Should return a different path (merged config)
        assertFalse(result.equals(configPath), "Should create a new merged config");

        // The merged config should have plugin-roots
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.toFile());
        assertTrue(root.has("plugin-roots"), "Merged config should have plugin-roots");
        String pluginRoots = root.get("plugin-roots").asText();
        assertTrue(pluginRoots.equals("plugins") || pluginRoots.endsWith("/plugins"),
                "plugin-roots should be 'plugins' or end with '/plugins', got: " + pluginRoots);

        // Original config values should be preserved
        assertTrue(root.has("pipes"));
        assertEquals(2, root.get("pipes").get("numClients").asInt());

        // Clean up
        Files.deleteIfExists(result);
    }

    @Test
    public void testEnsurePluginRootsPreservesExisting() throws Exception {
        // Create a config with plugin-roots already set
        Path configPath = tempDir.resolve("config-with-plugins.json");
        Files.writeString(configPath, """
            {
              "plugin-roots": "/custom/plugins",
              "pipes": {
                "numClients": 4
              }
            }
            """);

        // ensurePluginRoots should return the original path (no merging needed)
        Path result = TikaAsyncCLI.ensurePluginRoots(configPath, null);

        // Should return the same path
        assertEquals(configPath, result, "Should return original config when plugin-roots exists");
    }

    @Test
    public void testEnsurePluginRootsUsesCommandLineOption() throws Exception {
        // Create a config without plugin-roots
        Path configPath = tempDir.resolve("config-no-plugins2.json");
        Files.writeString(configPath, """
            {
              "pipes": {
                "numClients": 2
              }
            }
            """);

        // ensurePluginRoots with a custom plugins dir
        Path result = TikaAsyncCLI.ensurePluginRoots(configPath, "/my/custom/plugins");

        // Should create a merged config with the custom plugins dir
        assertFalse(result.equals(configPath));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.toFile());
        assertTrue(root.has("plugin-roots"));
        assertEquals("/my/custom/plugins", root.get("plugin-roots").asText());

        // Clean up
        Files.deleteIfExists(result);
    }
}
