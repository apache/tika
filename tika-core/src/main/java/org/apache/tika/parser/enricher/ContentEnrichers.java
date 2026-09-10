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
package org.apache.tika.parser.enricher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.utils.ParserUtils;

/**
 * Resolves the content enricher for a media type.
 * <p>
 * Call sites: wrap the handler (an {@code EmbeddedContentHandler} over a
 * {@code BodyContentHandler}) so the enricher cannot dump structure or metadata into the
 * caller's XHTML; resolve on the <em>detected</em> type, captured before a parser can
 * refine Content-Type mid-parse; pass the target's metadata, or a probe carrying its
 * {@code Content-Type} and {@code EMBEDDED_RESOURCE_TYPE} when resolving ahead of a render;
 * and pass the caller's own {@link ParseContext} through -- the recursion guard rides it, so
 * a fresh context defeats it.
 *
 * @since Apache Tika 4.1
 */
public final class ContentEnrichers {

    private static final Logger LOG = LoggerFactory.getLogger(ContentEnrichers.class);

    private static final String LEGACY_OCR_PREFIX = "ocr-";

    // one WARN per key; keys are class names and engine sets, so bounded
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ContentEnrichers() {
    }

    /**
     * The enricher to invoke for a media type, or null. A configured list is authoritative:
     * every matching member runs in order and an uncovered type gets nothing. With no list,
     * the {@link ContentEnricher}s in the composite bound to the context are candidates and
     * one runs, chosen as {@link #resolve} chooses. Null while an enrichment is in progress
     * in this context, so an enricher cannot recurse.
     *
     * @param enrichers the injected composite; null when none is configured
     * @param mediaType the real, normalized media type of the bytes; may be null
     * @param target    the metadata of the image the engine will be handed, or a probe for
     *                  a render not yet made; dispatch reads only facts from it
     */
    public static Parser get(CompositeContentEnricher enrichers, MediaType mediaType,
                             Metadata target, ParseContext context) {
        Objects.requireNonNull(target, "target");
        return select(enrichers, mediaType, context);
    }

    /**
     * Suspends dispatch in this context until the returned scope closes. A container that
     * enriches a rendering itself wraps the embedded parse of that rendering, so the embedded
     * copy is not enriched a second time. Scopes nest; each restores the state it found.
     */
    public static Suspension suspend(ParseContext context) {
        return new Suspension(context, false);
    }

    /**
     * Suspends text recognizers only, until the returned scope closes: {@link #get} returns
     * the annotators (embedders, taggers, captioners) and {@link #hasTextRecognizer} is
     * false. A recognizer that declines this parse is still a recognizer, not an annotator.
     * Scopes nest as for {@link #suspend}.
     */
    public static Suspension suspendRecognizers(ParseContext context) {
        return new Suspension(context, true);
    }

    private static Parser select(CompositeContentEnricher enrichers, MediaType mediaType,
                                 ParseContext context) {
        if (mediaType == null || isActive(context) || isSuspended(context)) {
            return null;
        }
        boolean annotatorsOnly = isRecognizersSuspended(context);
        if (enrichers != null) {
            List<Parser> matched = enrichers.getEnrichers(mediaType);
            if (annotatorsOnly) {
                List<Parser> annotators = new ArrayList<>();
                for (Parser p : matched) {
                    if (isAnnotator(p, enrichers.isLegacyClaimant(p))) {
                        annotators.add(p);
                    }
                }
                matched = annotators;
            }
            if (matched.isEmpty()) {
                return null;
            }
            return new GuardedEnricher(matched.size() == 1
                    ? matched.get(0) : new SequentialEnricher(matched));
        }
        Candidate discovered = discover(mediaType, context);
        if (discovered == null
                || (annotatorsOnly && !isAnnotator(discovered.parser, discovered.legacy))) {
            return null;
        }
        return new GuardedEnricher(discovered.parser);
    }

    private static boolean isAnnotator(Parser member, boolean legacy) {
        return !legacy && asTextRecognizer(member) == null;
    }

