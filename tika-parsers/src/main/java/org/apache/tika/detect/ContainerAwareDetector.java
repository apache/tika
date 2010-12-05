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
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

import org.apache.poi.poifs.common.POIFSConstants;
import org.apache.poi.poifs.storage.HeaderBlockConstants;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;


/**
 * A detector that knows about the container formats that we support 
 *  (eg POIFS, Zip), and is able to peek inside them to better figure 
 *  out the contents.
 * Delegates to another {@link Detector} (normally {@link MimeTypes})
 *  to handle detection for non container formats. 
 * Should normally be used with a {@link TikaInputStream} to minimise 
 *  the memory usage.
 */
public class ContainerAwareDetector implements Detector {

    private Detector fallbackDetector;

    private Detector zipDetector;

    private Detector poifsDetector;

    /**
     * Creates a new container detector, which will use the
     *  given detector for non container formats.
     * @param fallbackDetector The detector to use for non-containers
     */
    public ContainerAwareDetector(Detector fallbackDetector) {
        this.fallbackDetector = fallbackDetector;
        poifsDetector = new POIFSContainerDetector();
        zipDetector = new ZipContainerDetector();
    }

    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        MediaType type = zipDetector.detect(input, metadata);
        if (MediaType.OCTET_STREAM.equals(type)) {
            type = poifsDetector.detect(input, metadata);
        }
        if (MediaType.OCTET_STREAM.equals(type)) {
            return fallbackDetector.detect(input, metadata);
        }
        return type;
    }

}
