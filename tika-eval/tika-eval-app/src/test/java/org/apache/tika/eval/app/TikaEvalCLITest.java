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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TikaEvalCLITest {
    //TODO: these barely reach the minimal acceptable stage for unit tests

    private final static String dbName = "testdb";

    @TempDir
    private static Path TEMP_DIR;
    private static Path extractsDir = Paths.get("src/test/resources/test-dirs");
    private static Path compareDBDir;
    private static Path profileDBDir;
    private static Path compareReportsDir;
    private static Path profileReportsDir;

    @BeforeAll
    public static void setUp() throws Exception {
        compareDBDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-compare-db-");
        profileDBDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-profile-db-");
        compareReportsDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-compare-reports-");
        profileReportsDir = Files.createTempDirectory(TEMP_DIR, "tika-eval-cli-profile-reports-");
        compare();
        profile();
        reportCompare();
        reportProfile();
    }

    private static void compare() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Compare");
        args.add("-a");
        args.add(extractsDir
                .resolve("extractsA")
                .toAbsolutePath()
                .toString());
        args.add("-b");
        args.add(extractsDir
                .resolve("extractsB")
                .toAbsolutePath()
                .toString());

        args.add("-d");
        args.add(compareDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName);

        execute(args);

    }

    private static void profile() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Profile");
        args.add("-e");
        args.add(extractsDir
                .resolve("extractsA")
                .toAbsolutePath()
                .toString());

        args.add("-d");
        args.add(profileDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName);
        execute(args);
    }

    private static void reportProfile() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Report");
        args.add("-db");
        args.add(profileDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName);
        args.add("-rd");
        args.add(profileReportsDir
                .toAbsolutePath()
                .toString());
        execute(args);
    }

    private static void reportCompare() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Report");
        args.add("-db");
        args.add(compareDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName);
        args.add("-rd");
        args.add(compareReportsDir
                .toAbsolutePath()
                .toString());
        execute(args);
    }

    private static void execute(List<String> args) throws Exception {
        TikaEvalCLI.main(args.toArray(new String[0]));
    }

    @Test
    public void testBasicCompare() throws Exception {
        Set<String> fNames = new HashSet<>();
        for (File f : compareDBDir
                .toFile()
                .listFiles()) {
            fNames.add(f.getName());
        }
        assertTrue(fNames.contains(dbName + ".mv.db"), fNames.toString());
    }

    @Test
    public void testBasicProfile() throws Exception {
        Set<String> fNames = new HashSet<>();
        for (File f : profileDBDir
                .toFile()
                .listFiles()) {
            fNames.add(f.getName());
        }
        assertTrue(fNames.contains(dbName + ".mv.db"), fNames.toString());
    }

    @Test
    public void testProfileReports() throws Exception {
        CachingFileVisitor v = new CachingFileVisitor();
        Files.walkFileTree(profileReportsDir, v);
        int cnt = 0;
        for (Path report : v.getPaths()) {

            if (report
                    .getFileName()
                    .toString()
                    .endsWith(".xlsx")) {
                cnt++;
            }
        }
        assertTrue(cnt > 5);
    }

    @Test
    public void testComparisonReports() throws Exception {
        CachingFileVisitor v = new CachingFileVisitor();
        Files.walkFileTree(compareReportsDir, v);
        int cnt = 0;
        boolean hasSummaryMd = false;
        for (Path report : v.getPaths()) {
            String name = report.getFileName().toString();
            if (name.endsWith(".xlsx")) {
                cnt++;
            }
            if ("summary.md".equals(name)) {
                hasSummaryMd = true;
                assertTrue(Files.size(report) > 100,
                        "summary.md should not be empty");
            }
        }
        assertTrue(cnt > 33);
        assertTrue(hasSummaryMd, "summary.md should be generated for comparison reports");
        // If there is a failure, check for SQL errors in the previous log.
        // If it's is a syntax error, for the position look for "[*]" in the exception message.
        // The "[42001-230]" is [<error number>-<build number].
    }

    @Test
    @Disabled("use this for development")
    public void testOneOff() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Compare");
        args.add("-extractsA");
        args.add(extractsDir
                .resolve("extractsA")
                .toAbsolutePath()
                .toString());
        args.add("-extractsB");
        args.add(extractsDir
                .resolve("extractsB")
                .toAbsolutePath()
                .toString());
        args.add("-db");
        args.add(compareDBDir
                .toAbsolutePath()
                .toString() + "/" + dbName);

        execute(args);
        //      args.add("-drop");
//        args.add("-jdbc");
//        args.add("jdbc:postgresql:tika_eval?user=user&password=password");

    }

    private final static class CachingFileVisitor implements FileVisitor<Path> {
        Set<Path> paths = new HashSet<>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            paths.add(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        Set<Path> getPaths() {
            return paths;
        }
    }

}