    /**
     * Whether the enricher {@link #get} would return produces the document's text: a
     * {@link TextRecognizer} that recognizes for this context, or a legacy
     * {@code image/ocr-*} claimant. False while an enrichment is in progress.
     * {@code target} is as for {@link #get}.
     */
    public static boolean hasTextRecognizer(CompositeContentEnricher enrichers,
                                            MediaType mediaType, Metadata target,
                                            ParseContext context) {
        Objects.requireNonNull(target, "target");
        if (mediaType == null || isActive(context) || isSuspended(context)
                || isRecognizersSuspended(context)) {
            return false;
        }
        if (enrichers != null) {
            for (Parser p : enrichers.getEnrichers(mediaType)) {
                TextRecognizer recognizer = asTextRecognizer(p);
                if (recognizer != null ? recognizer.recognizesText(context)
                        : enrichers.isLegacyClaimant(p)) {
                    return true;
                }
            }
            return false;
        }
        Candidate discovered = discover(mediaType, context);
        if (discovered == null) {
            return false;
        }
        if (discovered.legacy) {
            return true;
        }
        TextRecognizer recognizer = asTextRecognizer(discovered.parser);
        return recognizer != null && recognizer.recognizesText(context);
    }

    /**
     * One enricher per type from the enrichers found in a parser tree: an engine outside
     * {@link DefaultParser} beats one inside it, the last wins within a tier, and an entry's
     * {@code _mime-include}/{@code _mime-exclude} applies at every node. A collision within
     * the winning tier is logged once. Empty when the tree holds no enricher. This is what
     * the loader injects when no {@code "text-recognizers"} list is configured; the
     * runtime fallback for an unloaded {@code AutoDetectParser} applies the same rules.
     */
    public static CompositeContentEnricher resolve(Parser root) {
        List<Found> found = walk(root, new ParseContext());
        Map<MediaType, Parser> winners = new HashMap<>();
        Set<Parser> legacy = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<MediaType> types = new HashSet<>();
        for (Found f : found) {
            types.addAll(f.types);
        }
        Map<String, Collision> collisions = new LinkedHashMap<>();
        for (MediaType type : types) {
            List<Found> tier = tier(type, found);
            Found winner = tier.get(tier.size() - 1);
            winners.put(type, winner.member);
            if (winner.legacy) {
                legacy.add(winner.member);
            }
            if (tier.size() > 1) {
                collisions.computeIfAbsent(names(tier), k -> new Collision(tier, winner))
                        .types.add(type);
            }
        }
        for (Collision c : collisions.values()) {
            warnCollision(c.types, c.tier, c.winner);
        }
        return CompositeContentEnricher.resolved(winners, legacy);
    }

    /** True if the parser, under any decorators, is a {@link ContentEnricher}. */
    public static boolean isEnricher(Parser parser) {
        return unwrap(parser) instanceof ContentEnricher;
    }

    /** The {@link TextRecognizer} under any decorators; null if the parser is not one. */
    public static TextRecognizer asTextRecognizer(Parser parser) {
        return unwrap(parser) instanceof TextRecognizer recognizer ? recognizer : null;
    }

    /** True for a retired {@code image/ocr-*} pseudo-type. */
    public static boolean isLegacyOcrType(MediaType mediaType) {
        return mediaType != null && mediaType.getSubtype().startsWith(LEGACY_OCR_PREFIX);
    }

    /** The real type behind a retired pseudo-type; any other type unchanged. */
    public static MediaType stripLegacyOcrPrefix(MediaType mediaType) {
        if (!isLegacyOcrType(mediaType)) {
            return mediaType;
        }
        return new MediaType(mediaType.getType(),
                mediaType.getSubtype().substring(LEGACY_OCR_PREFIX.length()));
    }

    static void warnLegacyAdvertisement(Parser engine) {
        String name = engine.getClass().getName();
        if (WARNED.add("legacy:" + name)) {
            LOG.warn("{} advertises retired image/ocr-* pseudo-types; it is treated as a text "
                    + "recognizer for the real types until 5.0. Advertise the real types and "
                    + "implement {}.", name, TextRecognizer.class.getName());
        }
    }

    // invoked, not dispatched to: record it as the composite would
    private static void record(Parser enricher, Metadata metadata, ParseContext context) {
        String className = ParserUtils.getParserClassname(enricher);
        ParserUtils.recordParserDetails(className, metadata);
        ParseRecord parseRecord = context.get(ParseRecord.class);
        if (parseRecord != null) {
            parseRecord.addParserClass(className);
        }
    }

    private static boolean isActive(ParseContext context) {
        ActiveEnrichment active = context.get(ActiveEnrichment.class);
        return active != null && active.active;
    }

