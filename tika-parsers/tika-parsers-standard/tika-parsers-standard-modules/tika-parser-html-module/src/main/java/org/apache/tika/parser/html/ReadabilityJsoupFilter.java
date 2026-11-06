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
package org.apache.tika.parser.html;

import java.util.ArrayList;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.OperatorOnly;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.ReadabilityMetadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.readability.Article;
import org.apache.tika.readability.Readability;
import org.apache.tika.readability.ReadabilityLimitException;
import org.apache.tika.readability.ReadabilityOptions;
import org.apache.tika.readability.Readerable;

/**
 * Replaces the body with the main content found by Mozilla's Readability algorithm
 * ({@code tika-readability}); the head, and so the HTML metadata, is untouched. Readability returns
 * a best attempt for nearly any page, so by default it runs only where {@code isProbablyReaderable}
 * says the page is an article, as Firefox does. Otherwise, or when nothing is found, a limit is
 * hit, or Readability fails, the document is emitted whole.
 * {@link ReadabilityMetadata#STATUS} records which.
 *
 * @since Apache Tika 4.3
 */
@TikaComponent(name = "readability-jsoup-filter", spi = false, contextKey = JsoupFilter.class)
public class ReadabilityJsoupFilter implements JsoupFilter {

    private static final long serialVersionUID = 1L;

    // Cost grows with elements x nesting depth; these bound the worst inputs found to ~3s.
    public static final int DEFAULT_MAX_ELEMENTS = 20_000;
    public static final long DEFAULT_MAX_TEXT_SCANNED = 50_000_000L;

    private int maxElements = DEFAULT_MAX_ELEMENTS;
    private long maxTextScanned = DEFAULT_MAX_TEXT_SCANNED;
    private int charThreshold = ReadabilityOptions.DEFAULT_CHAR_THRESHOLD;
    private boolean keepClasses = false;
    private boolean requireReaderable = true;

    @Override
    public Document filter(Document document, Metadata metadata, ParseContext context) {
        Document working = document.clone();
        removeComments(working);
        // TODO if requested: operator CSS selectors to keep/remove elements here, for site-specific crawls
        boolean readerable = Readerable.isProbablyReaderable(working);
        metadata.set(ReadabilityMetadata.READERABLE, readerable);
        if (requireReaderable && !readerable) {
            metadata.set(ReadabilityMetadata.STATUS, ReadabilityMetadata.STATUS_NOT_READERABLE);
            return document;
        }
        ReadabilityOptions options = new ReadabilityOptions().maxElemsToParse(maxElements)
                .maxTextScanned(maxTextScanned).charThreshold(charThreshold).keepClasses(keepClasses)
                .documentUri(metadata.get(HttpHeaders.CONTENT_LOCATION));
        Article article;
        try {
            article = new Readability(working, options).parse();
        } catch (ReadabilityLimitException e) {
            metadata.set(ReadabilityMetadata.STATUS, ReadabilityMetadata.STATUS_LIMIT);
            return document;
        } catch (RuntimeException e) {
            metadata.set(ReadabilityMetadata.STATUS, ReadabilityMetadata.STATUS_ERROR);
            ParseRecord record = context.get(ParseRecord.class);
            if (record != null) {
                record.addWarning("readability failed: " + e);
            }
            return document;
        }
        if (article == null) {
            metadata.set(ReadabilityMetadata.STATUS, ReadabilityMetadata.STATUS_NO_ARTICLE);
            return document;
        }
        metadata.set(ReadabilityMetadata.STATUS, ReadabilityMetadata.STATUS_EXTRACTED);
        setIfPresent(metadata, ReadabilityMetadata.TITLE, article.title());
        setIfPresent(metadata, ReadabilityMetadata.BYLINE, article.byline());
        setIfPresent(metadata, ReadabilityMetadata.EXCERPT, article.excerpt());
        setIfPresent(metadata, ReadabilityMetadata.SITE_NAME, article.siteName());
        setIfPresent(metadata, ReadabilityMetadata.PUBLISHED_TIME, article.publishedTime());
        setIfPresent(metadata, ReadabilityMetadata.LANG, article.lang());
        setIfPresent(metadata, ReadabilityMetadata.DIR, article.dir());
        document.body().empty().appendChild(article.content());
        return document;
    }

    private static void setIfPresent(Metadata metadata, org.apache.tika.metadata.Property p, String v) {
        if (v != null && !v.isEmpty()) {
            metadata.set(p, v);
        }
    }

    // a comment splits a phrasing run, so a paragraph broken by one is cut mid-sentence
    private static void removeComments(Node node) {
        for (Node child : new ArrayList<>(node.childNodes())) {
            if (child instanceof Comment) {
                child.remove();
            } else {
                removeComments(child);
            }
        }
    }

    public int getMaxElements() {
        return maxElements;
    }

    /** Documents with more elements are emitted whole. Must be positive. */
    @OperatorOnly
    public void setMaxElements(int maxElements) {
        if (maxElements <= 0) {
            throw new IllegalArgumentException("maxElements must be > 0");
        }
        this.maxElements = maxElements;
    }

    public long getMaxTextScanned() {
        return maxTextScanned;
    }

    /** Characters of text Readability may read across its passes before giving up. Must be positive. */
    @OperatorOnly
    public void setMaxTextScanned(long maxTextScanned) {
        if (maxTextScanned <= 0) {
            throw new IllegalArgumentException("maxTextScanned must be > 0");
        }
        this.maxTextScanned = maxTextScanned;
    }

    public int getCharThreshold() {
        return charThreshold;
    }

    /** Fewest characters an attempt must yield before Readability relaxes its rules and retries. */
    public void setCharThreshold(int charThreshold) {
        this.charThreshold = charThreshold;
    }

    public boolean isRequireReaderable() {
        return requireReaderable;
    }

    /** Run Readability only on pages {@code isProbablyReaderable} accepts. Default true. */
    public void setRequireReaderable(boolean requireReaderable) {
        this.requireReaderable = requireReaderable;
    }

    public boolean isKeepClasses() {
        return keepClasses;
    }

    public void setKeepClasses(boolean keepClasses) {
        this.keepClasses = keepClasses;
    }
}
