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
package org.apache.tika.eval.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.eval.app.db.H2Util;

/** A gzipped extract set ({@code .json.gz}, as the file-system emitter writes it) compares like its plain twin. */
public class ComparerGzipExtractsTest {

    private static final String[] QUERIES = {
            "select file_path || ' ' || extract_file_length_a || ' ' || extract_file_length_b"
                    + " from containers order by file_path",
            "select count(1) from profiles_a",
            "select count(1) from profiles_b",
            "select count(1) from extract_exceptions_a",
            "select count(1) from extract_exceptions_b",
            "select count(1) from content_comparisons"
    };

    @TempDir
    Path tmp;

    @Test
    public void testGzipOnEitherSide() throws Exception {
        Path testDirs = Paths.get(getClass().getResource("/test-dirs").toURI());
        Path a = testDirs.resolve("extractsA");
        Path b = testDirs.resolve("extractsB");
        List<String> plain = compare("plain", a, b);
        assertTrue(plain.size() > QUERIES.length, "comparison produced no containers");
        assertEquals(plain, compare("gzB", a, gzipCopy(b, tmp.resolve("bGz"))), "gzipped B");
        assertEquals(plain, compare("gzA", gzipCopy(a, tmp.resolve("aGz")), b), "gzipped A");
    }

    private List<String> compare(String name, Path a, Path b) throws Exception {
        Path db = tmp.resolve(name).toAbsolutePath();
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-Djava.awt.headless=true");
        args.add("-cp");
        args.add(System.getProperty("java.class.path"));
        args.add(TikaEvalCLI.class.getName());
        args.add("Compare");
        args.add("-extractsA");
        args.add(a.toAbsolutePath().toString());
        args.add("-extractsB");
        args.add(b.toAbsolutePath().toString());
        args.add("-db");
        args.add(db.toString());
        Process process = new ProcessBuilder(args).inheritIO().start();
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "compare timed out");
        assertEquals(0, process.exitValue());

        List<String> results = new ArrayList<>();
        try (Connection c = new H2Util(db).getConnection(); Statement st = c.createStatement()) {
            for (String q : QUERIES) {
                try (ResultSet rs = st.executeQuery(q)) {
                    while (rs.next()) {
                        results.add(q + " -> " + rs.getString(1));
                    }
                }
            }
        }
        return results;
    }

    private static Path gzipCopy(Path src, Path dest) throws IOException {
        FileUtils.copyDirectory(src.toFile(), dest.toFile());
        List<Path> jsons;
        try (Stream<Path> files = Files.walk(dest)) {
            jsons = files.filter(f -> f.toString().endsWith(".json")).collect(Collectors.toList());
        }
        for (Path p : jsons) {
            try (OutputStream os = new GZIPOutputStream(
                    Files.newOutputStream(p.resolveSibling(p.getFileName() + ".gz")))) {
                Files.copy(p, os);
            }
            Files.delete(p);
        }
        return dest;
    }
}
