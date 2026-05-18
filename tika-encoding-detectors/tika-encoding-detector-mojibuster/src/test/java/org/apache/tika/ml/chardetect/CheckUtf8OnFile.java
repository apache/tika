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
package org.apache.tika.ml.chardetect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Quick-and-dirty: run checkUtf8 on a list of files and report the
 * result + error count + post-strip result.
 */
public final class CheckUtf8OnFile {

    private CheckUtf8OnFile() {
    }

    public static void main(String[] args) throws Exception {
        Path probeDir = null;
        String[] probes = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--probe-dir":
                    probeDir = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--probes":
                    probes = args[++i].split(",");
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (probeDir == null || probes == null) {
            System.err.println("Usage: CheckUtf8OnFile --probe-dir <dir> --probes p1,p2,...");
            System.exit(1);
        }
        for (String pid : probes) {
            Path p = probeDir.resolve(pid);
            if (!Files.exists(p)) {
                System.err.println("Missing: " + p);
                continue;
            }
            byte[] bytes = Files.readAllBytes(p);
            String shortId = pid.contains("/")
                    ? pid.substring(pid.indexOf('/') + 1, pid.indexOf('/') + 13) : pid;

            StructuralEncodingRules.Utf8Result rawR = StructuralEncodingRules.checkUtf8(bytes);
            int rawErrors = StructuralEncodingRules.countUtf8Errors(bytes);

            byte[] dst = new byte[bytes.length];
            HtmlByteStripper.Result sr = HtmlByteStripper.strip(bytes, 0, bytes.length, dst, 0);
            byte[] stripped = (sr.tagCount >= 1)
                    ? java.util.Arrays.copyOf(dst, sr.length) : bytes;
            StructuralEncodingRules.Utf8Result strpR = StructuralEncodingRules.checkUtf8(stripped);
            int strpErrors = StructuralEncodingRules.countUtf8Errors(stripped);

            System.out.printf(Locale.ROOT,
                    "%-14s raw=%6dB result=%-14s errors=%4d (%.4f%%)   "
                            + "strip=%6dB result=%-14s errors=%4d (%.4f%%)%n",
                    shortId, bytes.length, rawR, rawErrors,
                    100.0 * rawErrors / Math.max(1, bytes.length),
                    stripped.length, strpR, strpErrors,
                    100.0 * strpErrors / Math.max(1, stripped.length));
        }
    }
}
