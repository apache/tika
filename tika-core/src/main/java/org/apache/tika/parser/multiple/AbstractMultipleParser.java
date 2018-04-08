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
package org.apache.tika.parser.multiple;

import static org.apache.tika.utils.ParserUtils.cloneMetadata;
import static org.apache.tika.utils.ParserUtils.recordParserDetails;
import static org.apache.tika.utils.ParserUtils.recordParserFailure;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.utils.ParserUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract base class for parser wrappers which may / will
 *  process a given stream multiple times, merging the results
 *  of the various parsers used.
 * End users should normally use {@link FallbackParser} or
 *  {@link SupplementingParser} along with a Strategy.
 * Note that unless you give a {@link ContentHandlerFactory},
 *  you'll get content from every parser tried mushed together!
 *
 * @since Apache Tika 1.18
 */
public abstract class AbstractMultipleParser extends AbstractParser {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 5383668090329836559L;

    /**
     * The various strategies for handling metadata emitted by
     *  multiple parsers.
     * Note that not all will be supported by all subclasses.
     */
    public enum MetadataPolicy {
        /**
         * Before moving onto another parser, throw away
         *  all previously seen metadata
         */
        DISCARD_ALL,
        /**
         * The first parser to output a given key wins,
         *  merge in non-clashing other keys
         */
        FIRST_WINS,
        /**
         * The last parser to output a given key wins,
         *  overriding previous parser values for a
         *  clashing key.
         */
        LAST_WINS,
        /**
         * Where multiple parsers output a given key,
         *  store all their different (unique) values
         */
        KEEP_ALL
    };
    protected static final String METADATA_POLICY_CONFIG_KEY = "metadataPolicy";
    
    /**
     * Media type registry.
     */
    private MediaTypeRegistry registry;
    
    /**
     * How we should handle metadata clashes
     */
    private MetadataPolicy policy;
    
    /**
     * List of the multiple parsers to try.
     */
    private Collection<? extends Parser> parsers;
    
    /**
     * Computed list of Mime Types to offer, which is all
     *  those in common between the parsers.
     * For explicit mimetypes only, use a {@link ParserDecorator}
     */
    private Set<MediaType> offeredTypes;
    
    /**
     * Returns the media type registry used to infer type relationships.
     *
     * @return media type registry
     */
    public MediaTypeRegistry getMediaTypeRegistry() {
        return registry;
    }

    /**
     * Sets the media type registry used to infer type relationships.
     *
     * @param registry media type registry
     */
    public void setMediaTypeRegistry(MediaTypeRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("rawtypes")
    protected static MetadataPolicy getMetadataPolicy(Map<String, Param> params) {
        if (params.containsKey(METADATA_POLICY_CONFIG_KEY)) {
            return (MetadataPolicy)params.get(METADATA_POLICY_CONFIG_KEY).getValue();
        }
        throw new IllegalArgumentException("Required parameter '"+METADATA_POLICY_CONFIG_KEY+"' not supplied");
    }
    @SuppressWarnings("rawtypes")
    public AbstractMultipleParser(MediaTypeRegistry registry, 
                                  Collection<? extends Parser> parsers,
                                  Map<String, Param> params) {
        this(registry, getMetadataPolicy(params), parsers);
    }
    public AbstractMultipleParser(MediaTypeRegistry registry, MetadataPolicy policy,
                                  Parser... parsers) {
        this(registry, policy, Arrays.asList(parsers));
    }
    public AbstractMultipleParser(MediaTypeRegistry registry, MetadataPolicy policy,
                                  Collection<? extends Parser> parsers) {
        this.policy = policy;
        this.parsers = parsers;
        this.registry = registry;
        
        // TODO Only offer those in common to several/all parser
        // TODO Some sort of specialisation / subtype support
        this.offeredTypes = new HashSet<>();
        for (Parser parser : parsers) {
            offeredTypes.addAll(
                    parser.getSupportedTypes(new ParseContext())
            );
        }
    }
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return offeredTypes;
    }
    
    public MetadataPolicy getMetadataPolicy() {
        return policy;
    }
    public List<Parser> getAllParsers() {
        return Collections.unmodifiableList(new ArrayList<>(parsers));
    }
    
    /**
     * Used to allow implementations to prepare or change things
     *  before parsing occurs
     */
    protected void parserPrepare(Parser parser, Metadata metadata,
                                 ParseContext context) {}

    /**
     * Used to notify implementations that a Parser has Finished
     *  or Failed, and to allow them to decide to continue or 
     *  abort further parsing
     */
    protected abstract boolean parserCompleted(
            Parser parser, Metadata metadata, ContentHandler handler, 
            ParseContext context, Exception exception);
    
