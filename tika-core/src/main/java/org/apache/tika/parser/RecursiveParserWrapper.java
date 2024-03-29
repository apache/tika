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

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.CorruptedFileException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.SecureContentHandler;
import org.apache.tika.sax.WriteLimiter;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.ParserUtils;

/**
 * This is a helper class that wraps a parser in a recursive handler.
 * It takes care of setting the embedded parser in the ParseContext
 * and handling the embedded path calculations.
 * <p>
 * After parsing a document, call getMetadata() to retrieve a list of
 * Metadata objects, one for each embedded resource.  The first item
 * in the list will contain the Metadata for the outer container file.
 * <p>
 * Content can also be extracted and stored in the {@link TikaCoreProperties#TIKA_CONTENT} field
 * of a Metadata object.  Select the type of content to be stored
 * at initialization.
 * <p>
 * If a WriteLimitReachedException is encountered, the wrapper will stop
 * processing the current resource, and it will not process
 * any of the child resources for the given resource.  However, it will try to
 * parse as much as it can.  If a WLRE is reached in the parent document,
 * no child resources will be parsed.
 * <p>
 * The implementation is based on Jukka's RecursiveMetadataParser
 * and Nick's additions. See:
 * <a href="http://wiki.apache.org/tika/RecursiveMetadata#Jukka.27s_RecursiveMetadata_Parser">RecursiveMetadataParser</a>.
 * <p>
 * Note that this wrapper holds all data in memory and is not appropriate
 * for files with content too large to be held in memory.
 * <p>
 * The unit tests for this class are in the tika-parsers module.
 * </p>
 */
public class RecursiveParserWrapper extends ParserDecorator {

    /**
     * Generated serial version
     */
    private static final long serialVersionUID = 9086536568120690938L;


    private final boolean catchEmbeddedExceptions;

    /**
     * Initialize the wrapper with {@link #catchEmbeddedExceptions} set
     * to <code>true</code> as default.
     *
     * @param wrappedParser parser to use for the container documents and the embedded documents
     */
    public RecursiveParserWrapper(Parser wrappedParser) {
        this(wrappedParser, true);
    }

