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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.DetectHelper;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * This class is designed to detect subtypes of zip-based file formats.
 * For the sake of efficiency, it also detects archive and compressor formats
 * via commons-compress.
 * <p>
 * As a first step, it uses commons-compress to detect any archive format
 * supported by commons-compress. If "zip" file is detected, then the
 * ZipContainerDetectors are run to try to identify a subtype.
 * <p>
 * If an archive format that is not a zip is detected, that mime type is returned.
 * <p>
 * Finally, if the file is not detected as an archive format, this runs
 * commons-compress' compressor format detector.
 * <p>
 * For {@link TikaInputStream}, file-based detection is used (TikaInputStream
 * handles spilling to disk automatically if needed).
 */
@TikaComponent
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

    private static final Logger LOG = LoggerFactory.getLogger(DefaultZipContainerDetector.class);

    static {
        TIFF_SIGNATURES[0] = new byte[]{'M', 'M', 0x00, 0x2a};
        TIFF_SIGNATURES[1] = new byte[]{'I', 'I', 0x2a, 0x00};
        TIFF_SIGNATURES[2] = new byte[]{'M', 'M', 0x00, 0x2b};
    }

    private transient ServiceLoader loader;

    protected List<ZipContainerDetector> staticZipDetectors;

    public DefaultZipContainerDetector() {
        this(new ServiceLoader(DefaultZipContainerDetector.class.getClassLoader(), false));
    }

    public DefaultZipContainerDetector(ServiceLoader loader) {
        this.loader = loader;
        staticZipDetectors = loader.loadStaticServiceProviders(ZipContainerDetector.class);
    }

    public DefaultZipContainerDetector(List<ZipContainerDetector> zipDetectors) {
        staticZipDetectors = zipDetectors;
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
            String name = ArchiveStreamFactory.detect(
                    UnsynchronizedByteArrayInputStream.builder().setByteArray(prefix).setLength(length).get());
            return PackageConstants.getMediaType(name);
        } catch (IOException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    static MediaType detectCompressorFormat(byte[] prefix, int length) {
        try {
            String type =
                    CompressorStreamFactory.detect(
                            UnsynchronizedByteArrayInputStream.builder().setByteArray(prefix).setLength(length).get());
            return CompressorConstants.getMediaType(type);
        } catch (IOException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    private static final int MIN_BUFFER_SIZE = 1024;

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext) throws IOException {
        // Check if we have access to the document
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }

        byte[] prefix = new byte[MIN_BUFFER_SIZE];
        tis.mark(MIN_BUFFER_SIZE);
        int length = -1;
        try {
            length = IOUtils.read(tis, prefix, 0, MIN_BUFFER_SIZE);
        } finally {
            tis.reset();
        }

        MediaType type = detectArchiveFormat(prefix, length);

        if (type == TIFF) {
            return TIFF;
        } else if (isZipArchive(type)) {
            // If content is truncated for detection, use streaming detection
            // since file-based detection with ZipFile requires the central directory
            // which is at the end of the file
            if (DetectHelper.isContentTruncatedForDetection(metadata)) {
                int contentLength = DetectHelper.getDetectionContentLength(metadata);
                tis.mark(contentLength > 0 ? contentLength : MIN_BUFFER_SIZE);
                try {
                    return detectStreaming(tis, metadata, false);
                } finally {
                    tis.reset();
                }
            }
            //spool to disk if not already file-backed and detect on file
            return detectZipFormatOnFile(tis, metadata, parseContext);
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
    private MediaType detectZipFormatOnFile(TikaInputStream tis, Metadata metadata, ParseContext parseContext) {
        ZipFile zip = null;
        try {
            zip = ZipFile.builder().setFile(tis.getFile()).get();

            for (ZipContainerDetector zipDetector : getDetectors()) {
                MediaType type = zipDetector.detect(zip, tis);
                if (type != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} detected {}", zipDetector.getClass(),
                                type.toString());
                    }
                    //e.g. if OPCPackage has already been set
                    //don't overwrite it with the zip
                    if (tis.getOpenContainer() == null) {
                        tis.setOpenContainer(zip);
                    } else {
                        tis.addCloseableResource(zip);
                    }
                    return type;
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} detected null", zipDetector.getClass());
                    }
                }
            }
        } catch (IOException e) {
            //do nothing
        }
        // Fallback: it's still a zip file, we just don't know what kind of one
        if (zip != null) {
            IOUtils.closeQuietly(zip);
            return MediaType.APPLICATION_ZIP;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("zip file failed to open; attempting streaming detect. Results may be imprecise");
        }
        //problem opening zip file (truncated?)
        try {
            return detectStreamingFromPath(tis.getPath(), metadata, false);
        } catch (IOException e) {
            //swallow
        }
        return MediaType.APPLICATION_ZIP;

    }

    private MediaType detectStreaming(InputStream input, Metadata metadata, boolean allowStoredEntries)
            throws IOException {
        StreamingDetectContext detectContext = new StreamingDetectContext();
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                CloseShieldInputStream.wrap(input), "UTF8", false, allowStoredEntries)) {
            ZipArchiveEntry zae = zis.getNextEntry();
            while (zae != null) {
                MediaType mt = detect(zae, zis, detectContext);
                if (mt != null) {
                    return mt;
                }
                zae = zis.getNextEntry();
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

    private MediaType detectStreamingFromPath(Path p, Metadata metadata, boolean allowStoredEntries)
            throws IOException {
        StreamingDetectContext detectContext = new StreamingDetectContext();
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                Files.newInputStream(p), "UTF8", false, allowStoredEntries)) {
            ZipArchiveEntry zae = zis.getNextEntry();
            while (zae != null) {
                MediaType mt = detect(zae, zis, detectContext);
                if (mt != null) {
                    return mt;
                }
                zae = zis.getNextEntry();
            }
        } catch (UnsupportedZipFeatureException zfe) {
            if (allowStoredEntries == false &&
                    zfe.getFeature() == UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR) {
                return detectStreamingFromPath(p, metadata, true);
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
        for (ZipContainerDetector d : getDetectors()) {
            MediaType mt = d.streamingDetectUpdate(zae, zis, detectContext);
            if (mt != null) {
                return mt;
            }
        }
        return null;
    }

    private MediaType finalDetect(StreamingDetectContext detectContext) {
        for (ZipContainerDetector d : getDetectors()) {
            MediaType mt = d.streamingDetectFinal(detectContext);
            if (mt != null) {
                return mt;
            }
        }
        return MediaType.APPLICATION_ZIP;
    }

    private List<ZipContainerDetector> getDetectors() {
        if (loader != null && loader.isDynamic()) {
            List<ZipContainerDetector> dynamicDetectors =
                    loader.loadDynamicServiceProviders(ZipContainerDetector.class);
            if (!dynamicDetectors.isEmpty()) {
                List<ZipContainerDetector> zipDetectors = new ArrayList<>(staticZipDetectors);
                zipDetectors.addAll(dynamicDetectors);
                return zipDetectors;
            } else {
                return staticZipDetectors;
            }
        }
        return staticZipDetectors;
    }
}
