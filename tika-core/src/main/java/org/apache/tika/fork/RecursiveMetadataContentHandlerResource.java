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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class RecursiveMetadataContentHandlerResource implements ForkResource {

    private static final ContentHandler DEFAULT_HANDLER = new DefaultHandler();
    private final AbstractRecursiveParserWrapperHandler handler;

    public RecursiveMetadataContentHandlerResource(RecursiveParserWrapperHandler handler) {
        this.handler = handler;
    }

    public Throwable process(DataInputStream input, DataOutputStream output) throws IOException {
        try {
            internalProcess(input);
            return null;
        } catch (SAXException e) {
            return e;
        }
    }

    private void internalProcess(DataInputStream input) throws IOException, SAXException {
        byte embeddedOrMain = input.readByte();
        byte handlerAndMetadataOrMetadataOnly = input.readByte();

        ContentHandler localContentHandler = DEFAULT_HANDLER;
        if (handlerAndMetadataOrMetadataOnly
                == RecursiveMetadataContentHandlerProxy.HANDLER_AND_METADATA) {
            localContentHandler = (ContentHandler) readObject(input);
        } else if (handlerAndMetadataOrMetadataOnly
                != RecursiveMetadataContentHandlerProxy.METADATA_ONLY) {
            throw new IllegalArgumentException(
                    "Expected HANDLER_AND_METADATA or METADATA_ONLY, but got:"
                            + handlerAndMetadataOrMetadataOnly);
        }

        Metadata metadata = (Metadata) readObject(input);
        if (embeddedOrMain == RecursiveMetadataContentHandlerProxy.EMBEDDED_DOCUMENT) {
            handler.endEmbeddedDocument(localContentHandler, metadata);
        } else if (embeddedOrMain == RecursiveMetadataContentHandlerProxy.MAIN_DOCUMENT) {
            handler.endDocument(localContentHandler, metadata);
        } else {
            throw new IllegalArgumentException(
                    "Expected either 0x01 or 0x02, but got: " + embeddedOrMain);
        }
        byte isComplete = input.readByte();
        if (isComplete != RecursiveMetadataContentHandlerProxy.COMPLETE) {
            throw new IOException("Expected the 'complete' signal, but got: " + isComplete);
        }
    }

    private Object readObject(DataInputStream inputStream) throws IOException {
        try {
            return ForkObjectInputStream.readObject(inputStream, this.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }
}