    /**
     * Processes the given Stream through one or more parsers, 
     *  resetting things between parsers as requested by policy.
     * The actual processing is delegated to one or more {@link Parser}s.
     * 
     * Note that you'll get text from every parser this way, to have 
     *  control of which content is from which parser you need to
     *  call the method with a {@link ContentHandlerFactory} instead. 
     */
    @Override
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, null, metadata, context);
    }
    /**
     * Processes the given Stream through one or more parsers, 
     *  resetting things between parsers as requested by policy.
     * The actual processing is delegated to one or more {@link Parser}s.
     * You will get one ContentHandler fetched for each Parser used.
     * TODO Do we need to return all the ContentHandler instances we created?
     * @deprecated The {@link ContentHandlerFactory} override is still experimental 
     *  and the method signature is subject to change before Tika 2.0
     */
    public void parse(
            InputStream stream, ContentHandlerFactory handlers,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        parse(stream, null, handlers, metadata, context);
    }
    private void parse(InputStream stream, 
            ContentHandler handler, ContentHandlerFactory handlerFactory,
            Metadata originalMetadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // Track the metadata between parsers, so we can apply our policy
        Metadata lastMetadata = cloneMetadata(originalMetadata);
        Metadata metadata = lastMetadata;
        
        // Start tracking resources, so we can clean up when done
        TemporaryResources tmp = new TemporaryResources();
        try {
            // Ensure we'll be able to re-read safely, buffering to disk if so,
            //  to permit Parsers 2+ to be able to read the same data
            InputStream taggedStream = ParserUtils.ensureStreamReReadable(stream, tmp);
            
            for (Parser p : parsers) {
                // Get a new handler for this parser, if we can
                // If not, the user will get text from every parser
                //  mushed together onto the one solitary handler...
                if (handlerFactory != null) {
                    handler = handlerFactory.getNewContentHandler();
                }
                
                // Record that we used this parser
                recordParserDetails(p, originalMetadata);

                // Prepare an near-empty Metadata, will merge after
                metadata = cloneMetadata(originalMetadata);
                
                // Notify the implementation of what we're about to do
                parserPrepare(p, metadata, context);

                // Process if possible
                Exception failure = null;
                try {
                    p.parse(taggedStream, handler, metadata, context);
                } catch (Exception e) {
                    // Record the failure such that it can't get lost / overwritten
                    recordParserFailure(p, e, originalMetadata);
                    recordParserFailure(p, e, metadata);
                    failure = e;
                }
                
                // Notify the implementation how it went
                boolean tryNext = parserCompleted(p, metadata, handler, context, failure);
                
                // Handle metadata merging / clashes
                metadata = mergeMetadata(metadata, lastMetadata, policy);
                
                // Abort if requested, with the exception if there was one
                if (!tryNext) {
                   if (failure != null) {
                       if (failure instanceof IOException) throw (IOException)failure;
                       if (failure instanceof SAXException) throw (SAXException)failure;
                       if (failure instanceof TikaException) throw (TikaException)failure;
                       throw new TikaException("Unexpected RuntimeException from " + p, failure);
                   }
                   // Abort processing, don't try any more parsers
                   break;
                }
                
                // Prepare for the next parser, if present
                lastMetadata = cloneMetadata(metadata);
                taggedStream = ParserUtils.streamResetForReRead(taggedStream, tmp);
            }
        } finally {
            tmp.dispose();
        }
        
        // Finally, copy the latest metadata back onto their supplied object
        for (String n : metadata.names()) {
            originalMetadata.remove(n);
            for (String val : metadata.getValues(n)) {
                originalMetadata.add(n, val);
            }
        }
    }
    
    protected static Metadata mergeMetadata(Metadata newMetadata, Metadata lastMetadata, MetadataPolicy policy) {
        if (policy == MetadataPolicy.DISCARD_ALL) {
            return newMetadata;
        }
        
        for (String n : lastMetadata.names()) {
            // If this is one of the metadata keys we're setting ourselves
            //  for tracking/errors, then always keep the latest one!
            if (n.equals(ParserUtils.X_PARSED_BY)) continue;
            if (n.equals(ParserUtils.EMBEDDED_PARSER.getName())) continue;
            if (n.equals(ParserUtils.EMBEDDED_EXCEPTION.getName())) continue;
            
            // Merge as per policy 
            String[] newVals = newMetadata.getValues(n);
            String[] oldVals = lastMetadata.getValues(n);
            if (newVals == null || newVals.length == 0) {
                // Metadata only in previous run, keep old values
                for (String val : oldVals) {
                    newMetadata.add(n, val);
                }
            } else if (Arrays.deepEquals(oldVals, newVals)) {
                // Metadata is the same, nothing to do
                continue;
            } else {
                switch (policy) {
                case FIRST_WINS:
                    // Use the earlier value(s) in place of this/these one/s
                    newMetadata.remove(n);
                    for (String val : oldVals) {
                        newMetadata.add(n, val);
                    }
                    continue;
                case LAST_WINS:
                    // Most recent (last) parser has already won
                    continue;
                case KEEP_ALL:
                    // Start with old list, then add any new unique values
                    List<String> vals = new ArrayList<>(Arrays.asList(oldVals));
                    newMetadata.remove(n);
                    for (String oldVal : oldVals) {
                        newMetadata.add(n, oldVal);
                    }
                    for (String newVal : newVals) {
                        if (! vals.contains(newVal)) {
                            newMetadata.add(n, newVal);
                            vals.add(newVal);
                        }
                    }
                    
                    continue;
                }
            }
        }
        return newMetadata;
    }
}
