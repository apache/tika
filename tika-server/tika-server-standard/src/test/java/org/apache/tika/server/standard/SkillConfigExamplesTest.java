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
package org.apache.tika.server.standard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.server.core.TikaServerConfig;

/**
 * The user skills ship in the source release and agents copy their configs verbatim: every one
 * must load in tika-app and tika-server.
 */
public class SkillConfigExamplesTest {

    private static final String SKILLS_RELATIVE_PATH = ".skills/users";

    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL);

    // a block with none of these is an output sample, not a config
    private static final Set<String> CONFIG_KEYS = Set.of("server", "parsers", "engines",
            "text-recognizers", "parse-context", "pipes", "detectors", "inference");

    @TempDir
    Path tempDir;

    @Test
    public void testUserSkillConfigsLoad() throws Exception {
        Map<String, String> configs = findConfigs();
        assertFalse(configs.isEmpty(), "no configs found under " + SKILLS_RELATIVE_PATH);
        List<String> failures = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> e : configs.entrySet()) {
            Path file = tempDir.resolve("config-" + i++ + ".json");
            Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
            try {
                TikaLoader.load(file).loadParsers();
                CommandLine cl = new DefaultParser().parse(
                        new Options().addOption("c", true, ""), new String[] {"-c", file.toString()});
                TikaServerConfig.load(cl);
            } catch (Exception ex) {
                failures.add(e.getKey() + ": " + ex.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), "skill configs that fail to load:\n" + String.join("\n", failures));
    }

    private static Map<String, String> findConfigs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path root = findSkillsDir();
        Map<String, String> configs = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = root.relativize(p).toString();
                if (!name.endsWith(".json") && !name.endsWith(".md")) {
                    continue;
                }
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (name.endsWith(".json")) {
                    configs.put(name, text);
                } else if (name.endsWith(".md")) {
                    Matcher m = JSON_BLOCK.matcher(text);
                    while (m.find()) {
                        JsonNode node = mapper.readTree(m.group(1));
                        if (node.isObject() && CONFIG_KEYS.stream().anyMatch(node::has)) {
                            int line = text.substring(0, m.start()).split("\n", -1).length;
                            configs.put(name + ":" + line, m.group(1));
                        }
                    }
                }
            }
        }
        return configs;
    }

    private static Path findSkillsDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(SKILLS_RELATIVE_PATH);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "could not locate " + SKILLS_RELATIVE_PATH + " above " + Path.of("").toAbsolutePath());
    }
}