    private static boolean isSuspended(ParseContext context) {
        ActiveEnrichment state = context.get(ActiveEnrichment.class);
        return state != null && state.suspended;
    }

    private static boolean isRecognizersSuspended(ParseContext context) {
        ActiveEnrichment state = context.get(ActiveEnrichment.class);
        return state != null && state.recognizersSuspended;
    }

    /** Scope of a {@link #suspend} or {@link #suspendRecognizers}; closing restores the prior state. */
    public static final class Suspension implements AutoCloseable {

        private final ActiveEnrichment state;
        private final boolean wasSuspended;
        private final boolean wereRecognizersSuspended;

        private Suspension(ParseContext context, boolean recognizersOnly) {
            ActiveEnrichment found = context.get(ActiveEnrichment.class);
            if (found == null) {
                found = new ActiveEnrichment();
                context.set(ActiveEnrichment.class, found);
            }
            this.state = found;
            this.wasSuspended = found.suspended;
            this.wereRecognizersSuspended = found.recognizersSuspended;
            if (recognizersOnly) {
                found.recognizersSuspended = true;
            } else {
                found.suspended = true;
            }
        }

        @Override
        public void close() {
            state.suspended = wasSuspended;
            state.recognizersSuspended = wereRecognizersSuspended;
        }
    }

    private static Parser unwrap(Parser parser) {
        while (parser instanceof ParserDecorator decorator) {
            parser = decorator.getWrappedParser();
        }
        return parser;
    }

    private record Candidate(Parser parser, boolean legacy) {
    }

    /** An enricher in the tree, with the real types it may enrich after every filter. */
    private record Found(Parser member, boolean inDefault, boolean legacy, Set<MediaType> types) {
    }

    private static final class Collision {
        final List<Found> tier;
        final Found winner;
        final Set<MediaType> types = new TreeSet<>();

        Collision(List<Found> tier, Found winner) {
            this.tier = tier;
            this.winner = winner;
        }
    }

    private static Candidate discover(MediaType mediaType, ParseContext context) {
        Parser root = EmbeddedDocumentUtil.getStatelessParser(context);
        if (root == null) {
            return null;
        }
        MediaType baseType = mediaType.getBaseType();
        List<Found> found = walk(root, context);
        List<Found> tier = tier(baseType, found);
        if (tier.isEmpty()) {
            return null;
        }
        Found winner = tier.get(tier.size() - 1);
        if (tier.size() > 1) {
            warnCollision(Set.of(baseType), tier, winner);
        }
        return new Candidate(winner.member, winner.legacy);
    }

    // the winning tier for a type: configured members if any claim it, else discovered
    private static List<Found> tier(MediaType type, List<Found> found) {
        List<Found> configured = new ArrayList<>();
        List<Found> discovered = new ArrayList<>();
        for (Found f : found) {
            if (f.types.contains(type)) {
                (f.inDefault ? discovered : configured).add(f);
            }
        }
        return configured.isEmpty() ? discovered : configured;
    }

    private static List<Found> walk(Parser root, ParseContext context) {
        List<Found> found = new ArrayList<>();
        walk(root, context, false, t -> true, found);
        return found;
    }

    private static void walk(Parser parser, ParseContext context, boolean inDefault,
                             Predicate<MediaType> passes, List<Found> found) {
        Parser engine = unwrap(parser);
        Predicate<MediaType> here = parser == engine ? passes
                : passes.and(t -> !filteredOut(parser, engine, t, context));
        if (engine instanceof CompositeParser composite) {
            boolean def = inDefault || engine instanceof DefaultParser;
            for (Parser child : composite.getAllComponentParsers()) {
                walk(child, context, def, here, found);
            }
            return;
        }
        boolean enricher = engine instanceof ContentEnricher;
        Set<MediaType> types = new HashSet<>();
        boolean legacyAdvertised = false;
        for (MediaType advertised : engine.getSupportedTypes(context)) {
            if (isLegacyOcrType(advertised)) {
                legacyAdvertised = true;
            } else if (!enricher) {
                continue;
            }
            MediaType type = stripLegacyOcrPrefix(advertised.getBaseType());
            if (here.test(type)) {
                types.add(type);
            }
        }
        if (legacyAdvertised) {
            warnLegacyAdvertisement(engine);
        }
        if (!types.isEmpty()) {
            found.add(new Found(parser, inDefault, !enricher, types));
        }
    }

