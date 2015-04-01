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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.OutputStreamFactory;
import org.apache.tika.batch.ParserFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.util.TikaExceptionFilter;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Basic FileResourceConsumer that reads files from an input
 * directory and writes content to the output directory.
 * <p/>
 * This tries to catch most of the common exceptions, log them and
 * store them in the metadata list output.
 */
public class RecursiveParserWrapperFSConsumer extends AbstractFSConsumer {


    private final ParserFactory parserFactory;
    private final ContentHandlerFactory contentHandlerFactory;
    private final OutputStreamFactory fsOSFactory;
    private final TikaConfig tikaConfig;
    private String outputEncoding = "UTF-8";
    //TODO: parameterize this
    private TikaExceptionFilter exceptionFilter = new TikaExceptionFilter();


    public RecursiveParserWrapperFSConsumer(ArrayBlockingQueue<FileResource> queue,
                                            ParserFactory parserFactory,
                                            ContentHandlerFactory contentHandlerFactory,
                                            OutputStreamFactory fsOSFactory, TikaConfig tikaConfig) {
        super(queue);
        this.parserFactory = parserFactory;
        this.contentHandlerFactory = contentHandlerFactory;
        this.fsOSFactory = fsOSFactory;
        this.tikaConfig = tikaConfig;
    }

    @Override
    public boolean processFileResource(FileResource fileResource) {

        Parser wrapped = parserFactory.getParser(tikaConfig);
        RecursiveParserWrapper parser = new RecursiveParserWrapper(wrapped, contentHandlerFactory);
        ParseContext context = new ParseContext();

//        if (parseRecursively == true) {
        context.set(Parser.class, parser);
//        }

        //try to open outputstream first
        OutputStream os = getOutputStream(fsOSFactory, fileResource);

        if (os == null) {
            logger.debug("Skipping: " + fileResource.getMetadata().get(FSProperties.FS_REL_PATH));
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
        try {
            parse(fileResource.getResourceId(), parser, is, new DefaultHandler(),
                    containerMetadata, context);
            metadataList = parser.getMetadata();
        } catch (Throwable t) {
            thrown = t;
            metadataList = parser.getMetadata();
            if (metadataList == null) {
                metadataList = new LinkedList<Metadata>();
            }
            Metadata m = null;
            if (metadataList.size() == 0) {
                m = containerMetadata;
            } else {
                //take the top metadata item
                m = metadataList.remove(0);
            }
            String stackTrace = exceptionFilter.getStackTrace(t);
            m.add(TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX+"runtime", stackTrace);
            metadataList.add(0, m);
        } finally {
            IOUtils.closeQuietly(is);
        }

        Writer writer = null;

        try {
            writer = new OutputStreamWriter(os, getOutputEncoding());
            JsonMetadataList.toJson(metadataList, writer);
        } catch (Exception e) {
            //this is a stop the world kind of thing
            logger.error("{}", getXMLifiedLogMsg(IO_OS+"json",
                    fileResource.getResourceId(), e));
            throw new RuntimeException(e);
        } finally {
            flushAndClose(writer);
        }

        if (thrown != null) {
            if (thrown instanceof Error) {
                throw (Error) thrown;
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
