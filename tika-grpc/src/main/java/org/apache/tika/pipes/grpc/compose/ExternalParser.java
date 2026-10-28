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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;

import org.apache.tika.grpc.adapter.AdapterContribution;
import org.apache.tika.grpc.adapter.AdapterRequest;
import org.apache.tika.grpc.adapter.ChildDraft;
import org.apache.tika.grpc.adapter.ContributionLimits;
import org.apache.tika.grpc.adapter.ExternalParserAdapter;
import org.apache.tika.grpc.mapper.AdapterMerge;
import org.apache.tika.grpc.mapper.DocumentComposer;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.ExtensionResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Calls one external parser through its adapter and merges what the adapter contributed
 * into the Document, under {@link AdapterMerge}'s rules and this entry's limits. Whatever
 * goes wrong (the service is down, rejects the request, misses the deadline, sends a reply
 * over the size limit, or the adapter throws, {@link Error}s such as a
 * {@link LinkageError} or {@link StackOverflowError} included) leaves Tika's Document as it
 * was and gives the slot {@code STATUS_FAILED} with the reason, never an exception: a missing
 * external result must not cost the caller Tika's Document (TIKA-4771 rule 6). Only a
 * {@link VirtualMachineError} other than a stack overflow, such as {@link OutOfMemoryError},
 * is rethrown.
 */
final class ExternalParser<ReqT extends Message, RespT extends Message> {

    private static final int MAX_REASON_CHARS = 1024;

    /** The Document after this parser, and its slot. */
    record Outcome(Document document, ExtensionResult slot) {
    }

    private final ExternalParserAdapter<ReqT, RespT> adapter;
    // Read from the adapter once, at startup, and checked there.
    private final String producerId;
    private final MethodDescriptor<ReqT, RespT> method;
    private final String keyPrefix;
    private final Set<String> contentTypes;
    private final JsonNode options;
    private final long timeoutMs;
    private final Long maxContentBytes;
    private final boolean keepRawReply;
    private final ContributionLimits contributionLimits;
    private final AdapterMerge.Limits mergeLimits;
    private final Channel channel;

