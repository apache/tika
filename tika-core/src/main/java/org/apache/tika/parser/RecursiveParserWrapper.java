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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.ContentHandlerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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
public class RecursiveParserWrapper implements Parser {
    
    /**
     * Generated serial version
     */
    private static final long serialVersionUID = 9086536568120690938L;

    //move this to TikaCoreProperties?
    public final static Property TIKA_CONTENT = Property.internalText(TikaCoreProperties.TIKA_META_PREFIX+"content");
    public final static Property PARSE_TIME_MILLIS = Property.internalText(TikaCoreProperties.TIKA_META_PREFIX+"parse_time_millis");
    public final static Property WRITE_LIMIT_REACHED =
                Property.internalBoolean(TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX+"write_limit_reached");
    public final static Property EMBEDDED_RESOURCE_LIMIT_REACHED = 
                Property.internalBoolean(TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX+"embedded_resource_limit_reached");

    //move this to TikaCoreProperties?
    public final static Property EMBEDDED_RESOURCE_PATH = 
                Property.internalText(TikaCoreProperties.TIKA_META_PREFIX+"embedded_resource_path");
 
    private final Parser wrappedParser;
    private final ContentHandlerFactory contentHandlerFactory;
    private final List<Metadata> metadatas = new LinkedList<Metadata>();

    //used in naming embedded resources that don't have a name.
    private int unknownCount = 0;   
    private int maxEmbeddedResources = -1;
    private boolean hitMaxEmbeddedResources = false;
    
    public RecursiveParserWrapper(Parser wrappedParser, ContentHandlerFactory contentHandlerFactory) {
        this.wrappedParser = wrappedParser;
        this.contentHandlerFactory = contentHandlerFactory;
    }
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return wrappedParser.getSupportedTypes(context);
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
    public void parse(InputStream stream, ContentHandler ignore,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

        String name = getResourceName(metadata);
        EmbeddedParserDecorator decorator = new EmbeddedParserDecorator(name);
        context.set(Parser.class, decorator);
        ContentHandler localHandler = contentHandlerFactory.getNewContentHandler();
        long started = new Date().getTime();
        try {
            wrappedParser.parse(stream, localHandler, metadata, context);
        } catch (SAXException e) {
            boolean wlr = isWriteLimitReached(e);
            if (wlr == false) {
                throw e;
            }
            metadata.set(WRITE_LIMIT_REACHED, "true");
        }
        long elapsedMillis = new Date().getTime()-started;
        metadata.set(PARSE_TIME_MILLIS, Long.toString(elapsedMillis));
        addContent(localHandler, metadata);
        
        if (hitMaxEmbeddedResources) {
            metadata.set(EMBEDDED_RESOURCE_LIMIT_REACHED, "true");
        }
        metadatas.add(0, deepCopy(metadata));
    }

    /**
     * 
     * The first element in the returned list represents the 
     * data from the outer container file.  There is no guarantee
     * about the ordering of the list after that.
     * 
     * @return list of Metadata objects that were gathered during the parse
     */
    public List<Metadata> getMetadata() {
        return metadatas;
    }
    
    /**
     * Set the maximum number of embedded resources to store.
     * If the max is hit during parsing, the {@link #EMBEDDED_RESOURCE_LIMIT_REACHED}
     * property will be added to the container document's Metadata.
     * 
     * <p>
     * If this value is < 0 (the default), the wrapper will store all Metadata.
     * 
     * @param max maximum number of embedded resources to store
     */
    public void setMaxEmbeddedResources(int max) {
        maxEmbeddedResources = max;
    }
    

    /**
     * This clears the metadata list and resets {@link #unknownCount} and
     * {@link #hitMaxEmbeddedResources}
     */
    public void reset() {
        metadatas.clear();
        unknownCount = 0;
        hitMaxEmbeddedResources = false;
    }
    
