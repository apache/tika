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
package org.apache.tika.pipes.grpc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ComponentIds;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.config.ConfigMerger;
import org.apache.tika.pipes.core.config.ConfigOverrides;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.core.fetcher.BytesFetcher;
import org.apache.tika.pipes.core.fetcher.InlineBytes;
import org.apache.tika.pipes.core.fetcher.PayloadRouter;

/**
 * Passes ParseBytes content to the forked pipes workers. Content up to
 * {@code pipes.maxInlineBytes} goes with the request; larger content goes to a file in a
 * temporary directory, which a reserved file-system fetcher reads. The server and its
 * workers load a copy of the user's config that adds this fetcher. One spool lasts as long as the server, and
 * each request stages its bytes in a {@link Payload}.
 */
final class ParseBytesSpool {

    private static final Logger LOG = LoggerFactory.getLogger(ParseBytesSpool.class);

    /** Each spool appends a UUID, so servers sharing a config store get different fetchers. */
    static final String FETCHER_PREFIX = ComponentIds.SYSTEM_PREFIX + "tika-grpc-parse-bytes-";

    private final String fetcherId;
    private final Path directory;
    private final Path userConfig;
    private final Path effectiveConfig;
    private final int maxInlineBytes;
    private boolean released;
    private int openSpoolFiles;

    private ParseBytesSpool(String fetcherId, Path directory, Path userConfig,
                            Path effectiveConfig, int maxInlineBytes) {
        this.fetcherId = fetcherId;
        this.directory = directory;
        this.userConfig = userConfig;
        this.effectiveConfig = effectiveConfig;
        this.maxInlineBytes = maxInlineBytes;
    }

    /**
     * Creates the spool directory under {@code parent} and an effective copy of
     * {@code userConfig} that adds the spool fetcher. The user's file is not modified.
     */
    static ParseBytesSpool create(Path userConfig, Path parent, int maxInlineBytes)
            throws IOException {
        Path directory = Files.createTempDirectory(parent, "tika-grpc-parse-bytes");
        String fetcherId = FETCHER_PREFIX + UUID.randomUUID();
        try {
            Map<String, Object> fetcherConfig = new HashMap<>();
            fetcherConfig.put("basePath", directory.toAbsolutePath().toString());
            fetcherConfig.put("extractFileSystemMetadata", false);
            Path effectiveConfig = ConfigMerger.mergeOrCreate(userConfig, ConfigOverrides.builder()
                    .addFetcher(fetcherId, "file-system-fetcher", fetcherConfig)
                    .build()).configPath();
            return new ParseBytesSpool(fetcherId, directory, userConfig, effectiveConfig,
                    maxInlineBytes);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(directory);
            throw e;
        }
    }

    String fetcherId() {
        return fetcherId;
    }

    Path directory() {
        return directory;
    }

    /** The config the server and its workers load: the user's config plus the spool fetcher. */
    Path effectiveConfig() {
        return effectiveConfig;
    }

    /**
     * Stages one request's bytes. Up to {@code maxInlineBytes} they are set on
     * {@code context} as {@link InlineBytes}; larger content is written to a spool file.
     * Closing the payload deletes the file.
     *
     * @throws IllegalStateException if the content needs a spool file and the spool has been
     *                               released
     */
    Payload stage(InputStream content, String resourceNameHint, ParseContext context)
            throws IOException {
        String hint = resourceNameHint == null || resourceNameHint.isBlank()
                ? "" : resourceNameHint.trim();
        TikaInputStream tis = TikaInputStream.get(content);
        Path[] created = new Path[1];
        PayloadRouter.Routed routed;
        try {
            routed = PayloadRouter.route(tis, maxInlineBytes,
                    () -> created[0] = createSpoolFile());
        } catch (IOException | RuntimeException e) {
            tis.close();
            if (created[0] != null) {
                deleteQuietly(created[0]);
                spoolFileClosed();
            }
            throw e;
        }
        FetchKey fetchKey;
        if (routed.isInline()) {
            context.set(InlineBytes.class, routed.inlineBytes());
            fetchKey = new FetchKey(BytesFetcher.FETCHER_ID, hint);
        } else {
            fetchKey = new FetchKey(fetcherId, routed.path().getFileName().toString());
        }
        return new Payload(tis, routed, fetchKey, hint);
    }

    private synchronized Path createSpoolFile() throws IOException {
        if (released) {
            throw new IllegalStateException("the ParseBytes spool has been released");
        }
        Path file = Files.createTempFile(directory, "parse-bytes-", "");
        openSpoolFiles++;
        return file;
    }

    private synchronized void spoolFileClosed() {
        openSpoolFiles--;
        if (released && openSpoolFiles == 0) {
            deleteRecursively(directory);
        }
    }

    /**
     * Removes this spool's fetcher from {@code store} and deletes the effective config. The
     * directory is deleted now, or by the last open payload when it closes. Safe to call more
     * than once.
     */
    synchronized void release(ConfigStore store) {
        if (released) {
            return;
        }
        released = true;
        if (store != null) {
            // Look the id up first: a file-backed store re-reads its file on lookups but not
            // on remove, and remove writes this JVM's copy back, which would drop entries
            // other servers added since.
            try {
                if (store.containsKey(fetcherId)) {
                    store.remove(fetcherId);
                }
            } catch (RuntimeException e) {
                LOG.debug("Could not remove {} from the config store", fetcherId, e);
            }
        }
        if (openSpoolFiles == 0) {
            deleteRecursively(directory);
        }
        if (!effectiveConfig.equals(userConfig)) {
            deleteQuietly(effectiveConfig);
        }
    }

    /** One request's staged bytes. */
    final class Payload implements AutoCloseable {
        private final TikaInputStream tis;
        private final PayloadRouter.Routed routed;
        private final FetchKey fetchKey;
        private final String resourceNameHint;

        private Payload(TikaInputStream tis, PayloadRouter.Routed routed, FetchKey fetchKey,
                        String resourceNameHint) {
            this.tis = tis;
            this.routed = routed;
            this.fetchKey = fetchKey;
            this.resourceNameHint = resourceNameHint;
        }

        FetchKey fetchKey() {
            return fetchKey;
        }

        /**
         * Copies {@code parsed} without what the spool file adds: its path under
         * {@link TikaCoreProperties#SOURCE_PATH}, and its name as the resource name when the
         * caller sent none. Returns {@code null} for {@code null}, which means the forked pipes
         * worker returned no metadata.
         */
        Metadata scrub(Metadata parsed) {
            if (parsed == null) {
                return null;
            }
            Metadata copy = new Metadata();
            String sourcePath = TikaCoreProperties.SOURCE_PATH.getName();
            String resourceName = TikaCoreProperties.RESOURCE_NAME_KEY.getName();
            boolean dropResourceName = resourceNameHint.isEmpty();
            for (String name : parsed.names()) {
                if (name.equals(sourcePath) || (dropResourceName && name.equals(resourceName))) {
                    continue;
                }
                for (String value : parsed.getValues(name)) {
                    // addTrusted: plain writes refuse reserved keys such as the parser chain
                    // and the digest.
                    copy.addTrusted(name, value);
                }
            }
            return copy;
        }

        @Override
        public void close() throws IOException {
            try {
                routed.close();
            } finally {
                try {
                    tis.close();
                } finally {
                    if (!routed.isInline()) {
                        spoolFileClosed();
                    }
                }
            }
        }
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(ParseBytesSpool::deleteQuietly);
        } catch (IOException e) {
            LOG.warn("Could not clean the ParseBytes spool directory {}", dir, e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.debug("Could not delete {}", path, e);
        }
    }
}
