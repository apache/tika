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
package org.apache.tika.parser.inference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.TransientParseState;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedMetadataLookup;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.hook.ParseHook;
import org.apache.tika.parser.hook.ParseHooks;

/**
 * Matches offered units against the inference bindings and runs the tasks. Built once at
 * config load and run as a {@link ParseHook}: every document, top-level or embedded, is
 * offered once by the auto-detect parser. Units are buffered per binding for the whole top-level parse and flushed at its end, so a
 * document is one request per binding, not one per unit; the buffer is bounded by each
 * binding's {@code maxChunks} and {@code maxBytes}, and its bytes live in files the
 * dispatcher owns until the flush. At the flush every unit is aimed at the metadata that is
 * still read: under the recursive wrapper, the copy it kept for the unit's document (an
 * inline part or a page render lands on its parent); outside it, where the parse hands back
 * one metadata object and the embedded documents' own are discarded, the top-level document.
 * A request narrows the bindings through {@link InferenceSelection}. Failures mark the
 * document and never fail the parse. TEXT is not offered during the parse: whoever holds the
 * finished metadata list runs {@link #text} over it.
 *
 * @since Apache Tika 4.1
 */
public final class InferenceDispatcher implements ParseHook, TransientParseState {

    static final int MAX_MEDIA_UNITS = 1000;
    private static final Logger LOG = LoggerFactory.getLogger(InferenceDispatcher.class);
    private static final InferenceSelection ALL = new InferenceSelection();

    /** A binding resolved to its engine and tasks. */
    public record Bound(InferenceBinding binding, Engine engine, List<InferenceTask> tasks) {
    }

    private final List<Bound> bound;

    public InferenceDispatcher(List<Bound> bound) {
        this.bound = List.copyOf(bound);
    }

    public List<Bound> getBound() {
        return bound;
    }

    /**
     * Resolves the request's selection once, at the top of the parse, so a misnamed binding
     * fails the request rather than one embedded document.
     */
    @Override
    public void start(Metadata root, ParseContext context) throws TikaException {
        state(context).selected(context, bound);
    }

    @Override
    public boolean wants(MediaType type, Metadata metadata, ParseContext context)
            throws TikaException {
        InputKind kind = kindOf(type, metadata);
        return kind != null && wants(kind, type, context);
    }

    @Override
    public void offer(MediaType type, Metadata metadata, Metadata parent, Path bytes,
                      ParseContext context) throws IOException, TikaException {
        InputKind kind = kindOf(type, metadata);
        if (kind != null) {
            offer(kind, type, metadata, parent, bytes, context);
        }
    }

    @Override
    public boolean wantsPages(MediaType renderType, Metadata document, ParseContext context)
            throws TikaException {
        return wants(InputKind.PAGES, renderType, context);
    }

    @Override
    public void offerPage(MediaType type, Metadata document, Metadata parent, int page,
                          Path bytes, ParseContext context) throws IOException, TikaException {
        offer(InputKind.PAGES, type, document, parent, bytes, page, context);
    }

