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

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.tika.config.Field;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.LookaheadInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ZipContainerDetector implements Detector {




    //Regrettably, some tiff files can be incorrectly identified
    //as tar files.  We need this ugly workaround to rule out TIFF.
    //If commons-compress ever chooses to take over TIFF detection
    //we can remove all of this. See TIKA-2591.
    private final static MediaType TIFF = MediaType.image("tiff");
    private final static byte[][] TIFF_SIGNATURES = new byte[3][];
    static {
        TIFF_SIGNATURES[0] = new byte[]{'M','M',0x00,0x2a};
        TIFF_SIGNATURES[1] = new byte[]{'I','I',0x2a, 0x00};
        TIFF_SIGNATURES[2] = new byte[]{'M','M', 0x00, 0x2b};
    }

    /** Serial version UID */
    private static final long serialVersionUID = 2891763938430295453L;

    //this has to be > 100,000 to handle some of the iworks files
    //in our unit tests
    @Field
    int markLimit = 16 * 1024 * 1024;

    List<ZipDetector> zipDetectors;

    public ZipContainerDetector() {
        this(new ServiceLoader(DefaultEncodingDetector.class.getClassLoader()));
    }

    public ZipContainerDetector(ServiceLoader loader) {
        this(loader.loadServiceProviders(ZipDetector.class));
    }

    public ZipContainerDetector(List<ZipDetector> zipDetectors) {
        //OPCBased needs to be last!!!
        this.zipDetectors = zipDetectors;
    }

    /**
     * If this is less than 0, the file will be spooled to disk,
     * and detection will run on the full file.
     * If this is greater than 0, the {@link StreamingZipContainerDetector}
     * will be called only up to the markLimit.
     *
     * @param markLimit mark limit for streaming detection
     */
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
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

            if (TikaInputStream.isTikaInputStream(input)) {
                TikaInputStream tis = TikaInputStream.cast(input);
                if (markLimit < 0) {
                    tis.getFile();
                }
                if (tis.hasFile()) {
                    return detectZipFormatOnFile(tis);
                }
            }

            try (LookaheadInputStream lookahead = new LookaheadInputStream(input, markLimit)) {
                //TODO: figure out this one
                //return streamingZipContainerDetector.detect(lookahead, metadata);
            }
        } else if (!type.equals(MediaType.OCTET_STREAM)) {
            return type;
        } else {
            return detectCompressorFormat(prefix, length);
        }
        return PackageConstants.ZIP;
    }

    /**
     * This will call TikaInputStream's getFile(). If there are no exceptions,
     * it will place the ZipFile in TikaInputStream's openContainer and leave it
     * open.
     * @param tis
     * @return
     */
    private MediaType detectZipFormatOnFile(TikaInputStream tis) {
        try {
            ZipFile zip = new ZipFile(tis.getFile()); // TODO: hasFile()?

            try{
            for (ZipDetector zipDetector : zipDetectors) {
                MediaType type = zipDetector.detect(zip, tis);
                if (type != null) {
                    return type;
                }
            }
            } finally {
                tis.setOpenContainer(zip);
            }

        } catch (IOException e) {
            // ignore
        }
        // Fallback: it's still a zip file, we just don't know what kind of one
        return MediaType.APPLICATION_ZIP;
    }


    static boolean isZipArchive(MediaType type) {
        return type.equals(PackageConstants.ZIP)
                || type.equals(PackageConstants.JAR);
    }

    private static boolean isTiff(byte[] prefix) {
        for (byte[] sig : TIFF_SIGNATURES) {
            if(arrayStartWith(sig, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean arrayStartWith(byte[] needle, byte[] haystack) {
        if (haystack.length < needle.length) {
            return false;
        }
        for (int i = 0; i < needle.length; i++) {
            if (haystack[i] != needle[i]) {
                return false;
            }
        }
        return true;
    }

    private static MediaType detectArchiveFormat(byte[] prefix, int length) {
        if (isTiff(prefix)) {
            return TIFF;
        }
        try {
            String name = ArchiveStreamFactory.detect(new ByteArrayInputStream(prefix, 0, length));
            return PackageConstants.getMediaType(name);
        } catch (ArchiveException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    private static MediaType detectCompressorFormat(byte[] prefix, int length) {
        try {
            String type = CompressorStreamFactory.detect(new ByteArrayInputStream(prefix, 0, length));
            return CompressorConstants.getMediaType(type);
        } catch (CompressorException e) {
            return MediaType.OCTET_STREAM;
        }
    }
}
