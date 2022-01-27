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


import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.io.LookaheadInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Currently only used in tests.  Unlike {@link DefaultZipContainerDetector},
 * this will never try to open the File as a ZipFile; this relies solely
 * on streaming detection.
 */
public class StreamingZipContainerDetector extends DefaultZipContainerDetector {


    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2891763938430295453L;


    public StreamingZipContainerDetector() {
        this(new ServiceLoader(StreamingZipContainerDetector.class.getClassLoader(),
                LoadErrorHandler.WARN, false));
    }

    public StreamingZipContainerDetector(ServiceLoader loader) {
        super(loader);
    }

    public StreamingZipContainerDetector(List<ZipContainerDetector> zipDetectors) {
        //in initial development, OPCBased needed to be last.
        //this should have been fixed.
        super(zipDetectors);
    }


    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        // Check if we have access to the document
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        byte[] prefix = new byte[1024]; // enough for all known archive formats
        input.mark(1024);
        int length = -1;
        try {
            length = IOUtils.read(input, prefix, 0, 1024);
        } finally {
            input.reset();
        }

        MediaType type = detectArchiveFormat(prefix, length);

        if (type == TIFF) {
            return TIFF;
        } else if (isZipArchive(type)) {

            try (LookaheadInputStream lookahead = new LookaheadInputStream(input, markLimit)) {
                return detectStreaming(lookahead, metadata);
            }
        } else if (!type.equals(MediaType.OCTET_STREAM)) {
            return type;
        } else {
            return detectCompressorFormat(prefix, length);
        }
    }
}
