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
package org.apache.tika.detect.zip;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

public class OpenDocumentDetector implements ZipContainerDetector {
    private static final int MAX_MIME_TYPE = 1024;

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        try {
            ZipArchiveEntry mimetype = zip.getEntry("mimetype");
            if (mimetype != null && mimetype.getSize() > 0) {
                try (InputStream stream = zip.getInputStream(mimetype)) {
                    return MediaType.parse(IOUtils.toString(stream, UTF_8));
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext)
            throws IOException {
        String name = zae.getName();
        if ("mimetype".equals(name)) {
            //can't rely on zae.getSize to determine if there is any
            //content here. :(
            UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
            BoundedInputStream bis = new BoundedInputStream(MAX_MIME_TYPE, zis);
            IOUtils.copy(bis, bos);
            //do anything with an inputstream > MAX_MIME_TYPE?
            if (bos.size() > 0) {
                //odt -- TODO -- check that the results are valid
                return MediaType.parse(bos.toString(UTF_8));
            }
        }
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        return null;
    }
}
