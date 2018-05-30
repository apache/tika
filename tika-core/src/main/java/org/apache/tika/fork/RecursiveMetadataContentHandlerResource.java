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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

class RecursiveMetadataContentHandlerResource implements ForkResource {

    private static final ContentHandler DEFAULT_HANDLER = new DefaultHandler();
    private final AbstractRecursiveParserWrapperHandler handler;

    public RecursiveMetadataContentHandlerResource(AbstractRecursiveParserWrapperHandler handler) {
        this.handler = handler;
    }

    public Throwable process(DataInputStream input, DataOutputStream output)
            throws IOException {
        try {
            internalProcess(input);
            return null;
        } catch (SAXException e) {
            return e;
        }
    }

    private void internalProcess(DataInputStream input)
            throws IOException, SAXException {
        int type = input.readByte();
        if (type == RecursiveMetadataContentHandlerProxy.EMBEDDED_DOCUMENT) {
            Metadata metadata = null;
            try {
                metadata = (Metadata)ForkObjectInputStream.readObject(input, this.getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
            byte isComplete = input.readByte();
            if (isComplete != RecursiveMetadataContentHandlerProxy.COMPLETE) {
                throw new IOException("Expected the 'complete' signal, but got: "+isComplete);
            }
            handler.endEmbeddedDocument(DEFAULT_HANDLER, metadata);
        } else if (type == RecursiveMetadataContentHandlerProxy.MAIN_DOCUMENT) {
            Metadata metadata = null;
            try {
                metadata = (Metadata)ForkObjectInputStream.readObject(input, this.getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
            byte isComplete = input.readByte();
            if (isComplete != RecursiveMetadataContentHandlerProxy.COMPLETE) {
                throw new IOException("Expected the 'complete' signal, but got: "+isComplete);
            }
            handler.endDocument(DEFAULT_HANDLER, metadata);
        } else {
            throw new IllegalArgumentException("I regret that I don't understand: "+type);
        }
    }

    private Metadata deserializeMetadata(DataInputStream dataInputStream) throws IOException {
        int length = dataInputStream.readInt();
        byte[] data = new byte[length];
        dataInputStream.readFully(data);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        Object obj = null;
        try {
            obj = ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        return (Metadata) obj;
    }
    private ContentHandler deserializeContentHandler(DataInputStream dataInputStream) throws IOException {
        int length = dataInputStream.readInt();
        byte[] data = new byte[length];
        dataInputStream.readFully(data);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        Object obj = null;
        try {
            obj = ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        return (ContentHandler)obj;
    }
}
