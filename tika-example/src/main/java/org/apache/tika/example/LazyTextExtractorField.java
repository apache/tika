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

package org.apache.tika.example;

import java.io.InputStream;
import java.io.Reader;
import java.util.concurrent.Executor;

import org.apache.jackrabbit.core.query.lucene.FieldNames;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <code>LazyTextExtractorField</code> implements a Lucene field with a String
 * value that is lazily initialized from a given {@link Reader}. In addition
 * this class provides a method to find out whether the purpose of the reader is
 * to extract text and whether the extraction process is already finished.
 *
 * @see #isExtractorFinished()
 */
@SuppressWarnings("serial")
public class LazyTextExtractorField extends AbstractField {
    /**
     * The logger instance for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LazyTextExtractorField.class);

    /**
     * The exception used to forcibly terminate the extraction process when the
     * maximum field length is reached.
     * <p>
     * Such exceptions shouldn't be used in logging since its stack trace is meaningless.
     */
    private static final SAXException STOP = new SAXException("max field length reached");

    /**
     * The extracted text content of the given binary value. Set to non-null
     * when the text extraction task finishes.
     */
    private volatile String extract = null;

    /**
     * Creates a new <code>LazyTextExtractorField</code> with the given
     * <code>name</code>.
     *
     * @param name         the name of the field.
     * @param reader       the reader where to obtain the string from.
     * @param highlighting set to <code>true</code> to enable result highlighting support
     */
    public LazyTextExtractorField(Parser parser, InternalValue value,
                                  Metadata metadata, Executor executor, boolean highlighting,
                                  int maxFieldLength) {
        super(FieldNames.FULLTEXT, highlighting ? Store.YES : Store.NO,
                Field.Index.ANALYZED, highlighting ? TermVector.WITH_OFFSETS
                        : TermVector.NO);
        executor.execute(new ParsingTask(parser, value, metadata,
                maxFieldLength));
    }

    /**
     * Returns the extracted text. This method blocks until the text extraction
     * task has been completed.
     *
     * @return the string value of this field
     */
    public synchronized String stringValue() {
        try {
            while (!isExtractorFinished()) {
                wait();
            }
            return extract;
        } catch (InterruptedException e) {
            LOG.error("Text extraction thread was interrupted", e);
            return "";
        }
    }

    /**
     * @return always <code>null</code>
     */
    public Reader readerValue() {
        return null;
    }

    /**
     * @return always <code>null</code>
     */
    public byte[] binaryValue() {
        return null;
    }

    /**
     * @return always <code>null</code>
     */
    public TokenStream tokenStreamValue() {
        return null;
    }

    /**
     * Checks whether the text extraction task has finished.
     *
     * @return <code>true</code> if the extracted text is available
     */
    public boolean isExtractorFinished() {
        return extract != null;
    }

    private synchronized void setExtractedText(String value) {
        extract = value;
        notify();
    }

    /**
     * Releases all resources associated with this field.
     */
    public void dispose() {
        // TODO: Cause the ContentHandler below to throw an exception
    }

    /**
     * The background task for extracting text from a binary value.
     */
    private class ParsingTask extends DefaultHandler implements Runnable {
        private final Parser parser;

        private final InternalValue value;

        private final Metadata metadata;

        private final int maxFieldLength;

        private final StringBuilder builder = new StringBuilder();

        private final ParseContext context = new ParseContext();

        // NOTE: not a part of Jackrabbit code, made
        private final ContentHandler handler = new DefaultHandler();

        public ParsingTask(Parser parser, InternalValue value,
                           Metadata metadata, int maxFieldLength) {
            this.parser = parser;
            this.value = value;
            this.metadata = metadata;
            this.maxFieldLength = maxFieldLength;
        }

        public void run() {
            try {
                try (InputStream stream = value.getStream()) {
                    parser.parse(stream, handler, metadata, context);
                }
            } catch (LinkageError e) {
                // Capture and ignore
            } catch (Throwable t) {
                if (t != STOP) {
                    LOG.debug("Failed to extract text.", t);
                    setExtractedText("TextExtractionError");
                    return;
                }
            } finally {
                value.discard();
            }
            setExtractedText(handler.toString());

        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            builder.append(ch, start,
                    Math.min(length, maxFieldLength - builder.length()));
            if (builder.length() >= maxFieldLength) {
                throw STOP;
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length)
                throws SAXException {
            characters(ch, start, length);
        }
    }
}