    /**
     * @param wrappedParser           parser to wrap
     * @param catchEmbeddedExceptions whether or not to catch+record embedded exceptions.
     *                                If set to <code>false</code>, embedded exceptions will be
     *                                thrown and the rest of the file will not be parsed. The
     *                                following will not be ignored:
     *                                {@link CorruptedFileException}, {@link RuntimeException}
     */
    public RecursiveParserWrapper(Parser wrappedParser, boolean catchEmbeddedExceptions) {
        super(wrappedParser);
        this.catchEmbeddedExceptions = catchEmbeddedExceptions;
    }


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return getWrappedParser().getSupportedTypes(context);
    }


    /**
     * @param stream
     * @param recursiveParserWrapperHandler -- handler must implement
     * {@link RecursiveParserWrapperHandler}
     * @param metadata
     * @param context
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     * @throws IllegalStateException if the handler is not a {@link RecursiveParserWrapperHandler}
     */
    @Override
    public void parse(InputStream stream, ContentHandler recursiveParserWrapperHandler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        //this tracks the state of the parent parser, per call to #parse
        ParserState parserState;
        if (recursiveParserWrapperHandler instanceof AbstractRecursiveParserWrapperHandler) {
            parserState = new ParserState(
                    (AbstractRecursiveParserWrapperHandler) recursiveParserWrapperHandler);
        } else {
            throw new IllegalStateException(
                    "ContentHandler must implement RecursiveParserWrapperHandler");
        }
        EmbeddedParserDecorator decorator =
                new EmbeddedParserDecorator(getWrappedParser(), "/", "/", parserState);
        context.set(Parser.class, decorator);
        ContentHandler localHandler =
                parserState.recursiveParserWrapperHandler.getNewContentHandler();
        long started = System.currentTimeMillis();
        parserState.recursiveParserWrapperHandler.startDocument();
        TemporaryResources tmp = new TemporaryResources();
        int writeLimit = -1;
        boolean throwOnWriteLimitReached = true;

        if (recursiveParserWrapperHandler instanceof AbstractRecursiveParserWrapperHandler) {
            ContentHandlerFactory factory =
                    ((AbstractRecursiveParserWrapperHandler)recursiveParserWrapperHandler).getContentHandlerFactory();
            if (factory instanceof WriteLimiter) {
                writeLimit = ((WriteLimiter)factory).getWriteLimit();
                throwOnWriteLimitReached = ((WriteLimiter)factory).isThrowOnWriteLimitReached();
            }
        }
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp, metadata);
            RecursivelySecureContentHandler secureContentHandler =
                    new RecursivelySecureContentHandler(localHandler, tis, writeLimit,
                            throwOnWriteLimitReached, context);
            context.set(RecursivelySecureContentHandler.class, secureContentHandler);
            getWrappedParser().parse(tis, secureContentHandler, metadata, context);
        } catch (Throwable e) {
            if (e instanceof EncryptedDocumentException) {
                metadata.set(TikaCoreProperties.IS_ENCRYPTED, "true");
            }
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                metadata.set(TikaCoreProperties.WRITE_LIMIT_REACHED, "true");
            } else {
                String stackTrace = ExceptionUtils.getFilteredStackTrace(e);
                metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, stackTrace);
                throw e;
            }
        } finally {
            tmp.dispose();
            long elapsedMillis = System.currentTimeMillis() - started;
            metadata.set(TikaCoreProperties.PARSE_TIME_MILLIS, Long.toString(elapsedMillis));
            parserState.recursiveParserWrapperHandler.endDocument(localHandler, metadata);
            parserState.recursiveParserWrapperHandler.endDocument();
        }
    }

    private String getResourceName(Metadata metadata, ParserState state) {
        String objectName = "";
        if (metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY) != null) {
            objectName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        } else if (metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID) != null) {
            objectName = metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
        } else if (metadata.get(TikaCoreProperties.VERSION_NUMBER) != null) {
            objectName = "version-number-" + metadata.get(TikaCoreProperties.VERSION_NUMBER);
        } else {
            objectName = "embedded-" + (++state.unknownCount);
        }
        //make sure that there isn't any path info in the objectName
        //some parsers can return paths, not just file names
        objectName = FilenameUtils.getName(objectName);
        return objectName;
    }


    private class EmbeddedParserDecorator extends StatefulParser {

        private static final long serialVersionUID = 207648200464263337L;
        private final ParserState parserState;
        private String location = null;

        private String embeddedIdPath = null;


        private EmbeddedParserDecorator(Parser parser, String location,
                                        String embeddedIdPath, ParserState parseState) {
            super(parser);
            this.location = location;
            if (!this.location.endsWith("/")) {
                this.location += "/";
            }
            this.embeddedIdPath = embeddedIdPath;
            this.parserState = parseState;
        }

        @Override
        public void parse(InputStream stream, ContentHandler ignore, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {

            //Test to see if we should avoid parsing
            if (parserState.recursiveParserWrapperHandler.hasHitMaximumEmbeddedResources()) {
                return;
            }
            // Work out what this thing is
            String objectName = getResourceName(metadata, parserState);
            String objectLocation = this.location + objectName;

            metadata.add(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, objectLocation);

            String idPath =
                    this.embeddedIdPath.equals("/") ?
                            this.embeddedIdPath + ++parserState.embeddedCount :
                            this.embeddedIdPath + "/" + ++parserState.embeddedCount;
            metadata.add(TikaCoreProperties.EMBEDDED_ID_PATH, idPath);
            metadata.set(TikaCoreProperties.EMBEDDED_ID, parserState.embeddedCount);
            //get a fresh handler
            ContentHandler localHandler =
                    parserState.recursiveParserWrapperHandler.getNewContentHandler();
            parserState.recursiveParserWrapperHandler.startEmbeddedDocument(localHandler, metadata);

            Parser preContextParser = context.get(Parser.class);
            context.set(Parser.class,
                    new EmbeddedParserDecorator(getWrappedParser(), objectLocation,
                            idPath, parserState));
            long started = System.currentTimeMillis();
            RecursivelySecureContentHandler secureContentHandler =
                    context.get(RecursivelySecureContentHandler.class);
            //store the handler that was used before this parse
            //so that you can return it back to its state at the end of this parse
            ContentHandler preContextHandler = secureContentHandler.handler;
            secureContentHandler.updateContentHandler(localHandler);

            try {
                super.parse(stream, secureContentHandler, metadata, context);
            } catch (SAXException e) {
                if (WriteLimitReachedException.isWriteLimitReached(e)) {
                    metadata.add(TikaCoreProperties.WRITE_LIMIT_REACHED, "true");
                    throw e;
                } else {
                    if (catchEmbeddedExceptions) {
                        ParserUtils.recordParserFailure(this, e, metadata);
                    } else {
                        throw e;
                    }
                }
            } catch (CorruptedFileException e) {
                throw e;
            } catch (TikaException e) {
                if (e instanceof EncryptedDocumentException) {
                    metadata.set(TikaCoreProperties.IS_ENCRYPTED, true);
                }
                if (context.get(ZeroByteFileException.IgnoreZeroByteFileException.class) != null &&
                        e instanceof ZeroByteFileException) {
                    //do nothing
                } else if (catchEmbeddedExceptions) {
                    ParserUtils.recordParserFailure(this, e, metadata);
                } else {
                    throw e;
                }
            } finally {
                context.set(Parser.class, preContextParser);
                secureContentHandler.updateContentHandler(preContextHandler);
                long elapsedMillis = System.currentTimeMillis() - started;
                metadata.set(TikaCoreProperties.PARSE_TIME_MILLIS, Long.toString(elapsedMillis));
                parserState.recursiveParserWrapperHandler
                        .endEmbeddedDocument(localHandler, metadata);
            }
        }
    }

    /**
     * This tracks the state of the parse of a single document.
     * In future versions, this will allow the RecursiveParserWrapper to be thread safe.
     */
    private static class ParserState {
        private final AbstractRecursiveParserWrapperHandler recursiveParserWrapperHandler;
        private int unknownCount = 0;
        private int embeddedCount = 0;//this is effectively 1-indexed
        private ParserState(AbstractRecursiveParserWrapperHandler handler) {
            this.recursiveParserWrapperHandler = handler;
        }
    }

    static class RecursivelySecureContentHandler extends SecureContentHandler {
        private ContentHandler handler;

        //total allowable chars across all handlers
        private final int totalWriteLimit;

        private final boolean throwOnWriteLimitReached;

        private final ParseContext parseContext;

        private boolean writeLimitReached = false;

        //total chars written to all handlers
        private int totalChars = 0;
        public RecursivelySecureContentHandler(ContentHandler handler, TikaInputStream stream,
                                               int totalWriteLimit,
                                               boolean throwOnWriteLimitReached, ParseContext parseContext) {
            super(handler, stream);
            this.handler = handler;
            this.totalWriteLimit = totalWriteLimit;
            this.throwOnWriteLimitReached = throwOnWriteLimitReached;
            this.parseContext = parseContext;
        }

        public void updateContentHandler(ContentHandler handler) {
            setContentHandler(handler);
            this.handler = handler;
        }

        /**
         * Bypass the SecureContentHandler...
         * <p>
         * This handler only looks at zip bomb via zip expansion.
         * Users should be protected within entries against nested
         * nested xml entities.  We don't want to carry
         * those stats _across_ entries.
         *
         * @param uri
         * @param localName
         * @param name
         * @param atts
         * @throws SAXException
         */
        @Override
        public void startElement(String uri, String localName, String name, Attributes atts)
                throws SAXException {
            this.handler.startElement(uri, localName, name, atts);
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            this.handler.endElement(uri, localName, name);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (writeLimitReached) {
                return;
            }

            if (totalWriteLimit < 0) {
                super.characters(ch, start, length);
                return;
            }
            int availableLength = Math.min(totalWriteLimit - totalChars, length);
            super.characters(ch, start, availableLength);
            totalChars += availableLength;
            if (availableLength < length) {
                handleWriteLimitReached();
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            if (writeLimitReached) {
                return;
            }

            if (totalWriteLimit < 0) {
                super.ignorableWhitespace(ch, start, length);
                return;
            }
            int availableLength = Math.min(totalWriteLimit - totalChars, length);
            super.ignorableWhitespace(ch, start, availableLength);
            totalChars += availableLength;
            if (availableLength < length) {
                handleWriteLimitReached();
            }
        }

        private void handleWriteLimitReached() throws WriteLimitReachedException {
            writeLimitReached = true;

            if (throwOnWriteLimitReached) {
                throw new WriteLimitReachedException(totalWriteLimit);
            } else {
                ParseRecord parseRecord = parseContext.get(ParseRecord.class);
                if (parseRecord != null) {
                    parseRecord.setWriteLimitReached(true);
                }
            }
        }
    }
}
