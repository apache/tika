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
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

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

    private ContentEnrichers() {
    }

    /**
     * Returns the enricher to invoke for one media type, or null when none applies.
     * A configured list is authoritative: every matching enricher runs, in config order,
     * behind the Parser returned here, and an uncovered type gets no enrichment -- never a
     * classpath engine nobody named. Legacy {@code image/ocr-*} dispatch applies only when
     * no list is configured. Null while an enrichment is already in progress in this
     * context, so an enricher that is (or invokes) a container parser cannot recurse.
     *
     * @param enrichers the injected composite; may be null when none is configured
     * @param mediaType the real, normalized media type of the bytes; may be null
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
            if (matched.isEmpty()) {
                return null;
            }
            return new GuardedEnricher(matched.size() == 1
                    ? matched.get(0) : new SequentialEnricher(matched));
        }
        Parser composite = EmbeddedDocumentUtil.getStatelessParser(context);
        if (composite != null && composite.getSupportedTypes(context)
                .contains(LegacyDispatchEnricher.toOcrMediaType(mediaType))) {
            return new GuardedEnricher(new LegacyDispatchEnricher(mediaType, composite));
        }
        return null;
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
