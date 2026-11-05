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
package org.apache.tika.pipes.core;

import static org.apache.tika.utils.ProcessTestHelper.awaitProcessesWith;
import static org.apache.tika.utils.ProcessTestHelper.describe;
import static org.apache.tika.utils.ProcessTestHelper.processesWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * A forked server's external processes and their children end with the server. The parser runs
 * {@code sh -c "sleep N; true"}, so {@code sleep} is a child of the process Tika started.
 */
@EnabledOnOs(OS.LINUX)
public class ExternalProcessCleanupTest {

    private static final String DOC = "doc.txt";

    enum ServerMode {
        PER_CLIENT,
        SHARED
    }

    enum ServerEnd {
        /** PipesParser.close() mid-parse: the parent destroys the server. */
        PARENT_KILLS_SERVER,
        /** The calling thread is interrupted; the per-client server loses its connection and exits. */
        SERVER_EXITS_ITSELF
    }

    @Test
    public void perClientServerKilledByParent(@TempDir Path tmp) throws Exception {
        run(tmp, ServerMode.PER_CLIENT, ServerEnd.PARENT_KILLS_SERVER);
    }

    @Test
    public void perClientServerExitsItself(@TempDir Path tmp) throws Exception {
        run(tmp, ServerMode.PER_CLIENT, ServerEnd.SERVER_EXITS_ITSELF);
    }

    @Test
    public void sharedServerKilledByParent(@TempDir Path tmp) throws Exception {
        run(tmp, ServerMode.SHARED, ServerEnd.PARENT_KILLS_SERVER);
    }

    private void run(Path tmp, ServerMode mode, ServerEnd end) throws Exception {
        // doubles as the sleep duration, so both command lines contain it; random per run
        String marker = String.format(Locale.ROOT, "301.%06d",
                ThreadLocalRandom.current().nextInt(1_000_000));
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve(DOC), "hello", StandardCharsets.UTF_8);
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-external-sleep.json", tmp, inputDir, tmp.resolve("output"), false,
                Map.of("USE_SHARED_SERVER", mode == ServerMode.SHARED,
                        "COMMAND_LINE", List.of("sh", "-c", "sleep " + marker + "; true")));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesParser pipesParser = PipesParser.load(tikaJsonConfig, PipesConfig.load(tikaJsonConfig));

        AtomicReference<String> parseOutcome = new AtomicReference<>();
        Thread parseThread = new Thread(() -> {
            try {
                PipesResult r = pipesParser.parse(new FetchEmitTuple(DOC, new FetchKey("fsf", DOC),
                        new EmitKey(), new Metadata(), new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                parseOutcome.set(r.status() + " " + r.message());
            } catch (Throwable t) {
                parseOutcome.set(t.getClass().getSimpleName());
            }
        }, "external-process-cleanup-parse");
        try {
            parseThread.start();
            List<ProcessHandle> running = awaitProcessesWith(marker, 2, 30_000);
            assertEquals(2, running.size(), "sh and its sleep should be running: " + describe(running)
                    + " parseOutcome=" + parseOutcome.get());
            ProcessHandle server = serverOf(running);

            if (end == ServerEnd.PARENT_KILLS_SERVER) {
                pipesParser.close();
            } else {
                parseThread.interrupt();
            }
            boolean serverExited = awaitExit(server, 30_000);
            List<ProcessHandle> survivors = awaitProcessesWith(marker, 0, 3_000);
            String report = String.format(Locale.ROOT,
                    "mode=%s end=%s server=%d serverExited=%s parseOutcome=%s before=%s survivors=%s",
                    mode, end, server.pid(), serverExited, parseOutcome.get(), describe(running),
                    describe(survivors));
            assertTrue(serverExited, "the server did not exit: " + report);
            assertTrue(survivors.isEmpty(), "processes outlived the server: " + report);
        } finally {
            parseThread.interrupt();
            pipesParser.close();
            processesWith(marker).forEach(ProcessHandle::destroyForcibly);
        }
    }

    /** The forked server: the parent of the process it started, and a child of this JVM. */
    private static ProcessHandle serverOf(List<ProcessHandle> running) {
        long thisJvm = ProcessHandle.current().pid();
        for (ProcessHandle ph : running) {
            ProcessHandle parent = ph.parent().orElse(null);
            if (parent != null && parent.parent().map(ProcessHandle::pid).orElse(-1L) == thisJvm) {
                return parent;
            }
        }
        throw new IllegalStateException("no process under a server of this JVM: " + describe(running));
    }

    private static boolean awaitExit(ProcessHandle ph, long millis) throws Exception {
        try {
            ph.onExit().get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }
}
