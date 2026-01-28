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
package org.apache.tika.parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

/**
 * Use this class to store exceptions, warnings and other information
 * during the parse.  This information is added to the parent's metadata
 * after the parse by the {@link CompositeParser}.
 * <p>
 * This class also tracks embedded document processing limits (depth and count)
 * which can be configured via {@link #setMaxEmbeddedDepth(int)} and
 * {@link #setMaxEmbeddedCount(int)}.
 */
public class ParseRecord {

    //hard limits so that specially crafted files
    //don't cause an OOM
    private static int MAX_PARSERS = 100;

    private static final int MAX_EXCEPTIONS = 100;

    private static final int MAX_WARNINGS = 100;

    private static final int MAX_METADATA_LIST_SIZE = 100;

    private int depth = 0;
    private final Set<String> parsers = new LinkedHashSet<>();

    private final List<Exception> exceptions = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    private final List<Metadata> metadataList = new ArrayList<>();

    private boolean writeLimitReached = false;

    // Embedded document tracking
    private int embeddedCount = 0;
    private int maxEmbeddedDepth = -1;
    private int maxEmbeddedCount = -1;
    private boolean throwOnMaxDepth = false;
    private boolean throwOnMaxCount = false;
    private boolean embeddedDepthLimitReached = false;
    private boolean embeddedCountLimitReached = false;

    /**
     * Creates a new ParseRecord configured from EmbeddedLimits in the ParseContext.
     * <p>
     * If EmbeddedLimits is present in the context, the ParseRecord will be configured
     * with those limits. Otherwise, default unlimited values are used.
     *
     * @param context the ParseContext (may be null)
     * @return a new ParseRecord configured from the context
     */
    public static ParseRecord newInstance(ParseContext context) {
        ParseRecord record = new ParseRecord();
        EmbeddedLimits limits = EmbeddedLimits.get(context);
        record.maxEmbeddedDepth = limits.getMaxDepth();
        record.maxEmbeddedCount = limits.getMaxCount();
        record.throwOnMaxDepth = limits.isThrowOnMaxDepth();
        record.throwOnMaxCount = limits.isThrowOnMaxCount();
        return record;
    }

    void beforeParse() {
        depth++;
    }

    void afterParse() {
        depth--;
    }

    public int getDepth() {
        return depth;
    }

    public String[] getParsers() {
        return parsers.toArray(new String[0]);
    }

    void addParserClass(String parserClass) {
        if (parsers.size() < MAX_PARSERS) {
            parsers.add(parserClass);
        }
    }

    public void addException(Exception e) {
        if (exceptions.size() < MAX_EXCEPTIONS) {
            exceptions.add(e);
        }
    }

    public void addWarning(String msg) {
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(msg);
        }
    }

    public void addMetadata(Metadata metadata) {
        if (metadataList.size() < MAX_METADATA_LIST_SIZE) {
            metadataList.add(metadata);
        }
    }

    public void setWriteLimitReached(boolean writeLimitReached) {
        this.writeLimitReached = writeLimitReached;
    }

    public List<Exception> getExceptions() {
        return exceptions;
    }

    public List<String> getWarnings() {
        return warnings;
    }


    public boolean isWriteLimitReached() {
        return writeLimitReached;
    }

    public List<Metadata> getMetadataList() {
        return metadataList;
    }

    /**
     * Checks whether an embedded document should be parsed based on configured limits.
     * This should be called before parsing each embedded document.
     * <p>
     * If throwOnMaxDepth or throwOnMaxCount is true and the respective limit is hit,
     * a TikaException is thrown. Otherwise, returns false and sets the appropriate
     * limit flag.
     * <p>
     * Note: The count limit is a hard stop (once hit, no more embedded docs are parsed).
     * The depth limit only affects documents at that depth - sibling documents at
     * shallower depths will still be parsed.
     *
     * @return true if the embedded document should be parsed, false if limits are exceeded
     * @throws TikaException if a limit is exceeded and throwing is configured
     */
    public boolean shouldParseEmbedded() throws TikaException {
        // Count limit is a hard stop - once we've hit max, no more embedded parsing
        if (embeddedCountLimitReached) {
            return false;
        }
        if (maxEmbeddedCount >= 0 && embeddedCount >= maxEmbeddedCount) {
            embeddedCountLimitReached = true;
            if (throwOnMaxCount) {
                throw new TikaException("Max embedded count reached: " + maxEmbeddedCount);
            }
            return false;
        }

        // Depth limit only applies to current depth - siblings at shallower levels
        // can still be parsed. The flag is set for reporting purposes.
        // depth is 1-indexed (main doc is depth 1), so embedded depth limit of N
        // means we allow parsing up to depth N+1
        if (maxEmbeddedDepth >= 0 && depth > maxEmbeddedDepth) {
            embeddedDepthLimitReached = true;
            if (throwOnMaxDepth) {
                throw new TikaException("Max embedded depth reached: " + maxEmbeddedDepth);
            }
            return false;
        }
        return true;
    }

    /**
     * Increments the embedded document count. Should be called when an embedded
     * document is about to be parsed.
     */
    public void incrementEmbeddedCount() {
        embeddedCount++;
    }

    /**
     * Gets the current count of embedded documents processed.
     *
     * @return the embedded document count
     */
    public int getEmbeddedCount() {
        return embeddedCount;
    }

    /**
     * Sets the maximum depth for parsing embedded documents.
     * A value of -1 means unlimited (the default).
     * A value of 0 means no embedded documents will be parsed.
     * A value of 1 means only first-level embedded documents will be parsed, etc.
     *
     * @param maxEmbeddedDepth the maximum embedded depth, or -1 for unlimited
     */
    public void setMaxEmbeddedDepth(int maxEmbeddedDepth) {
        this.maxEmbeddedDepth = maxEmbeddedDepth;
    }

    /**
     * Gets the maximum depth for parsing embedded documents.
     *
     * @return the maximum embedded depth, or -1 if unlimited
     */
    public int getMaxEmbeddedDepth() {
        return maxEmbeddedDepth;
    }

    /**
     * Sets the maximum number of embedded documents to parse.
     * A value of -1 means unlimited (the default).
     *
     * @param maxEmbeddedCount the maximum embedded count, or -1 for unlimited
     */
    public void setMaxEmbeddedCount(int maxEmbeddedCount) {
        this.maxEmbeddedCount = maxEmbeddedCount;
    }

    /**
     * Gets the maximum number of embedded documents to parse.
     *
     * @return the maximum embedded count, or -1 if unlimited
     */
    public int getMaxEmbeddedCount() {
        return maxEmbeddedCount;
    }

    /**
     * Returns whether the embedded depth limit was reached during parsing.
     *
     * @return true if the depth limit was reached
     */
    public boolean isEmbeddedDepthLimitReached() {
        return embeddedDepthLimitReached;
    }

    /**
     * Returns whether the embedded count limit was reached during parsing.
     *
     * @return true if the count limit was reached
     */
    public boolean isEmbeddedCountLimitReached() {
        return embeddedCountLimitReached;
    }
}
