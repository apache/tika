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
package org.apache.tika.io;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Stands in for an external program in tests: run in a JVM of its own, it reports the file
 * it was given. No shell, so it behaves the same on every OS.
 */
public class InputFileProbe {

    private static final String ARG = "arg=";
    private static final String ENTRY = "entry=";
    private static final String CONTENT = "content=";

    public static void main(String[] args) throws IOException {
        Path input = Paths.get(args[0]);
        StringBuilder sb = new StringBuilder();
        sb.append(ARG).append(args[0]).append('\n');
        try (Stream<Path> entries = Files.list(input.getParent())) {
            entries.map(p -> p.getFileName().toString()).sorted()
                    .forEach(name -> sb.append(ENTRY).append(name).append('\n'));
        }
        sb.append(CONTENT).append(new String(Files.readAllBytes(input), UTF_8)).append('\n');
        System.out.write(sb.toString().getBytes(UTF_8));
        System.out.flush();
    }

    /** A command line that runs this class on {@code inputToken}. */
    public static String[] command(String inputToken) throws URISyntaxException {
        Path classes = Paths.get(InputFileProbe.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        return new String[]{
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classes.toString(), InputFileProbe.class.getName(), inputToken};
    }

    /** The path the program was given. */
    public static Path arg(String stdout) {
        return Paths.get(values(stdout, ARG).get(0));
    }

    /** The names in the directory that holds the given path. */
    public static List<String> entries(String stdout) {
        return values(stdout, ENTRY);
    }

    public static String content(String stdout) {
        return values(stdout, CONTENT).get(0);
    }

    private static List<String> values(String stdout, String prefix) {
        List<String> values = new ArrayList<>();
        for (String line : stdout.split("\r?\n")) {
            if (line.startsWith(prefix)) {
                values.add(line.substring(prefix.length()));
            }
        }
        return values;
    }
}
