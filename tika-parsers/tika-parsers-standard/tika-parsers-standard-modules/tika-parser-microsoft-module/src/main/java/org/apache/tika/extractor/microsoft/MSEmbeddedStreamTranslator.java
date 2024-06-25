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
package org.apache.tika.extractor.microsoft;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.extractor.EmbeddedStreamTranslator;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.microsoft.OfficeParser;

public class MSEmbeddedStreamTranslator implements EmbeddedStreamTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(MSEmbeddedStreamTranslator.class);

    @Override
    public boolean shouldTranslate(InputStream inputStream, Metadata metadata) throws IOException {
        String contentType = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);
        if ("application/vnd.openxmlformats-officedocument.oleObject".equals(contentType)) {
            return true;
        } else if (inputStream instanceof TikaInputStream) {
            TikaInputStream tin = (TikaInputStream) inputStream;
            if (tin.getOpenContainer() != null &&
                    tin.getOpenContainer() instanceof DirectoryEntry) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InputStream translate(InputStream inputStream, Metadata metadata) throws IOException {
        String contentType = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);
        if ("application/vnd.openxmlformats-officedocument.oleObject".equals(contentType)) {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            IOUtils.copy(inputStream, bos);
            POIFSFileSystem poifs = new POIFSFileSystem(bos.toInputStream());
            OfficeParser.POIFSDocumentType type = OfficeParser.POIFSDocumentType.detectType(poifs);
            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);

            byte[] data = bos.toByteArray();
            if (type == OfficeParser.POIFSDocumentType.OLE10_NATIVE) {
                try {
                    Ole10Native ole = Ole10Native.createFromEmbeddedOleObject(poifs);
                    if (ole.getDataSize() > 0) {
                        name = ole.getLabel();
                        data = ole.getDataBuffer();
                    }
                } catch (Ole10NativeException ex) {
                    LOG.warn("Skipping invalid part", ex);
                }
            } else {
                name += '.' + type.getExtension();
            }
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            return new UnsynchronizedByteArrayInputStream(data);
        } else if (inputStream instanceof TikaInputStream) {
            TikaInputStream tin = (TikaInputStream) inputStream;

            if (tin.getOpenContainer() != null &&
                    tin.getOpenContainer() instanceof DirectoryEntry) {
                POIFSFileSystem fs = new POIFSFileSystem();
                copy((DirectoryEntry) tin.getOpenContainer(), fs.getRoot());
                try (UnsynchronizedByteArrayOutputStream bos2 = UnsynchronizedByteArrayOutputStream.builder().get()) {
                    fs.writeFilesystem(bos2);
                    return bos2.toInputStream();
                }
            }
        }
        return inputStream;
    }

    protected void copy(DirectoryEntry sourceDir, DirectoryEntry destDir) throws IOException {
        for (Entry entry : sourceDir) {
            if (entry instanceof DirectoryEntry) {
                // Need to recurse
                DirectoryEntry newDir = destDir.createDirectory(entry.getName());
                copy((DirectoryEntry) entry, newDir);
            } else {
                // Copy entry
                try (InputStream contents = new DocumentInputStream((DocumentEntry) entry)) {
                    destDir.createDocument(entry.getName(), contents);
                }
            }
        }
    }
}
