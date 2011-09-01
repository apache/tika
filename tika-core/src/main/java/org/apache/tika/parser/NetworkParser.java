/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.TaggedContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class NetworkParser extends AbstractParser {

    private final URI uri;

    private final Set<MediaType> supportedTypes;

    public NetworkParser(URI uri, Set<MediaType> supportedTypes) {
        this.uri = uri;
        this.supportedTypes = supportedTypes;
    }

    public NetworkParser(URI uri) {
        this(uri, Collections.singleton(MediaType.OCTET_STREAM));
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            parse(tis, handler, metadata, context);
        } finally {
            tmp.dispose();
        }
    }

    private void parse(
            TikaInputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if ("telnet".equals(uri.getScheme())) {
            final Socket socket = new Socket(uri.getHost(), uri.getPort());
            try {
                new ParsingTask(stream, new FilterOutputStream(socket.getOutputStream()) {
                    @Override
                    public void close() throws IOException {
                        socket.shutdownOutput();
                    }
                }).parse(
                        socket.getInputStream(), handler, metadata, context);
            } finally {
                socket.close();
            }
        } else {
            URL url = uri.toURL();
            URLConnection connection = url.openConnection();
            connection.setDoOutput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            try {
                new ParsingTask(stream, connection.getOutputStream()).parse(
                        new CloseShieldInputStream(input),
                        handler, metadata, context);
            } finally {
                input.close();
            }
        }

    }

    private static class ParsingTask implements Runnable {

        private final TikaInputStream input;

        private final OutputStream output;

        private volatile Exception exception = null;

        public ParsingTask(TikaInputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        public void parse(
                InputStream stream, ContentHandler handler,
                Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            Thread thread = new Thread(this, "Tika network parser");
            thread.start();

            TaggedContentHandler tagged = new TaggedContentHandler(handler);
            try {
                context.getSAXParser().parse(
                        stream, new TeeContentHandler(
                                tagged, new MetaHandler(metadata)));
            } catch (SAXException e) {
                tagged.throwIfCauseOf(e);
                throw new TikaException(
                        "Invalid network parser output", e);
            } catch (IOException e) {
                throw new TikaException(
                        "Unable to read network parser output", e);
            } finally {
                try {
                    thread.join(1000);
                } catch (InterruptedException e) {
                    throw new TikaException("Network parser interrupted", e);
                }

                if (exception != null) {
                    input.throwIfCauseOf(exception);
                    throw new TikaException(
                            "Unexpected network parser error", exception);
                }
            }
        }

        //----------------------------------------------------------<Runnable>

        public void run() {
            try {
                try {
                    IOUtils.copy(input, output);
                } finally {
                    output.close();
                }
            } catch (Exception e) {
                exception = e;
            }
        }

    }

    private static class MetaHandler extends DefaultHandler {

        private final Metadata metadata;

        public MetaHandler(Metadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void startElement(
                String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            if ("http://www.w3.org/1999/xhtml".equals(uri)
                    && "meta".equals(localName)) {
                String name = attributes.getValue("", "name");
                String content = attributes.getValue("", "content");
                if (name != null && content != null) {
                    metadata.add(name, content);
                }
            }
        }

    }

}
