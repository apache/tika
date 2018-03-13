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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract base class for parser wrappers which may / will
 *  process a given stream multiple times, merging the results
 *  of the various parsers used.
 * End users should normally use {@link FallbackParser} or
 *  {@link SupplementingParser} along with a Strategy.
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
    
    // TODO Figure out some sort of Content Policy and how
    //  it might possibly work
    // TODO Is an overridden method that takes a 
    //  ContentHandlerFactory the best way?

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
    private List<Parser> parsers;
    
    /**
     * Computed list of Mime Types to offer, which is all
     *  those in common between the parsers.
     * For explicit mimetypes only, use a {@link ParserDecorator}
     */
    private Set<MediaType> offeredTypes;
    
    // TODO Tika Config XML Support for these parsers and their
    //  metadata policies + parsers + mimetypes
    // See https://wiki.apache.org/tika/CompositeParserDiscussion
    
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

    
    public AbstractMultipleParser(MediaTypeRegistry registry, MetadataPolicy policy,
                                  Parser... parsers) {
        this(registry, policy, Arrays.asList(parsers));
    }
    public AbstractMultipleParser(MediaTypeRegistry registry, MetadataPolicy policy,
                                  List<Parser> parsers) {
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
    
    /**
     * Used to notify implementations that a Parser has Finished
     *  or Failed, and to allow them to decide to continue or 
     *  abort further parsing
     */
    protected abstract boolean parserCompleted(
            Parser parser, Metadata metadata, 
            ContentHandler handler, Exception exception);
    
    /**
     * Processes the given Stream through one or more parsers, 
     *  resetting things between parsers as requested by policy.
     * The actual processing is delegated to one or more {@link Parser}s
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata originalMetadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // Track the metadata between parsers, so we can apply our policy
        Metadata lastMetadata = cloneMetadata(originalMetadata);
        Metadata metadata = lastMetadata;
        
        // Start tracking resources, so we can clean up when done
        TemporaryResources tmp = new TemporaryResources();
        try {
            // Force the stream to be a Tika one
            // Force the stream to be file-backed, so we can re-read safely
            //  later if required for parser 2+
            // TODO Should we use RereadableInputStream instead?
            TikaInputStream taggedStream = TikaInputStream.get(stream, tmp);
            taggedStream.getPath();
            
            // TODO Somehow shield/wrap the Handler, so that we can
            //  avoid failures if multiple parsers want to do content
            // TODO Solve the multiple-content problem!
            // TODO Provide a way to supply a ContentHandlerFactory?

            for (Parser p : parsers) {
                // Indicate we may need to re-read the stream later
                // TODO Support an InputStreamFactory as an alternative to
                //  Files, see TIKA-2585
                taggedStream.mark(-1);
                
                // Record that we used this parser
                recordParserDetails(p, originalMetadata);

                // Prepare an near-empty Metadata, will merge after
                metadata = cloneMetadata(originalMetadata);
                
                // Process if possible
                Exception failure = null;
                try {
                    p.parse(taggedStream, handler, metadata, context);
                } catch (Exception e) {
                    recordParserFailure(p, e, metadata);
                    failure = e;
                }
                
                // Notify the implementation how it went
                boolean tryNext = parserCompleted(p, metadata, handler, failure);
                
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
                taggedStream.reset();
            }
        } finally {
            tmp.dispose();
        }
        
        // Finally, copy the latest metadata back onto their supplied object
        for (String n : metadata.names()) {
            originalMetadata.set(n, metadata.get(n));
        }
    }
    
    // TODO Provide a method that takes an InputStreamSource as well,
    //  and a ContentHandlerFactory. Will need wrappers to convert standard
    
    protected static Metadata mergeMetadata(Metadata newMetadata, Metadata lastMetadata, MetadataPolicy policy) {
        if (policy == MetadataPolicy.DISCARD_ALL) {
            return newMetadata;
        }
        
        for (String n : lastMetadata.names()) {
            if (newMetadata.get(n) == null) {
                newMetadata.set(n, lastMetadata.get(n));
            } else {
                switch (policy) {
                case FIRST_WINS:
                    // Use the earlier value 
                    newMetadata.set(n, lastMetadata.get(n));
                    continue;
                case LAST_WINS:
                    // Most recent (last) parser has already won
                    continue;
                case KEEP_ALL:
                    // TODO Find unique values to add
                    // TODO Implement
                    continue;
                }
            }
        }
        return newMetadata;
    }
}
