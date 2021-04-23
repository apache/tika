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

package org.apache.tika.batch.fs;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.OutputStreamFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.serialization.JsonStreamingSerializer;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.ContentHandlerFactory;

/**
 * This uses the {@link JsonStreamingSerializer} to write out a
 * single metadata object at a time.
 */
public class StreamOutRPWFSConsumer extends AbstractFSConsumer {

    private final Parser parser;
    private final ContentHandlerFactory contentHandlerFactory;
    private final OutputStreamFactory fsOSFactory;
    private final MetadataFilter metadataFilter;
    private String outputEncoding = "UTF-8";


    public StreamOutRPWFSConsumer(ArrayBlockingQueue<FileResource> queue, Parser parser,
                                  ContentHandlerFactory contentHandlerFactory,
                                  OutputStreamFactory fsOSFactory, MetadataFilter metadataFilter) {
        super(queue);
        this.contentHandlerFactory = contentHandlerFactory;
        this.fsOSFactory = fsOSFactory;
        this.parser = parser;
        this.metadataFilter = metadataFilter;
    }

    @Override
    public boolean processFileResource(FileResource fileResource) {

        ParseContext context = new ParseContext();

        //try to open outputstream first
        OutputStream os = getOutputStream(fsOSFactory, fileResource);

        if (os == null) {
            LOG.debug("Skipping: {}", fileResource.getMetadata().get(FSProperties.FS_REL_PATH));
            return false;
        }

        //try to open the inputstream before the parse.
        //if the parse hangs or throws a nasty exception, at least there will
        //be a zero byte file there so that the batchrunner can skip that problematic
        //file during the next run.
        InputStream is = getInputStream(fileResource);
        if (is == null) {
            IOUtils.closeQuietly(os);
            return false;
        }

        Metadata containerMetadata = fileResource.getMetadata();
        JsonStreamingSerializer writer =
                new JsonStreamingSerializer(new OutputStreamWriter(os, StandardCharsets.UTF_8));

        WriteoutRPWHandler handler =
                new WriteoutRPWHandler(contentHandlerFactory, writer, metadataFilter);
        Throwable thrown = null;
        try {
            parse(fileResource.getResourceId(), parser, is, handler, containerMetadata, context);
        } catch (Throwable t) {
            thrown = t;
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                //this is a stop the world kind of thing
                LOG.error("{}", getXMLifiedLogMsg(IO_OS + "json", fileResource.getResourceId(), e));
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
        if (thrown != null) {
            if (thrown instanceof Error) {
                throw (Error) thrown;
            } else if (thrown instanceof SecurityException) {
                throw (SecurityException) thrown;
            } else {
                return false;
            }
        }
        return true;
    }

    public String getOutputEncoding() {
        return outputEncoding;
    }

    public void setOutputEncoding(String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    //extend AbstractRPWH instead of RecursiveParserWrapperHandler so that
    //if we use the ForkParser, the output will not have to be streamed
    //back to the proxy, but can
    //be written straight to disk.
    private class WriteoutRPWHandler extends AbstractRecursiveParserWrapperHandler {
        private final JsonStreamingSerializer jsonWriter;
        private final MetadataFilter metadataFilter;

        public WriteoutRPWHandler(ContentHandlerFactory contentHandlerFactory,
                                  JsonStreamingSerializer writer, MetadataFilter metadataFilter) {
            super(contentHandlerFactory);
            this.jsonWriter = writer;
            this.metadataFilter = metadataFilter;
        }

        @Override
        public void endEmbeddedDocument(ContentHandler contentHandler, Metadata metadata)
                throws SAXException {
            metadata.add(TikaCoreProperties.TIKA_CONTENT, contentHandler.toString());
            try {
                metadataFilter.filter(metadata);
            } catch (TikaException e) {
                throw new SAXException(e);
            }
            try {
                jsonWriter.add(metadata);
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endDocument(ContentHandler contentHandler, Metadata metadata)
                throws SAXException {
            endEmbeddedDocument(contentHandler, metadata);
        }
    }
}
