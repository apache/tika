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

import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * This is a special handler to be used only with the
 * {@link org.apache.tika.parser.RecursiveParserWrapper}.
 * It allows for finer-grained processing of embedded documents than in the legacy handlers.
 * Subclasses can choose how to process individual embedded documents.
 */
public abstract class AbstractRecursiveParserWrapperHandler extends DefaultHandler
        implements Serializable {

    public final static Property EMBEDDED_RESOURCE_LIMIT_REACHED = Property.internalBoolean(
            TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX + "embedded_resource_limit_reached");
    private static final int MAX_DEPTH = 100;
    private final ContentHandlerFactory contentHandlerFactory;
    private final int maxEmbeddedResources;
    private int embeddedResources = 0;
    private int embeddedDepth = 0;

    public AbstractRecursiveParserWrapperHandler(ContentHandlerFactory contentHandlerFactory) {
        this(contentHandlerFactory, -1);
    }

    public AbstractRecursiveParserWrapperHandler(ContentHandlerFactory contentHandlerFactory,
                                                 int maxEmbeddedResources) {
        this.contentHandlerFactory = contentHandlerFactory;
        this.maxEmbeddedResources = maxEmbeddedResources;
    }

    public ContentHandler getNewContentHandler() {
        return contentHandlerFactory.getNewContentHandler();
    }

    public ContentHandler getNewContentHandler(OutputStream os, Charset charset) {
        return contentHandlerFactory.getNewContentHandler(os, charset);
    }

    /**
     * This is called before parsing each embedded document.  Override this
     * for custom behavior.  Make sure to call this in your custom classes
     * because this tracks the number of embedded documents.
     *
     * @param contentHandler local handler to be used on this embedded document
     * @param metadata       embedded document's metadata
     */
    public void startEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
            throws SAXException {
        embeddedResources++;
        embeddedDepth++;
        if (embeddedDepth >= MAX_DEPTH) {
            throw new SAXException("Max embedded depth reached: " + embeddedDepth);
        }
        metadata.set(TikaCoreProperties.EMBEDDED_DEPTH, embeddedDepth);
    }

    /**
     * This is called after parsing each embedded document.  Override this
     * for custom behavior.  This is currently a no-op.
     *
     * @param contentHandler content handler that was used on this embedded document
     * @param metadata       metadata for this embedded document
     * @throws SAXException
     */
    public void endEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
            throws SAXException {
        embeddedDepth--;
    }

    /**
     * This is called after the full parse has completed.  Override this
     * for custom behavior.  Make sure to call this as <code>super.endDocument(...)</code>
     * in subclasses because this adds whether or not the embedded resource
     * maximum has been hit to the metadata.
     *
     * @param contentHandler content handler that was used on the main document
     * @param metadata       metadata that was gathered for the main document
     * @throws SAXException
     */
    public void endDocument(ContentHandler contentHandler, Metadata metadata) throws SAXException {
        if (hasHitMaximumEmbeddedResources()) {
            metadata.set(EMBEDDED_RESOURCE_LIMIT_REACHED, "true");
        }
        metadata.set(TikaCoreProperties.EMBEDDED_DEPTH, 0);
    }

    /**
     * @return whether this handler has hit the maximum embedded resources during the parse
     */
    public boolean hasHitMaximumEmbeddedResources() {
        return maxEmbeddedResources > -1 && embeddedResources >= maxEmbeddedResources;
    }

    public ContentHandlerFactory getContentHandlerFactory() {
        return contentHandlerFactory;
    }
}
