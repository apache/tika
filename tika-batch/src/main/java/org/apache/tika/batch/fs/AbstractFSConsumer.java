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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.tika.batch.BatchNoRestartError;
import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.batch.OutputStreamFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;

public abstract class AbstractFSConsumer extends FileResourceConsumer {

    public AbstractFSConsumer(ArrayBlockingQueue<FileResource> fileQueue) {
        super(fileQueue);
    }

    /**
     * Use this for consistent logging of exceptions.  Clients must
     * check for whether the os is null, which is the signal
     * that the output file already exists and should be skipped.
     *
     * @param fsOSFactory factory that creates the outputstream
     * @param fileResource used by the OSFactory to create the stream
     * @return the OutputStream or null if the output file already exists
     */
    protected OutputStream getOutputStream(OutputStreamFactory fsOSFactory,
                                           FileResource fileResource) {
        OutputStream os = null;
        try {
            os = fsOSFactory.getOutputStream(fileResource.getMetadata());
        } catch (IOException e) {
            //This can happen if the disk has run out of space,
            //or if there was a failure with mkdirs in fsOSFactory
            logger.error("{}", getXMLifiedLogMsg(IO_OS,
                    fileResource.getResourceId(), e));
            throw new BatchNoRestartError("IOException trying to open output stream for " +
                    fileResource.getResourceId() + " :: " + e.getMessage());
        }
        return os;
    }

    /**
     *
     * @param fileResource
     * @return inputStream, can be null if there is an exception opening IS
     */
    protected InputStream getInputStream(FileResource fileResource) {
        InputStream is = null;
        try {
            is = fileResource.openInputStream();
        } catch (IOException e) {
            logger.warn("{}", getXMLifiedLogMsg(IO_IS,
                    fileResource.getResourceId(), e));
            flushAndClose(is);
        }
        return is;
    }

}
