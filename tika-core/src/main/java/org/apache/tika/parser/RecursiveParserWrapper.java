package org.apache.tika.parser;

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

import org.apache.tika.exception.CorruptedFileException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.SecureContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.ParserUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * This is a helper class that wraps a parser in a recursive handler.
 * It takes care of setting the embedded parser in the ParseContext 
 * and handling the embedded path calculations.
 * <p>
 * After parsing a document, call getMetadata() to retrieve a list of 
 * Metadata objects, one for each embedded resource.  The first item
 * in the list will contain the Metadata for the outer container file.
 * <p>
 * Content can also be extracted and stored in the {@link #TIKA_CONTENT} field
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
 * Note, too, that this wrapper is not thread safe because it stores state.  
 * The client must initialize a new wrapper for each thread, and the client
 * is responsible for calling {@link #reset()} after each parse.
 * <p>
 * The unit tests for this class are in the tika-parsers module.
 * </p>
 */
public class RecursiveParserWrapper extends ParserDecorator {
    
    /**
     * Generated serial version
     */
    private static final long serialVersionUID = 9086536568120690938L;

    /**
     * @deprecated use {@link org.apache.tika.sax.RecursiveParserWrapperHandler#TIKA_CONTENT}
     */
    @Deprecated
    public final static Property TIKA_CONTENT = AbstractRecursiveParserWrapperHandler.TIKA_CONTENT;
    /**
     * @deprecated use {@link org.apache.tika.sax.RecursiveParserWrapperHandler#PARSE_TIME_MILLIS}
     */
    @Deprecated
    public final static Property PARSE_TIME_MILLIS = AbstractRecursiveParserWrapperHandler.PARSE_TIME_MILLIS;

    /**
     * @deprecated use {@link org.apache.tika.sax.RecursiveParserWrapperHandler#EMBEDDED_EXCEPTION}
     */
    @Deprecated
    public final static Property WRITE_LIMIT_REACHED =
            AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED;
    /**
     * @deprecated use {@link org.apache.tika.sax.RecursiveParserWrapperHandler#EMBEDDED_RESOURCE_LIMIT_REACHED}
     */
    @Deprecated
    public final static Property EMBEDDED_RESOURCE_LIMIT_REACHED =
            AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED;

    /**
     * @deprecated use {@link org.apache.tika.sax.RecursiveParserWrapperHandler#EMBEDDED_EXCEPTION}
     */
    @Deprecated
    public final static Property EMBEDDED_EXCEPTION = AbstractRecursiveParserWrapperHandler.EMBEDDED_EXCEPTION;

    /**
     * @deprecated use {@link org.apache.tika.sax.RecursiveParserWrapperHandler#EMBEDDED_RESOURCE_PATH}
     */
    @Deprecated
    public final static Property EMBEDDED_RESOURCE_PATH = AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_PATH;

    /**
     * @deprecated this should be passed in via the {@link RecursiveParserWrapperHandler}
     */
    @Deprecated
    private ContentHandlerFactory contentHandlerFactory = null;

    private final boolean catchEmbeddedExceptions;

    /**
     * set this on the RecursiveParserWrapperHandler instead
     * @deprecated this is here only for legacy behavior; it will be removed in 2.0 and/or 1.20
     */
    @Deprecated
    private int maxEmbeddedResources = -1;
    /**
     * @deprecated this is here only for legacy behavior; it will be removed in 2.0 and/or 1.20
     */
    @Deprecated
    private ParserState lastParseState = null;

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
     *
     * @param wrappedParser parser to wrap
     * @param catchEmbeddedExceptions whether or not to catch+record embedded exceptions.
     *                                If set to <code>false</code>, embedded exceptions will be thrown and
     *                                the rest of the file will not be parsed. The following will not be ignored:
     *                                  {@link CorruptedFileException}, {@link RuntimeException}
     */
    public RecursiveParserWrapper(Parser wrappedParser, boolean catchEmbeddedExceptions) {
        super(wrappedParser);
        this.catchEmbeddedExceptions = catchEmbeddedExceptions;
    }
    /**
     * Initialize the wrapper with {@link #catchEmbeddedExceptions} set
     * to <code>true</code> as default.
     *
     * @param wrappedParser parser to use for the container documents and the embedded documents
     * @param contentHandlerFactory factory to use to generate a new content handler for
     *                              the container document and each embedded document
     * @deprecated use {@link RecursiveParserWrapper#RecursiveParserWrapper(Parser)}
     */
    @Deprecated
    public RecursiveParserWrapper(Parser wrappedParser, ContentHandlerFactory contentHandlerFactory) {
        this(wrappedParser, contentHandlerFactory, true);
    }

    /**
     * Initialize the wrapper.
     *
     * @param wrappedParser parser to use for the container documents and the embedded documents
     * @param contentHandlerFactory factory to use to generate a new content handler for
     *                              the container document and each embedded document
     * @param catchEmbeddedExceptions whether or not to catch the embedded exceptions.
     *                                If set to <code>true</code>, the stack traces will be stored in
     *                                the metadata object with key: {@link #EMBEDDED_EXCEPTION}.
     * @deprecated use {@link RecursiveParserWrapper#RecursiveParserWrapper(Parser, boolean)}
     */
    @Deprecated
    public RecursiveParserWrapper(Parser wrappedParser,
                                  ContentHandlerFactory contentHandlerFactory, boolean catchEmbeddedExceptions) {
        super(wrappedParser);
        this.contentHandlerFactory = contentHandlerFactory;
        this.catchEmbeddedExceptions = catchEmbeddedExceptions;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return getWrappedParser().getSupportedTypes(context);
    }

    /**
     * Acts like a regular parser except it ignores the ContentHandler
     * and it automatically sets/overwrites the embedded Parser in the 
     * ParseContext object.
     * <p>
     * To retrieve the results of the parse, use {@link #getMetadata()}.
     * <p>
     * Make sure to call {@link #reset()} after each parse.
     */
    @Override
    public void parse(InputStream stream, ContentHandler recursiveParserWrapperHandler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        //this tracks the state of the parent parser, per call to #parse
        //in future versions, we can remove lastParseState, and this will be thread-safe
        ParserState parserState;
        if (recursiveParserWrapperHandler instanceof AbstractRecursiveParserWrapperHandler) {
            parserState = new ParserState((AbstractRecursiveParserWrapperHandler)recursiveParserWrapperHandler);
        } else {
            parserState = new ParserState(new RecursiveParserWrapperHandler(contentHandlerFactory, maxEmbeddedResources));
            lastParseState = parserState;
        }
        EmbeddedParserDecorator decorator = new EmbeddedParserDecorator(getWrappedParser(), "/", parserState);
        context.set(Parser.class, decorator);
        ContentHandler localHandler = parserState.recursiveParserWrapperHandler.getNewContentHandler();
        long started = System.currentTimeMillis();
        parserState.recursiveParserWrapperHandler.startDocument();
        try {
            try (TikaInputStream tis = TikaInputStream.get(stream)) {
                RecursivelySecureContentHandler secureContentHandler =
                        new RecursivelySecureContentHandler(localHandler, tis);
                context.set(RecursivelySecureContentHandler.class, secureContentHandler);
                getWrappedParser().parse(tis, secureContentHandler, metadata, context);
            }
        } catch (SAXException e) {
            boolean wlr = isWriteLimitReached(e);
            if (wlr == false) {
                throw e;
            }
            metadata.set(RecursiveParserWrapperHandler.WRITE_LIMIT_REACHED, "true");
        } catch (Throwable e) {
            //try our best to record the problem in the metadata object
            //then rethrow
            String stackTrace = ExceptionUtils.getFilteredStackTrace(e);
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX+"runtime", stackTrace);
            throw e;
        } finally {
            long elapsedMillis = System.currentTimeMillis() - started;
            metadata.set(RecursiveParserWrapperHandler.PARSE_TIME_MILLIS, Long.toString(elapsedMillis));
            parserState.recursiveParserWrapperHandler.endDocument(localHandler, metadata);
            parserState.recursiveParserWrapperHandler.endDocument();
        }
    }

    /**
     * 
     * The first element in the returned list represents the 
     * data from the outer container file.  There is no guarantee
     * about the ordering of the list after that.
     *
     * @deprecated use a {@link RecursiveParserWrapperHandler} instead
     *
     * @return list of Metadata objects that were gathered during the parse
     * @throws IllegalStateException if you've used a {@link RecursiveParserWrapperHandler} in your last
     * call to {@link #parse(InputStream, ContentHandler, Metadata, ParseContext)}
     */
    @Deprecated
    public List<Metadata> getMetadata() {
        if (lastParseState != null) {
            return ((RecursiveParserWrapperHandler) lastParseState.recursiveParserWrapperHandler).getMetadataList();
        } else {
            throw new IllegalStateException("This is deprecated; please use a RecursiveParserWrapperHandler instead");
        }
    }
    
    /**
     * Set the maximum number of embedded resources to store.
     * If the max is hit during parsing, the {@link #EMBEDDED_RESOURCE_LIMIT_REACHED}
     * property will be added to the container document's Metadata.
     * 
     * <p>
     * If this value is < 0 (the default), the wrapper will store all Metadata.
     * @deprecated set this on a {@link RecursiveParserWrapperHandler}
     * @param max maximum number of embedded resources to store
     */
    @Deprecated
    public void setMaxEmbeddedResources(int max) {
        maxEmbeddedResources = max;
    }
    

    /**
     * This clears the last parser state (metadata list, unknown count, hit embeddedresource count)
     *
     * @deprecated use a {@link org.apache.tika.sax.RecursiveParserWrapperHandler} instead
     * @throws IllegalStateException if you used a {@link RecursiveParserWrapper} in your call
     * to {@link #parse(InputStream, ContentHandler, Metadata, ParseContext)}
     */
    @Deprecated
    public void reset() {
        if (lastParseState != null) {
            lastParseState = new ParserState(new RecursiveParserWrapperHandler(contentHandlerFactory, maxEmbeddedResources));
        } else {
            throw new IllegalStateException("This is deprecated; please use a RecursiveParserWrapperHandler instead");
        }
    }
    
    /**
     * Copied/modified from WriteOutContentHandler.  Couldn't make that 
     * static, and we need to have something that will work 
     * with exceptions thrown from both BodyContentHandler and WriteOutContentHandler
     * @param t
     * @return
     */
    private boolean isWriteLimitReached(Throwable t) {
        if (t.getMessage() != null && 
                t.getMessage().indexOf("Your document contained more than") == 0) {
            return true;
        } else {
            return t.getCause() != null && isWriteLimitReached(t.getCause());
        }
    }

    private String getResourceName(Metadata metadata, ParserState state) {
        String objectName = "";
        if (metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY) != null) {
            objectName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        } else if (metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID) != null) {
            objectName = metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
        } else {
            objectName = "embedded-" + (++state.unknownCount);
        }
        //make sure that there isn't any path info in the objectName
        //some parsers can return paths, not just file names
        objectName = FilenameUtils.getName(objectName);
        return objectName;
    }

    
    private class EmbeddedParserDecorator extends ParserDecorator {
        
        private static final long serialVersionUID = 207648200464263337L;
        
        private String location = null;
        private final ParserState parserState;

        
        private EmbeddedParserDecorator(Parser parser, String location, ParserState parseState) {
            super(parser);
            this.location = location;
            if (! this.location.endsWith("/")) {
               this.location += "/";
            }
            this.parserState = parseState;
        }

        @Override
        public void parse(InputStream stream, ContentHandler ignore,
                Metadata metadata, ParseContext context) throws IOException,
                SAXException, TikaException {
            //Test to see if we should avoid parsing
            if (parserState.recursiveParserWrapperHandler.hasHitMaximumEmbeddedResources()) {
                return;
            }
            // Work out what this thing is
            String objectName = getResourceName(metadata, parserState);
            String objectLocation = this.location + objectName;
      
            metadata.add(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_PATH, objectLocation);


            //get a fresh handler
            ContentHandler localHandler = parserState.recursiveParserWrapperHandler.getNewContentHandler();
            parserState.recursiveParserWrapperHandler.startEmbeddedDocument(localHandler, metadata);

            Parser preContextParser = context.get(Parser.class);
            context.set(Parser.class, new EmbeddedParserDecorator(getWrappedParser(), objectLocation, parserState));
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
                boolean wlr = isWriteLimitReached(e);
                if (wlr == true) {
                    metadata.add(WRITE_LIMIT_REACHED, "true");
                } else {
                    if (catchEmbeddedExceptions) {
                        ParserUtils.recordParserFailure(this, e, metadata);
                    } else {
                        throw e;
                    }
                }
            } catch(CorruptedFileException e) {
                throw e;
            } catch (TikaException e) {
                if (catchEmbeddedExceptions) {
                    ParserUtils.recordParserFailure(this, e, metadata);
                } else {
                    throw e;
                }
            } finally {
                context.set(Parser.class, preContextParser);
                secureContentHandler.updateContentHandler(preContextHandler);
                long elapsedMillis = System.currentTimeMillis() - started;
                metadata.set(RecursiveParserWrapperHandler.PARSE_TIME_MILLIS, Long.toString(elapsedMillis));
                parserState.recursiveParserWrapperHandler.endEmbeddedDocument(localHandler, metadata);
            }
        }
    }

    /**
     * This tracks the state of the parse of a single document.
     * In future versions, this will allow the RecursiveParserWrapper to be thread safe.
     */
    private class ParserState {
        private int unknownCount = 0;
        private final AbstractRecursiveParserWrapperHandler recursiveParserWrapperHandler;
        private ParserState(AbstractRecursiveParserWrapperHandler handler) {
            this.recursiveParserWrapperHandler = handler;
        }
    }

    private class RecursivelySecureContentHandler
            extends SecureContentHandler {
        private ContentHandler handler;
        public RecursivelySecureContentHandler(ContentHandler handler, TikaInputStream stream) {
            super(handler, stream);
            this.handler = handler;
        }

        public void updateContentHandler(ContentHandler handler) {
            setContentHandler(handler);
            this.handler = handler;
        }

        /**
         *  Bypass the SecureContentHandler...
         *
         *  This handler only looks at zip bomb via zip expansion.
         *  Users should be protected within entries against nested
         *  nested xml entities.  We don't want to carry
         *  those stats _across_ entries.
         *
         * @param uri
         * @param localName
         * @param name
         * @param atts
         * @throws SAXException
         */
        @Override
        public void startElement(
                String uri, String localName, String name, Attributes atts)
                throws SAXException {
            this.handler.startElement(uri, localName, name, atts);
        }

        @Override
        public void endElement(
                String uri, String localName, String name) throws SAXException {
            this.handler.endElement(uri, localName, name);
        }
    }
}