    /**
     * Reads and checks what the adapter declares, and has it check its options.
     *
     * @throws IllegalArgumentException if the adapter's declarations or options are not usable
     */
    ExternalParser(ExternalParserConfig config, ExternalParserAdapter<ReqT, RespT> adapter,
                   Channel channel) {
        this.adapter = adapter;
        this.producerId = config.getProducerId();
        if (!producerId.equals(adapter.producerId())) {
            throw new IllegalArgumentException("adapter " + adapter.getClass().getName()
                    + " says its producerId is '" + adapter.producerId() + "', not '"
                    + producerId + "'");
        }
        this.method = adapter.method();
        if (method == null || method.getType() != MethodDescriptor.MethodType.UNARY) {
            throw new IllegalArgumentException("adapter '" + producerId
                    + "' must name a unary method, got: "
                    + (method == null ? null : method.getFullMethodName() + " ("
                    + method.getType() + ")"));
        }
        this.keyPrefix = checkKeyPrefix(producerId, adapter.keyPrefix());
        Set<MediaType> types = adapter.supportedTypes();
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("adapter '" + producerId
                    + "' supports no content types, so it would never be called");
        }
        this.contentTypes = types.stream()
                .map(type -> type.getBaseType().toString())
                .collect(Collectors.toUnmodifiableSet());
        this.options = config.getOptions() == null
                ? JsonNodeFactory.instance.objectNode()
                : config.getOptions();
        try {
            adapter.validateOptions(options);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("external parser '" + producerId
                    + "' has bad options: " + e.getMessage(), e);
        }
        this.timeoutMs = config.getTimeoutMs();
        this.maxContentBytes = config.getMaxContentBytes();
        this.keepRawReply = config.isKeepRawReply();
        this.contributionLimits = config.contributionLimits();
        this.mergeLimits = config.mergeLimits();
        this.channel = channel;
    }

    /** A prefix that ends a namespace and is not one of Tika's own. */
    private static String checkKeyPrefix(String producerId, String prefix) {
        if (prefix == null || prefix.length() < 2
                || !(prefix.endsWith(":") || prefix.endsWith("-"))) {
            throw new IllegalArgumentException("external parser adapter '" + producerId
                    + "' has key prefix '" + prefix + "'; it must end with ':' or '-'");
        }
        String reserved = AdapterMerge.tikaNamespace(prefix);
        if (reserved != null) {
            throw new IllegalArgumentException("external parser adapter '" + producerId
                    + "' has key prefix '" + prefix + "', which overlaps Tika's own '"
                    + reserved + "' keys");
        }
        return prefix;
    }

    String producerId() {
        return producerId;
    }

    String keyPrefix() {
        return keyPrefix;
    }

    /** Whether this parser is called for a document Tika detected as {@code contentType}. */
    boolean accepts(String contentType) {
        return contentType != null && !contentType.isBlank()
                && contentTypes.contains(baseType(contentType));
    }

    /**
     * Sends {@code content} to the parser and merges its contribution into {@code document}.
     * The call's deadline is the earlier of the caller's and {@code timeoutMs} from now; a
     * caller that cancels stops the call, and nothing is mapped or merged after a cancel.
     *
     * @param tikaMetadata    what Tika's parse recorded; the adapter gets a copy
     * @param maxAddedBytes   how many serialized bytes of embedded documents this parser may
     *                        still add, under the request's {@code maxAddedBytes}
     */
    Outcome parse(Document document, ByteString content, Metadata tikaMetadata,
                  String resourceName, long maxAddedBytes) {
        if (maxContentBytes != null && content.size() > maxContentBytes) {
            return new Outcome(document, ExtensionResult.newBuilder()
                    .setProducerId(producerId)
                    .setStatus(ExtensionResult.Status.STATUS_SKIPPED)
                    .addWarnings(content.size() + " bytes is over this parser's limit of "
                            + maxContentBytes)
                    .build());
        }
        String methodName = method.getFullMethodName();
        long start = System.nanoTime();
        Context context = Context.current();
        // Everything that runs adapter code or reads what it made is inside the try: a bug
        // there costs this slot, never Tika's Document.
        try {
            Metadata tikaCopy = new Metadata();
            tikaCopy.putAll(tikaMetadata);
            // Each request gets its own copy, so an adapter that changes its options changes
            // nothing for the next request.
            AdapterRequest request = new AdapterRequest(content, tikaCopy,
                    resourceName == null ? "" : resourceName, document.getContentType(),
                    options.deepCopy(), contributionLimits);
            Deadline deadline = Deadline.after(timeoutMs, TimeUnit.MILLISECONDS);
            if (context.getDeadline() != null) {
                deadline = deadline.minimum(context.getDeadline());
            }
            ReqT built = adapter.buildRequest(request);
            if (context.isCancelled()) {
                return failed(document, "cancelled by the caller", start);
            }
            RespT reply = ClientCalls.blockingUnaryCall(channel, method,
                    CallOptions.DEFAULT.withDeadline(deadline), built);
            if (context.isCancelled()) {
                return failed(document, "cancelled by the caller", start);
            }
            AdapterContribution contribution = adapter.map(reply, request);
            if (contribution == null) {
                return failed(document, "adapter '" + producerId + "' returned no contribution",
                        start);
            }
            if (!contributionLimits.equals(contribution.limits())) {
                return failed(document, "adapter '" + producerId + "' built its contribution"
                        + " with other limits than the request's", start);
            }
            if (context.isCancelled()) {
                return failed(document, "cancelled by the caller", start);
            }
            return merge(document, reply, contribution, tikaMetadata, maxAddedBytes, start);
        } catch (StatusRuntimeException e) {
            return failed(document, methodName + ": " + e.getStatus(), start);
        } catch (VirtualMachineError e) {
            if (!(e instanceof StackOverflowError)) {
                throw e;
            }
            return failed(document, methodName + ": " + e, start);
        } catch (RuntimeException | Error e) {
            return failed(document, methodName + ": " + e, start);
        }
    }

    private Outcome merge(Document document, RespT reply, AdapterContribution contribution,
                          Metadata tikaMetadata, long maxAddedBytes, long start) {
        if (contribution.outcome() == AdapterContribution.Outcome.FAILED) {
            // Nothing of a failed contribution is merged: no parent keys, no children, and
            // no raw reply, whatever keepRawReply says.
            ExtensionResult.Builder slot = DocumentComposer.failed(producerId,
                    bounded(contribution.reason())).toBuilder();
            addWarnings(slot, contribution.warnings());
            return new Outcome(document, slot.setElapsedMs(elapsedMs(start)).build());
        }
        List<AdapterMerge.Child> children = new ArrayList<>(contribution.children().size());
        for (ChildDraft child : contribution.children()) {
            children.add(new AdapterMerge.Child(child.path(), child.metadata(), child.errors()));
        }
        // The entry's own byte limit, or what is left of the request's, whichever is less.
        AdapterMerge.Limits limits = new AdapterMerge.Limits(mergeLimits.maxChildren(),
                mergeLimits.maxKeysPerDocument(), mergeLimits.maxValueChars(),
                Math.max(0, Math.min(mergeLimits.maxEmbeddedBytes(), maxAddedBytes)));
        AdapterMerge.Result merged = AdapterMerge.merge(document, producerId, keyPrefix,
                List.of(tikaMetadata.names()), contribution.parentKeys(), children,
                contribution.reportedChildren(), limits);
        boolean partial = contribution.outcome() == AdapterContribution.Outcome.PARTIAL;
        ExtensionResult.Builder slot = ExtensionResult.newBuilder()
                .setProducerId(producerId)
                .setStatus(partial
                        ? ExtensionResult.Status.STATUS_PARTIAL
                        : ExtensionResult.Status.STATUS_OK)
                .setEmbeddedReported(merged.reported())
                .setEmbeddedKept(merged.kept())
                .setTruncated(merged.truncated());
        if (partial) {
            slot.addWarnings(bounded(contribution.reason()));
        }
        addWarnings(slot, contribution.warnings());
        slot.addAllWarnings(merged.warnings());
        if (keepRawReply) {
            slot.setPayload(Any.pack(reply));
        }
        return new Outcome(merged.document(), slot.setElapsedMs(elapsedMs(start)).build());
    }

    /**
     * The adapter's warnings, held to the same caps its builder applies, whether or not the
     * contribution came from {@link AdapterRequest#contribution()}.
     */
    private void addWarnings(ExtensionResult.Builder slot, List<String> warnings) {
        int max = contributionLimits.maxWarnings();
        for (int i = 0; i < warnings.size() && i < max; i++) {
            slot.addWarnings(cut(warnings.get(i), contributionLimits.maxWarningChars()));
        }
        if (warnings.size() > max) {
            slot.addWarnings((warnings.size() - max) + " more warnings dropped");
        }
    }

    /** Failure text as the rest of v2 reports it: its first line, at most 1024 chars. */
    private static String bounded(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return cut(newline < 0 ? text : text.substring(0, newline).stripTrailing(),
                MAX_REASON_CHARS);
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    private Outcome failed(Document document, String reason, long start) {
        return new Outcome(document, DocumentComposer.failed(producerId, bounded(reason))
                .toBuilder()
                .setElapsedMs(elapsedMs(start))
                .build());
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static String baseType(String contentType) {
        MediaType type = MediaType.parse(contentType.trim());
        return type == null
                ? contentType.trim().toLowerCase(Locale.ROOT)
                : type.getBaseType().toString();
    }
}
