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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * A decode-as charset may be a {@link PatchedCharset}, whose name {@link java.nio.charset.Charset#forName}
 * and name-taking library APIs cannot resolve. Fails on main sources anywhere in the repo that
 * take the name straight off a decode-as call; use {@link CharsetSupersets#jdkDecodeAs} for
 * those APIs. forbidden-apis bans signatures, not call chains, so this scans source.
 */
public class DecodeAsNameUsageTest {

    // getDecodeAs() / decodeAs(...) / supersetOf(...), then .name() / .displayName() / .toString()
    private static final Pattern NAME_OF_DECODE_AS = Pattern.compile(
            "\\b(?:getDecodeAs|decodeAs|supersetOf)\\s*\\((?:[^()]|\\([^()]*\\))*\\)\\s*\\.\\s*"
                    + "(?:name|displayName|toString)\\s*\\(");

    private static final String SRC_MAIN =
            File.separator + "src" + File.separator + "main" + File.separator + "java"
                    + File.separator;

    @Test
    public void noDecodeAsNameInMainSources() throws IOException {
        Path root = repoRoot();
        assumeTrue(root != null, "repo root (tika-core + tika-parsers) not found");
        List<String> hits = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String n = dir.getFileName() == null ? "" : dir.getFileName().toString();
                return !dir.equals(root) && (n.startsWith(".") || n.equals("target")
                        || n.equals("node_modules")) ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
                if (p.toString().endsWith(".java") && p.toString().contains(SRC_MAIN)) {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    Matcher m = NAME_OF_DECODE_AS.matcher(src);
                    while (m.find()) {
                        hits.add(root.relativize(p) + ":" + line(src, m.start()) + ": "
                                + m.group().replaceAll("\\s+", " "));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertTrue(hits.isEmpty(), "A decode-as charset's name may not resolve by name; use "
                + "CharsetSupersets.jdkDecodeAs(detected) for APIs that take a charset name:\n  "
                + String.join("\n  ", hits));
    }

    @Test
    public void patternCatchesTheShapes() {
        String[] bad = {"r.get(0).getDecodeAs().name()", "CharsetSupersets.decodeAs(cs).name()",
                "decodeAs(Charset.forName(x)) .\n name()", "supersetOf(c).toString()",
                "result.getDecodeAs().displayName()"};
        for (String s : bad) {
            assertTrue(NAME_OF_DECODE_AS.matcher(s).find(), s);
        }
        String[] ok = {"CharsetSupersets.jdkDecodeAs(cs).name()", "decodeAs.name()",
                "getDecodeAs()"};
        for (String s : ok) {
            assertTrue(!NAME_OF_DECODE_AS.matcher(s).find(), s);
        }
    }

    private static int line(String src, int offset) {
        int n = 1;
        for (int i = 0; i < offset; i++) {
            if (src.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("tika-core"))
                    && Files.isDirectory(p.resolve("tika-parsers"))) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }
}
