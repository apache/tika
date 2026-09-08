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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * refine Content-Type mid-parse; and pass the caller's own {@link ParseContext} through --
 * the recursion guard rides it, so a fresh context defeats it.
 *
 * @since Apache Tika 4.1
 */
public final class ContentEnrichers {

    private static final Logger LOG = LoggerFactory.getLogger(ContentEnrichers.class);

    /** Retired {@code image/ocr-*} pseudo-type marker; honored as an alias until 5.0. */
    private static final String LEGACY_OCR_PREFIX = "ocr-";

    // bounded by distinct engine class names; keeps the legacy and collision WARNs to once
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ContentEnrichers() {
    }

    /**
     * Returns the enricher to invoke for one media type, or null when none applies.
     * A configured list is authoritative: every matching enricher runs, in config order,
     * behind the Parser returned here, and an uncovered type gets no enrichment -- never a
     * classpath engine nobody named. With no list, the {@link ContentEnricher}s in the
     * composite bound to the context are the candidates and exactly one runs: an engine
     * named under {@code "parsers"} beats one the default parser discovered, and within a
     * tier the last claimant wins, matching composite dispatch (user-supplied classes
     * register after Tika's). Null while an enrichment is already in progress in this
     * context, so an enricher that is (or invokes) a container parser cannot recurse.
     *
     * @param enrichers the injected composite; may be null when none is configured
     * @param mediaType the real, normalized media type of the bytes; may be null
     */
    public static Parser get(CompositeContentEnricher enrichers, MediaType mediaType,
                             ParseContext context) {
        if (mediaType == null || isActive(context)) {
            return null;
        }
        if (enrichers != null) {
            List<Parser> matched = enrichers.getEnrichers(mediaType);
            if (matched.isEmpty()) {
                return null;
            }
            return new GuardedEnricher(matched.size() == 1
                    ? matched.get(0) : new SequentialEnricher(matched));
        }
        Candidate discovered = discover(mediaType, context);
        return discovered == null ? null : new GuardedEnricher(discovered.parser);
    }

    /**
     * Whether the enricher {@link #get} would return for this media type produces the
     * document's text: a {@link TextRecognizer} that recognizes text for this context.
     * Under a configured list any matching member counts; with no list, the discovered
     * engine must be one, or a legacy {@code image/ocr-*} claimant (that pseudo-type was
     * the OCR contract). False while an enrichment is in progress, matching {@link #get}.
     */
    public static boolean hasTextRecognizer(CompositeContentEnricher enrichers,
                                            MediaType mediaType, ParseContext context) {
        if (mediaType == null || isActive(context)) {
            return false;
        }
        if (enrichers != null) {
            for (Parser p : enrichers.getEnrichers(mediaType)) {
                if (p instanceof TextRecognizer recognizer && recognizer.recognizesText(context)) {
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
        return unwrap(discovered.parser) instanceof TextRecognizer recognizer
                && recognizer.recognizesText(context);
    }

    /** True if the parser, under any decorators, is a {@link ContentEnricher}. */
    public static boolean isEnricher(Parser parser) {
        return unwrap(parser) instanceof ContentEnricher;
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

    /** An enricher is invoked, not dispatched to: record it as the composite would. */
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

    private static Parser unwrap(Parser parser) {
        while (parser instanceof ParserDecorator decorator) {
            parser = decorator.getWrappedParser();
        }
        return parser;
    }

    private record Candidate(Parser parser, boolean legacy) {
    }

    private static Candidate discover(MediaType mediaType, ParseContext context) {
        Parser root = EmbeddedDocumentUtil.getStatelessParser(context);
        if (root == null) {
            return null;
        }
        List<Candidate> configured = new ArrayList<>();
        List<Candidate> discovered = new ArrayList<>();
        collect(root, mediaType.getBaseType(), context, false, configured, discovered);
        List<Candidate> tier = configured.isEmpty() ? discovered : configured;
        if (tier.isEmpty()) {
            return null;
        }
        Candidate winner = tier.get(tier.size() - 1);
        if (tier.size() > 1) {
            warnCollision(mediaType, tier, winner);
        }
        return winner;
    }

    private static void collect(Parser parser, MediaType type, ParseContext context,
                                boolean inDefault, List<Candidate> configured,
                                List<Candidate> discovered) {
        Parser engine = unwrap(parser);
        if (engine instanceof CompositeParser composite) {
            boolean def = inDefault || engine instanceof DefaultParser;
            for (Parser child : composite.getAllComponentParsers()) {
                collect(child, type, context, def, configured, discovered);
            }
            return;
        }
        // the decorated view: a _mime-exclude on the entry applies
        Set<MediaType> types = parser.getSupportedTypes(context);
        boolean legacy;
        if (engine instanceof ContentEnricher) {
            if (!types.contains(type)) {
                return;
            }
            legacy = false;
        } else if (types.contains(new MediaType(type.getType(),
                LEGACY_OCR_PREFIX + type.getSubtype()))) {
            warnLegacyAdvertisement(engine);
            legacy = true;
        } else {
            return;
        }
        (inDefault ? discovered : configured).add(new Candidate(parser, legacy));
    }

    private static void warnCollision(MediaType mediaType, List<Candidate> tier,
                                      Candidate winner) {
        StringBuilder names = new StringBuilder();
        for (Candidate c : tier) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(unwrap(c.parser).getClass().getName());
        }
        String winnerName = unwrap(winner.parser).getClass().getName();
        if (WARNED.add(mediaType + ":" + names)) {
            LOG.warn("Several content enrichers claim {}: [{}]; {} is used. Name one in "
                    + "\"content-enrichers\" to choose.", mediaType, names, winnerName);
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

    /** Mutable per-parse marker; single-threaded within one parse. */
    static final class ActiveEnrichment {
        boolean active;
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
