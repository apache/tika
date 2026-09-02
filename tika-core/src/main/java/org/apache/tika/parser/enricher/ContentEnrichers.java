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
import java.util.List;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Resolves the content enricher for a media type.
 * <p>
 * Contract for call sites:
 * <ul>
 *   <li>The caller owns placement: wrap the handler (e.g. an
 *       {@code EmbeddedContentHandler} over a {@code BodyContentHandler}) so the enricher
 *       cannot emit its own document structure or metadata dump into the caller's XHTML.</li>
 *   <li>The caller owns invocation granularity: once per image, per rendered page, per
 *       segment; the slot does not dictate.</li>
 *   <li>Resolve against the <em>detected</em> media type, captured at parse entry before
 *       the parser can refine Content-Type (e.g. a PDF re-typed to Illustrator mid-parse
 *       must still fire the enricher selected for the type it was dispatched on).</li>
 *   <li>The enricher writes into the caller's {@link Metadata}; the caller must not assume
 *       the metadata is untouched beyond the derived content.</li>
 * </ul>
 *
 * @since Apache Tika 4.1
 */
public final class ContentEnrichers {

    private ContentEnrichers() {
    }

    /**
     * Returns the enricher to invoke for one media type, or null when none applies.
     * Explicitly configured enrichers win: every one matching the type runs, in config
     * order, behind the single Parser returned here. Otherwise the legacy
     * {@code image/ocr-*} dispatch through the composite parser applies when an engine
     * claims the synthetic type. Returns null while a enrichment is already in progress
     * in this context, so an enricher that is (or invokes) a container parser cannot
     * recurse into enrichment.
     *
     * @param enrichers  the injected composite; may be null when none is configured
     * @param mediaType the real, normalized media type of the bytes; may be null
     * @param context   the parse context
     */
    public static Parser get(CompositeContentEnricher enrichers, MediaType mediaType,
                             ParseContext context) {
        if (mediaType == null) {
            return null;
        }
        ActiveEnrichment active = context.get(ActiveEnrichment.class);
        if (active != null && active.active) {
            return null;
        }
        if (enrichers != null) {
            List<Parser> matched = enrichers.getEnrichers(mediaType);
            if (!matched.isEmpty()) {
                return new GuardedEnricher(matched.size() == 1
                        ? matched.get(0) : new SequentialEnricher(matched));
            }
        }
        Parser composite = EmbeddedDocumentUtil.getStatelessParser(context);
        if (composite != null && composite.getSupportedTypes(context)
                .contains(LegacyDispatchEnricher.toOcrMediaType(mediaType))) {
            return new GuardedEnricher(new LegacyDispatchEnricher(mediaType));
        }
        return null;
    }

    /**
     * Runs each enricher in config order, best-effort: one enricher's failure does not
     * stop the others. The first failure is rethrown after the chain completes, with
     * later failures attached as suppressed, so call sites report every failure through
     * their existing exception handling. Timeouts, SecurityException and SAXException
     * (incl. write-limit aborts) propagate immediately -- a spent budget or a suspect
     * handler must not fund further enrichments.
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

    /** Marks enrichment in progress around the delegate so {@link #get} refuses re-entry. */
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
            active.active = true;
            try {
                delegate.parse(tis, handler, metadata, context);
            } finally {
                active.active = false;
            }
        }
    }
}
