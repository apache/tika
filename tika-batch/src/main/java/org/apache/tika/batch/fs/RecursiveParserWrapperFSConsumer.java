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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.io.IOUtils;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.OutputStreamFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * This runs a RecursiveParserWrapper against an input file
 * and outputs the json metadata to an output file.
 */
public class RecursiveParserWrapperFSConsumer extends AbstractFSConsumer {

    private final Parser parser;
    private final ContentHandlerFactory contentHandlerFactory;
    private final OutputStreamFactory fsOSFactory;
    private final MetadataFilter metadataFilter;
    private String outputEncoding = "UTF-8";

    /**
     * @param queue
     * @param parser                -- must be RecursiveParserWrapper or a ForkParser that wraps a
     *                              RecursiveParserWrapper
     * @param contentHandlerFactory
     * @param fsOSFactory
     */
    public RecursiveParserWrapperFSConsumer(ArrayBlockingQueue<FileResource> queue, Parser parser, ContentHandlerFactory contentHandlerFactory, OutputStreamFactory fsOSFactory,
                                            MetadataFilter metadataFilter) {
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
            LOG.debug("Skipping: {}", fileResource
                    .getMetadata()
                    .get(FSProperties.FS_REL_PATH));
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

        Throwable thrown = null;
        List<Metadata> metadataList = null;
        Metadata containerMetadata = fileResource.getMetadata();
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(contentHandlerFactory, -1, metadataFilter);
        try {
            parse(fileResource.getResourceId(), parser, is, handler, containerMetadata, context);
        } catch (Throwable t) {
            thrown = t;
        } finally {
            metadataList = handler.getMetadataList();
            IOUtils.closeQuietly(is);
        }

        Writer writer = null;

        try {
            writer = new OutputStreamWriter(os, getOutputEncoding());
            JsonMetadataList.toJson(metadataList, writer);
        } catch (Exception e) {
            //this is a stop the world kind of thing
            LOG.error("{}", getXMLifiedLogMsg(IO_OS + "json", fileResource.getResourceId(), e));
            throw new RuntimeException(e);
        } finally {
            flushAndClose(writer);
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
}
