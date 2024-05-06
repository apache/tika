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
package org.apache.tika.fork;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This class calls #toString() on the ContentHandler, inserts it into the Metadata object and
 * serializes the Metadata object. Ideally, this would serialize the ContentHandler and the Metadata
 * object as separate objects, but we can't guarantee that the ContentHandler is Serializable (e.g.
 * the StringWriter in the WriteOutContentHandler).
 */
class RecursiveMetadataContentHandlerProxy extends RecursiveParserWrapperHandler
        implements ForkProxy {

    public static final byte EMBEDDED_DOCUMENT = 1;
    public static final byte MAIN_DOCUMENT = 2;
    public static final byte HANDLER_AND_METADATA = 3;
    public static final byte METADATA_ONLY = 4;
    public static final byte COMPLETE = 5;

    /** Serial version UID */
    private static final long serialVersionUID = 737511106054617524L;

    private final int resource;

    private transient DataOutputStream output;

    public RecursiveMetadataContentHandlerProxy(
            int resource, ContentHandlerFactory contentHandlerFactory) {
        super(contentHandlerFactory);
        this.resource = resource;
    }

    public void init(DataInputStream input, DataOutputStream output) {
        this.output = output;
    }

    @Override
    public void endEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
            throws SAXException {
        proxyBackToClient(EMBEDDED_DOCUMENT, contentHandler, metadata);
    }

    @Override
    public void endDocument(ContentHandler contentHandler, Metadata metadata) throws SAXException {
        if (hasHitMaximumEmbeddedResources()) {
            metadata.set(EMBEDDED_RESOURCE_LIMIT_REACHED, "true");
        }
        proxyBackToClient(MAIN_DOCUMENT, contentHandler, metadata);
    }

    private void proxyBackToClient(
            int embeddedOrMainDocument, ContentHandler contentHandler, Metadata metadata)
            throws SAXException {
        try {
            output.write(ForkServer.RESOURCE);
            output.writeByte(resource);
            output.writeByte(embeddedOrMainDocument);
            boolean success = false;
            if (contentHandler instanceof Serializable) {
                byte[] bytes = null;
                try {
                    bytes = serialize(contentHandler);
                    success = true;
                } catch (NotSerializableException e) {
                    // object lied
                }
                if (success) {

                    output.write(HANDLER_AND_METADATA);
                    sendBytes(bytes);
                    send(metadata);
                    output.writeByte(COMPLETE);
                    return;
                }
            }
            // if contenthandler is not allegedly or actually Serializable
            // fall back to adding contentHandler.toString() to the metadata object
            // and send that.
            metadata.set(TikaCoreProperties.TIKA_CONTENT, contentHandler.toString());
            output.writeByte(METADATA_ONLY);
            send(metadata);
            output.writeByte(COMPLETE);
        } catch (IOException e) {
            throw new SAXException(e);
        } finally {
            doneSending();
        }
    }

    private void send(Object object) throws IOException {
        byte[] bytes = serialize(object);
        sendBytes(bytes);
    }

    private void sendBytes(byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    private byte[] serialize(Object object) throws IOException {
        // can't figure out why I'm getting an IllegalAccessException
        // when I try to use ForkedObjectInputStream, but
        // not when I do this manually ?!
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
            oos.flush();
        }
        return bos.toByteArray();
    }

    private void doneSending() throws SAXException {
        try {
            output.flush();
        } catch (IOException e) {
            throw new SAXException("Unexpected fork proxy problem", e);
        }
    }
}
