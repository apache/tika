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
package org.apache.tika.extractor;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.CorruptedFileException;
import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;

/**
 * Helper class for parsers of package archives or other compound document
 * formats that support embedded or attached component documents.
 *
 * @since Apache Tika 0.8
 */
public class ParsingEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    private static final File ABSTRACT_PATH = new File("");

    private static final Parser DELEGATING_PARSER = new DelegatingParser();

    private boolean writeFileNameToContent = true;

    protected final ParseContext context;

    public ParsingEmbeddedDocumentExtractor(ParseContext context) {
        this.context = context;
    }

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        // Check ParseRecord for depth/count limits first
        ParseRecord parseRecord = context.get(ParseRecord.class);
        if (parseRecord != null && !checkEmbeddedLimits(parseRecord)) {
            return false;
        }

        // Then check DocumentSelector for content-based filtering
        DocumentSelector selector = context.get(DocumentSelector.class);
        if (selector != null) {
            return selector.select(metadata);
        }

        // Then check FilenameFilter
        FilenameFilter filter = context.get(FilenameFilter.class);
        if (filter != null) {
            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (name != null) {
                return filter.accept(ABSTRACT_PATH, name);
            }
        }

        return true;
    }

    /**
     * Checks embedded document limits from ParseRecord.
     * <p>
     * If throwOnMaxDepth or throwOnMaxCount is configured and the respective limit is hit,
     * an EmbeddedLimitReachedException is thrown. Otherwise, returns false and sets the
     * appropriate limit flag on the ParseRecord.
     * <p>
     * Note: The count limit is a hard stop (once hit, no more embedded docs are parsed).
     * The depth limit only affects documents at that depth - sibling documents at
     * shallower depths will still be parsed.
     *
     * @param parseRecord the parse record to check
     * @return true if the embedded document should be parsed, false if limits are exceeded
     * @throws EmbeddedLimitReachedException if a limit is exceeded and throwing is configured
     */
    private boolean checkEmbeddedLimits(ParseRecord parseRecord) {
        // Count limit is a hard stop - once we've hit max, no more embedded parsing
        if (parseRecord.isEmbeddedCountLimitReached()) {
            return false;
        }
        int maxCount = parseRecord.getMaxEmbeddedCount();
        if (maxCount >= 0 && parseRecord.getEmbeddedCount() >= maxCount) {
            parseRecord.setEmbeddedCountLimitReached(true);
            if (parseRecord.isThrowOnMaxCount()) {
                throw new EmbeddedLimitReachedException(
                        EmbeddedLimitReachedException.LimitType.MAX_COUNT, maxCount);
            }
            return false;
        }

        // Depth limit only applies to current depth - siblings at shallower levels
        // can still be parsed. The flag is set for reporting purposes.
        // depth is 1-indexed (main doc is depth 1), so embedded depth limit of N
        // means we allow parsing up to depth N+1
        int maxDepth = parseRecord.getMaxEmbeddedDepth();
        if (maxDepth >= 0 && parseRecord.getDepth() > maxDepth) {
            parseRecord.setEmbeddedDepthLimitReached(true);
            if (parseRecord.isThrowOnMaxDepth()) {
                throw new EmbeddedLimitReachedException(
                        EmbeddedLimitReachedException.LimitType.MAX_DEPTH, maxDepth);
            }
            return false;
        }
        return true;
    }

    @Override
    public void parseEmbedded(
            TikaInputStream tis, ContentHandler handler, Metadata metadata, ParseContext parseContext, boolean outputHtml)
            throws SAXException, IOException {
        // Increment embedded count for tracking
        ParseRecord parseRecord = context.get(ParseRecord.class);
        if (parseRecord != null) {
            parseRecord.incrementEmbeddedCount();
        }

        if (outputHtml) {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "package-entry");
            handler.startElement(XHTML, "div", "div", attributes);
        }

        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (writeFileNameToContent && name != null && name.length() > 0 && outputHtml) {
            handler.startElement(XHTML, "h1", "h1", new AttributesImpl());
            char[] chars = name.toCharArray();
            handler.characters(chars, 0, chars.length);
            handler.endElement(XHTML, "h1", "h1");
        }

        // Use the delegate parser to parse this entry
        try {
            tis.setCloseShield();
            DELEGATING_PARSER.parse(tis, new EmbeddedContentHandler(new BodyContentHandler(handler)),
                    metadata, context);
        } catch (EncryptedDocumentException ede) {
            recordException(ede, context);
        } catch (CorruptedFileException e) {
            //necessary to stop the parse to avoid infinite loops
            //on corrupt sqlite3 files
            throw new IOException(e);
        } catch (TikaException e) {
            recordException(e, context);
        } finally {
            tis.removeCloseShield();
        }

        if (outputHtml) {
            handler.endElement(XHTML, "div", "div");
        }
    }

    void recordException(Exception e, ParseContext context) {
        ParseRecord record = context.get(ParseRecord.class);
        if (record == null) {
            return;
        }
        record.addException(e);
    }

    public Parser getDelegatingParser() {
        return DELEGATING_PARSER;
    }

    public void setWriteFileNameToContent(boolean writeFileNameToContent) {
        this.writeFileNameToContent = writeFileNameToContent;
    }

    public boolean isWriteFileNameToContent() {
        return writeFileNameToContent;
    }
}