    /** A page render emitted as an embedded document is a page, not an image: PAGES only. */
    private static InputKind kindOf(MediaType type, Metadata metadata) {
        if (TikaCoreProperties.EmbeddedResourceType.RENDERING.name()
                .equals(metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE))) {
            return null;
        }
        return InputKind.of(type);
    }

    /** Runs the buffered units, or drops them when the parse failed: no engine call for a document nobody gets. */
    @Override
    public void end(Metadata root, boolean failed, ParseContext context) {
        if (failed) {
            discard(context);
        } else {
            flush(root, context);
        }
    }

    /** Whether any binding that runs for this request takes this kind and type. */
    public boolean wants(InputKind kind, MediaType type, ParseContext context)
            throws TikaException {
        for (Bound b : bound) {
            if (runs(b.binding(), context) && b.binding().accepts(kind, type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Buffers the bytes at {@code source} for every binding that takes them; dropped past a
     * binding's budget. The top-level document's own file is referenced, since the stream
     * that owns it is open until after the flush; anything else (an embedded document's
     * spool, a page render) is copied into a file the dispatcher owns before its parser
     * deletes it.
     */
    public void offer(InputKind kind, MediaType type, Metadata target, Metadata parent,
                      Path source, ParseContext context) throws IOException, TikaException {
        offer(kind, type, target, parent, source, -1, context);
    }

    private void offer(InputKind kind, MediaType type, Metadata target, Metadata parent,
                       Path source, int page, ParseContext context)
            throws IOException, TikaException {
        State state = state(context);
        long size = Files.size(source);
        InferenceUnit unit = null;
        for (Bound b : bound) {
            InferenceBinding binding = b.binding();
            if (!runs(binding, context) || !binding.accepts(kind, type, target)) {
                continue;
            }
            // a media unit is the whole file; maxBytes and maxChunks bound its segments, in the task
            if (kind != InputKind.MEDIA && binding.getMaxBytes() >= 0 && size > binding.getMaxBytes()) {
                state.dropped.merge(binding.getId() + " over maxBytes", 1, Integer::sum);
                continue;
            }
            List<InferenceUnit> units = state.byBinding.computeIfAbsent(binding.getId(),
                    k -> new ArrayList<>());
            if (kind != InputKind.MEDIA && binding.getMaxChunks() >= 0
                    && units.size() >= binding.getMaxChunks()) {
                state.dropped.merge(binding.getId() + " over maxChunks", 1, Integer::sum);
                continue;
            }
            // a media unit is a held copy of a whole file; bound how many one tree may hold
            if (kind == InputKind.MEDIA && units.size() >= MAX_MEDIA_UNITS) {
                state.dropped.merge(binding.getId() + " over " + MAX_MEDIA_UNITS + " media files",
                        1, Integer::sum);
                continue;
            }
            if (unit == null) {
                ParseHooks.Run run = context.get(ParseHooks.Run.class);
                unit = new InferenceUnit(kind, type, target, parent, idPath(target, run),
                        parent == null ? null : idPath(parent, run),
                        held(source, parent, page, state), page);
            }
            units.add(unit);
        }
    }

    /** The source itself for the top-level document's bytes; a dispatcher-owned copy otherwise. */
    private static Path held(Path source, Metadata parent, int page, State state)
            throws IOException {
        if (parent == null && page < 0) {
            return source;
        }
        Path copy = state.tmp.createTempFile();
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }

    /** Runs every binding's tasks over its buffered units; clears the buffer and its files. */
    public void flush(Metadata root, ParseContext context) {
        State state = context.get(State.class);
        if (state == null) {
            return;
        }
        context.set(State.class, null);
        try {
            EmbeddedMetadataLookup lookup = context.get(EmbeddedMetadataLookup.class);
            for (Bound b : bound) {
                List<InferenceUnit> units = state.byBinding.get(b.binding().getId());
                if (units == null || units.isEmpty()) {
                    continue;
                }
                List<InferenceUnit> kept = aim(units, root, lookup);
                for (InferenceTask task : b.tasks()) {
                    try {
                        task.run(b.binding(), kept, b.engine(), context);
                    } catch (Exception e) {
                        LOG.warn("inference binding {} failed", b.binding().getId(), e);
                        root.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                                "inference binding " + b.binding().getId() + ": " + e.getMessage());
                    }
                }
            }
            for (Map.Entry<String, Integer> e : state.dropped.entrySet()) {
                root.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        "inference binding " + e.getKey() + ": skipped " + e.getValue() + " units");
            }
        } finally {
            try {
                state.tmp.close();
            } catch (IOException e) {
                LOG.warn("could not delete inference temp files", e);
            }
        }
    }

    /** Clears the buffer and its files without running anything. */
    public void discard(ParseContext context) {
        State state = context.get(State.class);
        if (state == null) {
            return;
        }
        context.set(State.class, null);
        try {
            state.tmp.close();
        } catch (IOException e) {
            LOG.warn("could not delete inference temp files", e);
        }
    }

    /** The wrapper's id path, or the run's for a document it did not number; null at the top. */
    private static String idPath(Metadata metadata, ParseHooks.Run run) {
        String idPath = metadata.get(TikaCoreProperties.EMBEDDED_ID_PATH);
        return idPath != null || run == null ? idPath : run.idPath(metadata);
    }

    /**
     * Aims units at the metadata still read. Under the wrapper that is the copy it kept for
     * the unit's document and parent, the live object where nothing is kept; without one the
     * parse hands back {@code root} alone, so everything lands there.
     */
    private static List<InferenceUnit> aim(List<InferenceUnit> units, Metadata root,
                                           EmbeddedMetadataLookup lookup) {
        List<InferenceUnit> aimed = new ArrayList<>(units.size());
        for (InferenceUnit unit : units) {
            if (lookup == null) {
                aimed.add(unit.aimed(unit.getTarget(), unit.getParent(), root));
                continue;
            }
            Metadata target = lookup.kept(unit.getTargetIdPath());
            Metadata parent = lookup.kept(unit.getParentIdPath());
            target = target != null ? target : unit.getTarget();
            parent = parent != null ? parent : unit.getParent();
            aimed.add(unit.aimed(target, parent,
                    InferenceUnit.placed(target, parent, unit.getTargetIdPath())));
        }
        return aimed;
    }

    /**
     * The {@link InputKind#TEXT} stage. Runs the TEXT bindings over the finished metadata list of
     * a document tree, so every document's text goes to the engine in one pass and a batch may
     * hold chunks of the container and of its attachments alike. Call it after the parse and
     * before any metadata filters. A document that released other inputs to inference
     * ({@link TikaCoreProperties#INFERENCE_RELEASED} without TEXT) is skipped. Failures mark
     * the first document of the list.
     */
    public void text(List<Metadata> metadataList, ParseContext context) throws TikaException {
        if (metadataList.isEmpty()) {
            return;
        }
        Metadata root = metadataList.get(0);
        for (Bound b : running(InputKind.TEXT, context)) {
            InferenceBinding binding = b.binding();
            List<InferenceUnit> units = new ArrayList<>();
            int skipped = 0;
            for (Metadata metadata : metadataList) {
                String text = metadata.get(TikaCoreProperties.TIKA_CONTENT);
                if (text == null || text.isBlank() || !releasedText(metadata)) {
                    continue;
                }
                MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
                if (!binding.accepts(InputKind.TEXT,
                        type == null ? MediaType.OCTET_STREAM : type)) {
                    continue;
                }
                if (binding.getMaxBytes() >= 0
                        && text.getBytes(StandardCharsets.UTF_8).length > binding.getMaxBytes()) {
                    skipped++;
                    continue;
                }
                units.add(new InferenceUnit(type, metadata, null, text));
            }
            if (skipped > 0) {
                root.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, "inference binding "
                        + binding.getId() + " over maxBytes: skipped " + skipped + " units");
            }
            if (units.isEmpty()) {
                continue;
            }
            for (InferenceTask task : b.tasks()) {
                try {
                    task.run(binding, units, b.engine(), context);
                } catch (Exception e) {
                    LOG.warn("inference binding {} failed", binding.getId(), e);
                    root.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            "inference binding " + binding.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    /** Absent means the parser released its text; a list without TEXT means it did not. */
    private static boolean releasedText(Metadata metadata) {
        String[] released = metadata.getValues(TikaCoreProperties.INFERENCE_RELEASED);
        if (released.length == 0) {
            return true;
        }
        for (String r : released) {
            if (InputKind.TEXT.name().equals(r)) {
                return true;
            }
        }
        return false;
    }

    /** The bindings of this kind that run for the request: enabled and selected. */
    private List<Bound> running(InputKind kind, ParseContext context) throws TikaException {
        List<Bound> result = new ArrayList<>();
        for (Bound b : bound) {
            if (b.binding().getInput() == kind && runs(b.binding(), context)) {
                result.add(b);
            }
        }
        return result;
    }

    private boolean runs(InferenceBinding binding, ParseContext context) throws TikaException {
        if (!binding.isEnabled()) {
            return false;
        }
        Set<String> selected = state(context).selected(context, bound);
        return selected == null || selected.contains(binding.getId());
    }

    private static State state(ParseContext context) {
        State state = context.get(State.class);
        if (state == null) {
            state = new State();
            context.set(State.class, state);
        }
        return state;
    }

    /** Per-parse buffer; lives in the context from the first offer to the flush. */
    static final class State implements TransientParseState {
        final Map<String, List<InferenceUnit>> byBinding = new LinkedHashMap<>();
        final Map<String, Integer> dropped = new LinkedHashMap<>();
        final TemporaryResources tmp = new TemporaryResources();
        private Set<String> selected;
        private boolean resolved;

        /** The binding ids this request runs, resolved once; null means every enabled one. */
        Set<String> selected(ParseContext context, List<Bound> bound) throws TikaException {
            if (resolved) {
                return selected;
            }
            InferenceSelection selection = context.get(InferenceSelection.class);
            if (selection == null) {
                try {
                    selection = ParseContextConfig.getConfig(context, "inference",
                            InferenceSelection.class, ALL);
                } catch (TikaConfigException | IOException e) {
                    throw new TikaException("invalid \"inference\" in parse-context", e);
                }
            }
            Set<String> result;
            if (!selection.isEnabled()) {
                result = Set.of();
            } else if (selection.getBindings().isEmpty()) {
                result = null;
            } else {
                Set<String> known = new HashSet<>();
                for (Bound b : bound) {
                    known.add(b.binding().getId());
                }
                for (String id : selection.getBindings()) {
                    if (!known.contains(id)) {
                        throw new TikaException("\"inference\" in parse-context names binding \""
                                + id + "\", which is not configured; configured: " + known);
                    }
                }
                result = new HashSet<>(selection.getBindings());
            }
            selected = result;
            resolved = true;
            return selected;
        }
    }
}
