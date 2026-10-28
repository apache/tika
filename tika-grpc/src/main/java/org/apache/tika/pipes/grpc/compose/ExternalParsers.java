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
package org.apache.tika.pipes.grpc.compose;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;
import io.grpc.ChannelCredentials;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.adapter.ExternalParserAdapter;
import org.apache.tika.grpc.mapper.AdapterMerge;
import org.apache.tika.grpc.mapper.DocumentComposer;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.EmbeddedDocument;
import org.apache.tika.grpc.v2.ExtensionResult;
import org.apache.tika.metadata.Metadata;

/**
 * The external gRPC parsers tika-grpc composes into ParseBytes replies (TIKA-4771). Each
 * {@code grpc.externalParsers} entry names an {@link ExternalParserAdapter} found on the
 * classpath. After Tika parses and detects the content type, every adapter that supports
 * the type calls its parser with the same bytes and turns the reply into a contribution;
 * {@link AdapterMerge} adds its child documents and prefixed parent keys to the Document,
 * never changing Tika's own fields, and {@link DocumentComposer} adds one
 * {@link ExtensionResult} slot per adapter. With none configured, the Document is returned
 * unchanged.
 * <p>
 * Adapters run in this server's JVM: they are trusted code, loaded from the classpath.
 * <p>
 * The parsers are called one after another on the request's thread, so a client that
 * cancels the ParseBytes call also cancels the call in progress. The request keeps its
 * ParseBytes admission place while they run, so {@code parseBytesMaxConcurrent} bounds
 * these calls and the replies they hold too.
 */