    /**
     * Whether the entry's decorators drop this type. A mime filter is asked directly (a
     * real-type filter cannot name a legacy engine's pseudo-type); any other decorator
     * counts by what it removes from the engine's own view.
     */
    private static boolean filteredOut(Parser decorated, Parser engine, MediaType type,
                                       ParseContext context) {
        boolean exact = false;
        for (Parser p = decorated; p instanceof ParserDecorator d; p = d.getWrappedParser()) {
            if (d instanceof ParserDecorator.MimeFilteringDecorator f) {
                exact = true;
                if (f.getExcludeTypes().contains(type) || (!f.getIncludeTypes().isEmpty()
                        && !f.getIncludeTypes().contains(type))) {
                    return true;
                }
            }
        }
        return !exact && engine.getSupportedTypes(context).contains(type)
                && !decorated.getSupportedTypes(context).contains(type);
    }

    private static String names(List<Found> tier) {
        StringBuilder names = new StringBuilder();
        for (Found f : tier) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(unwrap(f.member).getClass().getName());
        }
        return names.toString();
    }

    private static void warnCollision(Set<MediaType> types, List<Found> tier, Found winner) {
        String names = names(tier);
        if (WARNED.add("collision:" + names)) {
            LOG.warn("Several text recognizers claim {}: [{}]; {} is used. Name one in "
                    + "\"text-recognizers\" to choose.", types, names,
                    unwrap(winner.member).getClass().getName());
        }
    }

    /**
     * Runs each enricher in config order, best-effort: the first failure is rethrown once
     * the chain completes, later ones suppressed onto it. Timeouts, SecurityException,
     * SAXException (incl. write-limit aborts) and runtime exceptions abort immediately,
     * carrying any earlier failure -- a spent budget must not fund more enrichments.
     */
    private static final class SequentialEnricher implements Parser {

        private static final long serialVersionUID = 1L;

        private final List<Parser> delegates;

        private SequentialEnricher(List<Parser> delegates) {
            this.delegates = delegates;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return delegates.get(0).getSupportedTypes(context);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            // each delegate gets the bytes from the start; getPath() spools once at most
            Path path = tis.getPath();
            Exception first = null;
            for (Parser delegate : delegates) {
                record(delegate, metadata, context);
                try (TikaInputStream fresh = TikaInputStream.get(path)) {
                    delegate.parse(fresh, handler, metadata, context);
                } catch (SecurityException | TikaTimeoutException | SAXException e) {
                    if (first != null) {
                        e.addSuppressed(first);
                    }
                    throw e;
                } catch (IOException | TikaException e) {
                    if (first == null) {
                        first = e;
                    } else {
                        first.addSuppressed(e);
                    }
                } catch (RuntimeException e) {
                    if (first != null) {
                        e.addSuppressed(first);
                    }
                    throw e;
                }
            }
            if (first instanceof IOException e) {
                throw e;
            }
            if (first instanceof TikaException e) {
                throw e;
            }
        }
    }

    /** Mutable per-parse dispatch state; single-threaded within one parse. */
    static final class ActiveEnrichment {
        boolean active;
        boolean suspended;
        boolean recognizersSuspended;
    }

    /**
     * Marks enrichment in progress so {@link #get} refuses re-entry, and restores
     * Content-Type: an enricher derives content, it does not re-type the document.
     */
    private static final class GuardedEnricher implements Parser {

        private static final long serialVersionUID = 1L;

        private final Parser delegate;

        private GuardedEnricher(Parser delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return delegate.getSupportedTypes(context);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            ActiveEnrichment active = context.get(ActiveEnrichment.class);
            if (active == null) {
                active = new ActiveEnrichment();
                context.set(ActiveEnrichment.class, active);
            }
            String contentType = metadata.get(HttpHeaders.CONTENT_TYPE);
            // the sequential chain records each member itself
            if (!(delegate instanceof SequentialEnricher)) {
                record(delegate, metadata, context);
            }
            // restore, don't clear: a nested call must not lift the outer guard
            boolean wasActive = active.active;
            active.active = true;
            try {
                delegate.parse(tis, handler, metadata, context);
            } finally {
                active.active = wasActive;
                if (contentType == null) {
                    metadata.remove(HttpHeaders.CONTENT_TYPE);
                } else {
                    metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
                }
            }
        }
    }
}
