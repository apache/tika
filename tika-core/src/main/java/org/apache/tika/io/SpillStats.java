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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Diagnostic scoreboard of temp-file spills by calling site. Enabled only when the system
 * property {@code tika.debug.spillStats} names an output file; otherwise every call is a
 * single volatile read. The forked pipes worker may be hard-killed, so the summary is
 * rewritten every {@link #DUMP_EVERY} spills as well as at shutdown.
 */
public final class SpillStats {

    public static final String PROP = "tika.debug.spillStats";
    private static final int DUMP_EVERY = 100;
    private static final int FRAMES = 4;

    private static final Path OUT;
    private static final Map<String, long[]> SITES = new ConcurrentHashMap<>();
    private static final AtomicLong TOTAL_FILES = new AtomicLong();
    private static final AtomicLong TOTAL_BYTES = new AtomicLong();

    static {
        String p = System.getProperty(PROP);
        OUT = p == null || p.isBlank() ? null : Paths.get(p);
        if (OUT != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(SpillStats::dump, "spill-stats-dump"));
        }
    }

    private SpillStats() {
    }

    public static boolean enabled() {
        return OUT != null;
    }

    /** Captures the calling site; returns the key to pass to {@link #recordDelete}. */
    public static String recordCreate() {
        // key = the io-layer spill site, then the first FRAMES frames outside org.apache.tika.io
        // (the parser/detector that forced the spill); io-internal plumbing frames are skipped.
        List<StackTraceElement> frames = StackWalker.getInstance().walk(s -> s
                .map(StackWalker.StackFrame::toStackTraceElement)
                .filter(f -> f.getClassName().startsWith("org.apache.tika."))
                .filter(f -> !f.getClassName().endsWith("TemporaryResources")
                        && !f.getClassName().endsWith("SpillStats"))
                .collect(Collectors.toList()));
        StringBuilder sb = new StringBuilder();
        int outside = 0;
        for (int i = 0; i < frames.size() && outside < FRAMES; i++) {
            StackTraceElement f = frames.get(i);
            boolean io = f.getClassName().startsWith("org.apache.tika.io.");
            if (i == 0 || !io) {
                if (sb.length() > 0) {
                    sb.append('<');
                }
                sb.append(shortName(f.getClassName())).append('.').append(f.getMethodName());
                if (!io) {
                    outside++;
                }
            }
        }
        String site = sb.toString();
        SITES.computeIfAbsent(site, k -> new long[2])[0]++;
        return site;
    }

    public static void recordDelete(String site, Path path) {
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            return;
        }
        SITES.computeIfAbsent(site, k -> new long[2])[1] += size;
        TOTAL_BYTES.addAndGet(size);
        if (TOTAL_FILES.incrementAndGet() % DUMP_EVERY == 0) {
            dump();
        }
    }

    private static String shortName(String cls) {
        return cls.substring(cls.lastIndexOf('.') + 1);
    }

    static synchronized void dump() {
        List<String> lines = new ArrayList<>();
        lines.add("# spill stats pid=" + ProcessHandle.current().pid() + " files=" + TOTAL_FILES.get()
                + " bytes=" + TOTAL_BYTES.get());
        lines.add("bytes\tcount\tsite");
        SITES.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .forEach(e -> lines.add(e.getValue()[1] + "\t" + e.getValue()[0] + "\t" + e.getKey()));
        try {
            Path out = OUT.resolveSibling(OUT.getFileName() + "." + ProcessHandle.current().pid());
            Files.write(out, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // diagnostics only; never fail the parse
        }
    }
}