public final class ExternalParsers implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalParsers.class);

    /**
     * How many serialized bytes the external parsers may add to a Document, all together,
     * when {@code maxAddedBytes} is not given.
     */
    public static final long DEFAULT_MAX_ADDED_BYTES = 96L * 1024 * 1024;

    /** No external parsers: {@link #compose} returns the Document unchanged. */
    public static final ExternalParsers NONE =
            new ExternalParsers(List.of(), List.of(), DEFAULT_MAX_ADDED_BYTES);

    private final List<ExternalParser<?, ?>> parsers;
    private final List<ManagedChannel> channels;
    private final long maxAddedBytes;

    private ExternalParsers(List<ExternalParser<?, ?>> parsers, List<ManagedChannel> channels,
                            long maxAddedBytes) {
        this.parsers = parsers;
        this.channels = channels;
        this.maxAddedBytes = maxAddedBytes;
    }

    /**
     * Finds each entry's adapter on tika-grpc's classpath, checks it, and opens a channel to
     * its parser. Channels connect lazily, so a parser that is not up yet does not stop the
     * server from starting; anything wrong with the configuration or an adapter does.
     *
     * @param maxAddedBytes    how much the parsers may add to a Document, all together
     * @throws IllegalArgumentException if an entry is incomplete, names no adapter on the
     *                                  classpath (or one that failed to load), repeats a
     *                                  producer id, has bad options or credential files, or
     *                                  its adapter's declarations are not usable or its key
     *                                  prefix overlaps another's
     */
    public static ExternalParsers open(List<ExternalParserConfig> configs, long maxAddedBytes) {
        if (configs == null || configs.isEmpty()) {
            return NONE;
        }
        return open(configs,
                ExternalParserAdapters.load(ExternalParsers.class.getClassLoader()),
                ExternalParsers::channel, maxAddedBytes);
    }

    /**
     * Like {@link #open(List, long)}, with these adapters and the channels from
     * {@code channels}; the channels are shut down by {@link #close()}.
     */
    public static ExternalParsers open(List<ExternalParserConfig> configs,
                                       ExternalParserAdapters adapters,
                                       Function<ExternalParserConfig, ManagedChannel> channels) {
        return open(configs, adapters, channels, DEFAULT_MAX_ADDED_BYTES);
    }

    public static ExternalParsers open(List<ExternalParserConfig> configs,
                                       ExternalParserAdapters adapters,
                                       Function<ExternalParserConfig, ManagedChannel> channels,
                                       long maxAddedBytes) {
        if (configs == null || configs.isEmpty()) {
            return NONE;
        }
        if (maxAddedBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxAddedBytes must be positive, got: " + maxAddedBytes);
        }
        List<ExternalParser<?, ?>> parsers = new ArrayList<>();
        List<ManagedChannel> opened = new ArrayList<>();
        Set<String> producerIds = new HashSet<>();
        try {
            for (ExternalParserConfig config : configs) {
                config.validate();
                if (!producerIds.add(config.getProducerId())) {
                    throw new IllegalArgumentException("two external parsers have producerId '"
                            + config.getProducerId() + "'");
                }
                // Throws for a configured adapter that is missing, broken or duplicated; an
                // adapter jar that is on the classpath but not configured is never used.
                ExternalParserAdapter<?, ?> adapter = adapters.require(config.getProducerId());
                ManagedChannel channel = channels.apply(config);
                opened.add(channel);
                ExternalParser<?, ?> parser = parser(config, adapter, channel);
                String prefix = parser.keyPrefix().toLowerCase(Locale.ROOT);
                for (ExternalParser<?, ?> other : parsers) {
                    String otherPrefix = other.keyPrefix().toLowerCase(Locale.ROOT);
                    if (prefix.startsWith(otherPrefix) || otherPrefix.startsWith(prefix)) {
                        throw new IllegalArgumentException("external parsers '"
                                + other.producerId() + "' and '" + parser.producerId()
                                + "' have overlapping key prefixes '" + other.keyPrefix()
                                + "' and '" + parser.keyPrefix() + "'");
                    }
                }
                parsers.add(parser);
                LOG.info("external parser '{}': {} at {} ({}) for {}", config.getProducerId(),
                        adapter.method().getFullMethodName(), config.getTarget(),
                        config.isPlaintext() ? "plaintext" : "TLS", adapter.supportedTypes());
            }
        } catch (RuntimeException e) {
            opened.forEach(ManagedChannel::shutdownNow);
            throw e;
        }
        return new ExternalParsers(List.copyOf(parsers), List.copyOf(opened), maxAddedBytes);
    }

    private static <ReqT extends Message, RespT extends Message> ExternalParser<ReqT, RespT> parser(
            ExternalParserConfig config, ExternalParserAdapter<ReqT, RespT> adapter,
            ManagedChannel channel) {
        return new ExternalParser<>(config, adapter, channel);
    }

    private static ManagedChannel channel(ExternalParserConfig config) {
        // A reply can carry a whole archive's records, but no more than the entry allows.
        return Grpc.newChannelBuilder(config.getTarget(), credentials(config))
                .maxInboundMessageSize(config.getMaxReplyBytes())
                .build();
    }

    /** Plaintext only when the entry says so; otherwise TLS, with its CA and client cert. */
    static ChannelCredentials credentials(ExternalParserConfig config) {
        if (config.isPlaintext()) {
            return InsecureChannelCredentials.create();
        }
        ExternalParserConfig.Tls tls = config.getTls();
        if (tls == null) {
            return TlsChannelCredentials.create();
        }
        TlsChannelCredentials.Builder builder = TlsChannelCredentials.newBuilder();
        try {
            if (tls.getCaFile() != null) {
                builder.trustManager(new File(tls.getCaFile()));
            }
            if (tls.getCertFile() != null) {
                builder.keyManager(new File(tls.getCertFile()), new File(tls.getKeyFile()));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("external parser '" + config.getProducerId()
                    + "': cannot read its TLS files: " + e.getMessage(), e);
        }
        return builder.build();
    }

    public boolean isEmpty() {
        return parsers.isEmpty();
    }

    /**
     * Calls every parser whose adapter supports {@code core}'s content type with
     * {@code content}, and returns {@code core} with what they mapped merged in and one slot
     * each. Returns {@code core} itself when no adapter supports the type.
     * <p>
     * The parsers share one {@code maxAddedBytes} budget: each may add embedded documents up
     * to what the ones before it left. A caller that cancels stops the parsers not yet
     * called; their slots are {@code STATUS_FAILED}. Whatever goes wrong in composing, the
     * caller gets {@code core} back with every planned slot {@code STATUS_FAILED}, never an
     * exception, except for a {@link VirtualMachineError} other than a stack overflow.
     *
     * @param core         Tika's Document for {@code content}
     * @param tikaMetadata the metadata Tika's parse recorded, or {@code null} if none
     * @param content      the bytes Tika parsed
     * @param resourceName the resource name the caller sent, or empty
     */
    public Document compose(Document core, Metadata tikaMetadata, ByteString content,
                            String resourceName) {
        List<String> planned = new ArrayList<>();
        for (ExternalParser<?, ?> parser : parsers) {
            if (parser.accepts(core.getContentType())) {
                planned.add(parser.producerId());
            }
        }
        if (planned.isEmpty()) {
            return core;
        }
        try {
            return composePlanned(core, tikaMetadata, content, resourceName);
        } catch (VirtualMachineError e) {
            if (!(e instanceof StackOverflowError)) {
                throw e;
            }
            return failedAll(core, planned, e);
        } catch (RuntimeException | Error e) {
            return failedAll(core, planned, e);
        }
    }

    private Document composePlanned(Document core, Metadata tikaMetadata, ByteString content,
                                    String resourceName) {
        Metadata tika = tikaMetadata == null ? new Metadata() : tikaMetadata;
        long coreSize = core.getSerializedSize();
        Context context = Context.current();
        Document merged = core;
        List<ExtensionResult> results = new ArrayList<>();
        List<String> planned = new ArrayList<>();
        for (ExternalParser<?, ?> parser : parsers) {
            if (!parser.accepts(core.getContentType())) {
                continue;
            }
            planned.add(parser.producerId());
            if (context.isCancelled()) {
                results.add(DocumentComposer.failed(parser.producerId(),
                        "not called: cancelled by the caller"));
                continue;
            }
            long left = maxAddedBytes - (merged.getSerializedSize() - coreSize);
            ExternalParser.Outcome outcome = parser.parse(merged, content, tika, resourceName,
                    left);
            merged = outcome.document();
            results.add(outcome.slot());
        }
        // No digest check (rule 3): Tika and the external parsers got the same ByteString.
        return fit(core, DocumentComposer.compose(merged, null, null, results, planned));
    }

    private static Document failedAll(Document core, List<String> planned, Throwable e) {
        LOG.warn("composing external parser results failed; returning Tika's Document", e);
        Document.Builder failed = core.toBuilder();
        for (String producerId : planned) {
            failed.addExtensions(DocumentComposer.failed(producerId,
                    "composing failed: " + e.getClass().getName()));
        }
        return failed.build();
    }

    /**
     * Keeps what the external parsers added to {@code core} within {@code maxAddedBytes}, as a
     * last resort after each parser's own share of the budget: drops the raw replies first,
     * then the parsers' embedded documents from the end, then their parent keys from the
     * end, and says so on the slots it took from. Tika's own Document and the slots
     * themselves, raw replies aside (bounded by their own warning caps), are not counted, and Tika's Document is
     * never cut, so the reply may be larger than this limit when Tika's part alone is large.
     *
     * @throws IllegalStateException if the parsers' additions still do not fit, which the
     *                               steps above rule out
     */
    private Document fit(Document core, Document composed) {
        long coreSize = core.getSerializedSize();
        if (added(composed, coreSize) <= maxAddedBytes) {
            return composed;
        }
        String over = "the external parsers added more than " + maxAddedBytes + " bytes";
        Document.Builder fitted = composed.toBuilder();
        for (int i = 0; i < fitted.getExtensionsCount(); i++) {
            ExtensionResult slot = fitted.getExtensions(i);
            if (slot.hasPayload()) {
                fitted.setExtensions(i, slot.toBuilder().clearPayload().setTruncated(true)
                        .addWarnings("raw reply dropped: " + over));
            }
        }
        long added = added(fitted.build(), coreSize);
        Map<String, Integer> removed = new HashMap<>();
        // Tika's own embedded documents, if any, come first and are not the parsers' to lose.
        int tikaEmbedded = core.getEmbeddedCount();
        while (added > maxAddedBytes && fitted.getEmbeddedCount() > tikaEmbedded) {
            int last = fitted.getEmbeddedCount() - 1;
            EmbeddedDocument embedded = fitted.getEmbedded(last);
            added -= CodedOutputStream.computeMessageSize(
                    Document.EMBEDDED_FIELD_NUMBER, embedded);
            removed.merge(embedded.getProducerId(), 1, Integer::sum);
            fitted.removeEmbedded(last);
        }
        // Then the parsers' parent keys, which follow Tika's own in extra.
        int tikaExtra = core.getExtraCount();
        int droppedKeys = 0;
        while (added > maxAddedBytes && fitted.getExtraCount() > tikaExtra) {
            int last = fitted.getExtraCount() - 1;
            added -= CodedOutputStream.computeMessageSize(
                    Document.EXTRA_FIELD_NUMBER, fitted.getExtra(last));
            fitted.removeExtra(last);
            droppedKeys++;
        }
        for (int i = 0; i < fitted.getExtensionsCount(); i++) {
            ExtensionResult slot = fitted.getExtensions(i);
            Integer count = removed.get(slot.getProducerId());
            if (count != null) {
                fitted.setExtensions(i, slot.toBuilder()
                        .setEmbeddedKept(slot.getEmbeddedKept() - count)
                        .setTruncated(true)
                        .addWarnings(count + " embedded documents dropped: " + over));
            }
            if (droppedKeys > 0) {
                fitted.setExtensions(i, fitted.getExtensions(i).toBuilder()
                        .addWarnings(droppedKeys + " external parent keys dropped: " + over));
            }
        }
        Document result = fitted.build();
        long finalAdded = added(result, coreSize);
        if (finalAdded > maxAddedBytes) {
            throw new IllegalStateException("external parser additions are " + finalAdded
                    + " bytes after fitting, over " + maxAddedBytes);
        }
        return result;
    }

    /** What the parsers added to a Document of {@code coreSize} bytes, slots aside. */
    private static long added(Document document, long coreSize) {
        return document.getSerializedSize() - coreSize - slotBytes(document);
    }

    /**
     * The bytes of the slots themselves, which fitting keeps whatever their size: their
     * status, counts and warnings, not the raw replies they carry, which do count.
     */
    private static long slotBytes(Document document) {
        long size = 0;
        for (ExtensionResult slot : document.getExtensionsList()) {
            size += CodedOutputStream.computeMessageSize(Document.EXTENSIONS_FIELD_NUMBER,
                    slot.hasPayload() ? slot.toBuilder().clearPayload().build() : slot);
        }
        return size;
    }

    @Override
    public void close() throws InterruptedException {
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
        }
        for (ManagedChannel channel : channels) {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
