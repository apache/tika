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
package org.apache.tika.sax;

import java.util.LinkedList;
import java.util.List;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.utils.ParserUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This is the default implementation of {@link AbstractRecursiveParserWrapperHandler}. See its
 * documentation for more details.
 *
 * <p>This caches the a metadata object for each embedded file and for the container file. It places
 * the extracted content in the metadata object, with this key: {@link
 * TikaCoreProperties#TIKA_CONTENT} If memory is a concern, subclass
 * AbstractRecursiveParserWrapperHandler to handle each embedded document.
 *
 * <p><b>NOTE: This handler must only be used with the {@link
 * org.apache.tika.parser.RecursiveParserWrapper}</b>
 */
public class RecursiveParserWrapperHandler extends AbstractRecursiveParserWrapperHandler {

    protected final List<Metadata> metadataList = new LinkedList<>();
    private final MetadataFilter metadataFilter;

    /** Create a handler with no limit on the number of embedded resources */
    public RecursiveParserWrapperHandler(ContentHandlerFactory contentHandlerFactory) {
        this(contentHandlerFactory, -1, NoOpFilter.NOOP_FILTER);
    }

    /**
     * Create a handler that limits the number of embedded resources that will be parsed
     *
     * @param maxEmbeddedResources number of embedded resources that will be parsed
     */
    public RecursiveParserWrapperHandler(
            ContentHandlerFactory contentHandlerFactory, int maxEmbeddedResources) {
        this(contentHandlerFactory, maxEmbeddedResources, NoOpFilter.NOOP_FILTER);
    }

    public RecursiveParserWrapperHandler(
            ContentHandlerFactory contentHandlerFactory,
            int maxEmbeddedResources,
            MetadataFilter metadataFilter) {
        super(contentHandlerFactory, maxEmbeddedResources);
        this.metadataFilter = metadataFilter;
    }

    /**
     * This is called before parsing an embedded document
     *
     * @param contentHandler - local content handler to use on the embedded document
     * @param metadata metadata to use for the embedded document
     * @throws SAXException
     */
    @Override
    public void startEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
            throws SAXException {
        super.startEmbeddedDocument(contentHandler, metadata);
    }

    /**
     * This is called after parsing an embedded document.
     *
     * @param contentHandler local contenthandler used on the embedded document
     * @param metadata metadata from the embedded document
     * @throws SAXException
     */
    @Override
    public void endEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
            throws SAXException {
        super.endEmbeddedDocument(contentHandler, metadata);
        addContent(contentHandler, metadata);
        try {
            metadataFilter.filter(metadata);
        } catch (TikaException e) {
            throw new SAXException(e);
        }

        if (metadata.size() > 0) {
            metadataList.add(ParserUtils.cloneMetadata(metadata));
        }
    }

    /**
     * @param contentHandler content handler used on the main document
     * @param metadata metadata from the main document
     * @throws SAXException
     */
    @Override
    public void endDocument(ContentHandler contentHandler, Metadata metadata) throws SAXException {
        super.endDocument(contentHandler, metadata);
        addContent(contentHandler, metadata);
        try {
            metadataFilter.filter(metadata);
        } catch (TikaException e) {
            throw new SAXException(e);
        }

        if (metadata.size() > 0) {
            metadataList.add(0, ParserUtils.cloneMetadata(metadata));
        }
    }

    /**
     * @return a list of Metadata objects, one for the main document and one for each embedded
     *     document
     */
    public List<Metadata> getMetadataList() {
        return metadataList;
    }

    void addContent(ContentHandler handler, Metadata metadata) {

        if (handler.getClass().equals(DefaultHandler.class)) {
            // no-op: we can't rely on just testing for
            // empty content because DefaultHandler's toString()
            // returns e.g. "org.xml.sax.helpers.DefaultHandler@6c8b1edd"
        } else {
            String content = handler.toString();
            if (content != null && content.trim().length() > 0) {
                metadata.add(TikaCoreProperties.TIKA_CONTENT, content);
                metadata.add(
                        TikaCoreProperties.TIKA_CONTENT_HANDLER,
                        handler.getClass().getSimpleName());
            }
        }
    }
}