    /**
     * Copied/modified from WriteOutContentHandler.  Couldn't make that 
     * static, and we need to have something that will work 
     * with exceptions thrown from both BodyContentHandler and WriteOutContentHandler
     * @param t
     * @return
     */
    private boolean isWriteLimitReached(Throwable t) {
        if (t.getMessage().indexOf("Your document contained more than") == 0) {
            return true;
        } else {
            return t.getCause() != null && isWriteLimitReached(t.getCause());
        }
    }
    
    //defensive copy
    private Metadata deepCopy(Metadata m) {
        Metadata clone = new Metadata();
        
        for (String n : m.names()){
            if (! m.isMultiValued(n)) {
                clone.set(n, m.get(n));
            } else {
                String[] vals = m.getValues(n);
                for (int i = 0; i < vals.length; i++) {
                    clone.add(n, vals[i]);
                }
            }
        }
        return clone;
    }
    
    private String getResourceName(Metadata metadata) {
        String objectName = "";
        if (metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY) != null) {
            objectName = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
         } else if (metadata.get(TikaMetadataKeys.EMBEDDED_RELATIONSHIP_ID) != null) {
            objectName = metadata.get(TikaMetadataKeys.EMBEDDED_RELATIONSHIP_ID);
         } else {
            objectName = "embedded-" + (++unknownCount);
         }
         //make sure that there isn't any path info in the objectName
         //some parsers can return paths, not just file names
         objectName = FilenameUtils.getName(objectName);
         return objectName;
    }
    
    private void addContent(ContentHandler handler, Metadata metadata) {
        
        if (handler.getClass().equals(DefaultHandler.class)){
            //no-op: we can't rely on just testing for 
            //empty content because DefaultHandler's toString()
            //returns e.g. "org.xml.sax.helpers.DefaultHandler@6c8b1edd"
        } else {
            String content = handler.toString();
            if (content != null && content.trim().length() > 0 ) {
                metadata.add(TIKA_CONTENT, content);
            }
        }

    }
    
    /**
     * Override for different behavior.
     * 
     * @return handler to be used for each document
     */

    
    private class EmbeddedParserDecorator extends ParserDecorator {
        
        private static final long serialVersionUID = 207648200464263337L;
        
        private String location = null;

        
        private EmbeddedParserDecorator(String location) {
            super(wrappedParser);
            this.location = location;
            if (! this.location.endsWith("/")) {
               this.location += "/";
            }
        }

        @Override
        public void parse(InputStream stream, ContentHandler ignore,
                Metadata metadata, ParseContext context) throws IOException,
                SAXException, TikaException {
            //Test to see if we should avoid parsing
            if (maxEmbeddedResources > -1 && 
                    metadatas.size() >= maxEmbeddedResources) {
                hitMaxEmbeddedResources = true;
                return;
            }
            // Work out what this thing is
            String objectName = getResourceName(metadata);
            String objectLocation = this.location + objectName;
      
            metadata.add(EMBEDDED_RESOURCE_PATH, objectLocation);
            
            //ignore the content handler that is passed in
            //and get a fresh handler
            ContentHandler localHandler = contentHandlerFactory.getNewContentHandler();
            
            Parser preContextParser = context.get(Parser.class);
            context.set(Parser.class, new EmbeddedParserDecorator(objectLocation));

            try {
                super.parse(stream, localHandler, metadata, context);
            } catch (SAXException e) {
                boolean wlr = isWriteLimitReached(e);
                if (wlr == true) {
                    metadata.add(WRITE_LIMIT_REACHED, "true");
                } else {
                    throw e;
                }
            } finally {
                context.set(Parser.class, preContextParser);
            }
            
            //Because of recursion, we need
            //to re-test to make sure that we limit the 
            //number of stored resources
            if (maxEmbeddedResources > -1 && 
                    metadatas.size() >= maxEmbeddedResources) {
                hitMaxEmbeddedResources = true;
                return;
            }
            addContent(localHandler, metadata);
            metadatas.add(deepCopy(metadata));
        }        
    }


}
