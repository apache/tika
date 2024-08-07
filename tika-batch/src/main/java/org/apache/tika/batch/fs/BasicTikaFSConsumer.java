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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.OutputStreamFactory;
import org.apache.tika.batch.ParserFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ContentHandlerFactory;

/**
 * Basic FileResourceConsumer that reads files from an input
 * directory and writes content to the output directory.
 * <p>
 * This catches all exceptions and errors and then logs them.
 * This will re-throw errors.
 */
public class BasicTikaFSConsumer extends AbstractFSConsumer {

    private final Parser parser;
    private final ContentHandlerFactory contentHandlerFactory;
    private final OutputStreamFactory fsOSFactory;
    private boolean parseRecursively = true;
    private Charset outputEncoding = StandardCharsets.UTF_8;

    /**
     * @param queue
     * @param parserFactory
     * @param contentHandlerFactory
     * @param fsOSFactory
     * @param tikaConfig
     * @deprecated use {@link BasicTikaFSConsumer#BasicTikaFSConsumer(ArrayBlockingQueue,
     * Parser, ContentHandlerFactory, OutputStreamFactory)}
     */
    @Deprecated
    public BasicTikaFSConsumer(ArrayBlockingQueue<FileResource> queue, ParserFactory parserFactory, ContentHandlerFactory contentHandlerFactory, OutputStreamFactory fsOSFactory,
                               TikaConfig tikaConfig) {
        super(queue);
        this.parser = parserFactory.getParser(tikaConfig);
        this.contentHandlerFactory = contentHandlerFactory;
        this.fsOSFactory = fsOSFactory;
    }

    public BasicTikaFSConsumer(ArrayBlockingQueue<FileResource> queue, Parser parser, ContentHandlerFactory contentHandlerFactory, OutputStreamFactory fsOSFactory) {
        super(queue);
        this.parser = parser;
        this.contentHandlerFactory = contentHandlerFactory;
        this.fsOSFactory = fsOSFactory;
    }

    @Override
    public boolean processFileResource(FileResource fileResource) {

        ParseContext context = new ParseContext();
        if (parseRecursively) {
            context.set(Parser.class, parser);
        }

        OutputStream os = getOutputStream(fsOSFactory, fileResource);
        //os can be null if fsOSFactory is set to skip processing a file if the output
        //file already exists
        if (os == null) {
            LOG.debug("Skipping: {}", fileResource
                    .getMetadata()
                    .get(FSProperties.FS_REL_PATH));
            return false;
        }

        InputStream is = getInputStream(fileResource);
        if (is == null) {
            IOUtils.closeQuietly(os);
            return false;
        }
        ContentHandler handler;
        handler = contentHandlerFactory.getNewContentHandler(os, getOutputEncoding());


        //now actually call parse!
        Throwable thrown = null;
        try {
            parse(fileResource.getResourceId(), parser, is, handler, fileResource.getMetadata(), context);
        } catch (Error t) {
            throw t;
        } catch (Throwable t) {
            thrown = t;
        } finally {
            flushAndClose(os);
        }

        if (thrown != null) {
            return false;
        }
        return true;
    }

    public Charset getOutputEncoding() {
        return outputEncoding;
    }

    public void setOutputEncoding(Charset charset) {
        this.outputEncoding = charset;
    }
}
