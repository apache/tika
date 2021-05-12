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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;

import org.apache.tika.config.Field;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class DefaultZipContainerDetector implements Detector {

    //Regrettably, some tiff files can be incorrectly identified
    //as tar files.  We need this ugly workaround to rule out TIFF.
    //If commons-compress ever chooses to take over TIFF detection
    //we can remove all of this. See TIKA-2591.
    final static MediaType TIFF = MediaType.image("tiff");
    final static byte[][] TIFF_SIGNATURES = new byte[3][];
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2891763938430295453L;

    static {
        TIFF_SIGNATURES[0] = new byte[]{'M', 'M', 0x00, 0x2a};
        TIFF_SIGNATURES[1] = new byte[]{'I', 'I', 0x2a, 0x00};
        TIFF_SIGNATURES[2] = new byte[]{'M', 'M', 0x00, 0x2b};
    }

    //this has to be > 100,000 to handle some of the iworks files
    //in our unit tests
    @Field
    int markLimit = 16 * 1024 * 1024;

    List<ZipContainerDetector> zipDetectors;

    public DefaultZipContainerDetector() {
        this(new ServiceLoader(DefaultZipContainerDetector.class.getClassLoader()));
    }

    public DefaultZipContainerDetector(ServiceLoader loader) {
        this(loader.loadServiceProviders(ZipContainerDetector.class));
    }

    public DefaultZipContainerDetector(List<ZipContainerDetector> zipDetectors) {
        //TODO: OPCBased needs to be last!!!
        this.zipDetectors = zipDetectors;
    }

    static boolean isZipArchive(MediaType type) {
        return type.equals(PackageConstants.ZIP) || type.equals(PackageConstants.JAR);
    }

    private static boolean isTiff(byte[] prefix) {
        for (byte[] sig : TIFF_SIGNATURES) {
            if (arrayStartWith(sig, prefix)) {
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

    static MediaType detectArchiveFormat(byte[] prefix, int length) {
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

    static MediaType detectCompressorFormat(byte[] prefix, int length) {
        try {
            String type =
                    CompressorStreamFactory.detect(new ByteArrayInputStream(prefix, 0, length));
            return CompressorConstants.getMediaType(type);
        } catch (CompressorException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    /**
     * If this is less than 0, the file will be spooled to disk,
     * and detection will run on the full file.
     * If this is greater than 0, the {@link DeprecatedStreamingZipContainerDetector}
     * will be called only up to the markLimit.
     *
     * @param markLimit mark limit for streaming detection
     */
    @Field
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
            return detectStreaming(input, metadata);
        } else if (!type.equals(MediaType.OCTET_STREAM)) {
            return type;
        } else {
            return detectCompressorFormat(prefix, length);
        }
    }

    /**
     * This will call TikaInputStream's getFile(). If there are no exceptions,
     * it will place the ZipFile in TikaInputStream's openContainer and leave it
     * open.
     *
     * @param tis
     * @return
     */
    private MediaType detectZipFormatOnFile(TikaInputStream tis) {
        try {
            ZipFile zip = new ZipFile(tis.getFile()); // TODO: hasFile()?

            try {
                for (ZipContainerDetector zipDetector : zipDetectors) {
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

    MediaType detectStreaming(InputStream input, Metadata metadata) throws IOException {
        BoundedInputStream boundedInputStream = new BoundedInputStream(markLimit, input);
        boundedInputStream.mark(markLimit);
        try {
            return detectStreaming(boundedInputStream, metadata, false);
        } finally {
            boundedInputStream.reset();
        }
    }

    MediaType detectStreaming(InputStream input, Metadata metadata, boolean allowStoredEntries)
            throws IOException {
        StreamingDetectContext detectContext = new StreamingDetectContext();
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new CloseShieldInputStream(input), "UTF8", false, allowStoredEntries)) {
            ZipArchiveEntry zae = zis.getNextZipEntry();
            while (zae != null) {
                MediaType mt = detect(zae, zis, detectContext);
                if (mt != null) {
                    return mt;
                }
                zae = zis.getNextZipEntry();
            }
        } catch (UnsupportedZipFeatureException zfe) {
            if (allowStoredEntries == false &&
                    zfe.getFeature() == UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR) {
                input.reset();
                return detectStreaming(input, metadata, true);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (EOFException e) {
            //truncated zip -- swallow
        } catch (IOException e) {
            //another option for a truncated zip
        }

        return finalDetect(detectContext);
    }


    private MediaType detect(ZipArchiveEntry zae, ZipArchiveInputStream zis,
                             StreamingDetectContext detectContext) throws IOException {
        for (ZipContainerDetector d : zipDetectors) {
            MediaType mt = d.streamingDetectUpdate(zae, zis, detectContext);
            if (mt != null) {
                return mt;
            }
        }
        return null;
    }

    private MediaType finalDetect(StreamingDetectContext detectContext) {
        for (ZipContainerDetector d : zipDetectors) {
            MediaType mt = d.streamingDetectFinal(detectContext);
            if (mt != null) {
                return mt;
            }
        }
        return MediaType.APPLICATION_ZIP;
    }
}
