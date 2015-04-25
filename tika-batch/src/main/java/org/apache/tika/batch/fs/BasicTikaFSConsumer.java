package org.apache.tika.batch.fs;

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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.OutputStreamFactory;
import org.apache.tika.batch.ParserFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ContentHandlerFactory;
import org.xml.sax.ContentHandler;

/**
 * Basic FileResourceConsumer that reads files from an input
 * directory and writes content to the output directory.
 * <p>
 * This catches all exceptions and errors and then logs them.
 * This will re-throw errors.
 *
 */
public class BasicTikaFSConsumer extends AbstractFSConsumer {

    private boolean parseRecursively = true;
    private final ParserFactory parserFactory;
    private final ContentHandlerFactory contentHandlerFactory;
    private final OutputStreamFactory fsOSFactory;
    private final TikaConfig config;
    private String outputEncoding = IOUtils.UTF_8.toString();


    public BasicTikaFSConsumer(ArrayBlockingQueue<FileResource> queue,
                               ParserFactory parserFactory,
                               ContentHandlerFactory contentHandlerFactory,
                               OutputStreamFactory fsOSFactory,
                               TikaConfig config) {
        super(queue);
        this.parserFactory = parserFactory;
        this.contentHandlerFactory = contentHandlerFactory;
        this.fsOSFactory = fsOSFactory;
        this.config = config;
    }

    @Override
    public boolean processFileResource(FileResource fileResource) {

        Parser parser = parserFactory.getParser(config);
        ParseContext context = new ParseContext();
        if (parseRecursively) {
            context.set(Parser.class, parser);
        }

        OutputStream os = getOutputStream(fsOSFactory, fileResource);
        //os can be null if fsOSFactory is set to skip processing a file if the output
        //file already exists
        if (os == null) {
            logger.debug("Skipping: " + fileResource.getMetadata().get(FSProperties.FS_REL_PATH));
            return false;
        }

        InputStream is = getInputStream(fileResource);
        if (is == null) {
            IOUtils.closeQuietly(os);
            return false;
        }
        ContentHandler handler;
        try {
            handler = contentHandlerFactory.getNewContentHandler(os, getOutputEncoding());
        } catch (UnsupportedEncodingException e) {
            incrementHandledExceptions();
            logger.error(getXMLifiedLogMsg("output_encoding_ex",
                    fileResource.getResourceId(), e));
            flushAndClose(os);
            throw new RuntimeException(e.getMessage());
        }

        //now actually call parse!
        Throwable thrown = null;
        try {
            parse(fileResource.getResourceId(), parser, is, handler,
                    fileResource.getMetadata(), context);
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

    public String getOutputEncoding() {
        return outputEncoding;
    }

    public void setOutputEncoding(String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }
}
