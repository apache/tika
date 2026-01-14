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
import java.util.List;

import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * A zip container detector that uses only streaming detection, never opening
 * the file as a ZipFile. This is primarily used in tests to verify streaming
 * detection behavior.
 * <p>
 * Unlike {@link DefaultZipContainerDetector}, this will never try to open
 * the File as a ZipFile; this relies solely on streaming detection.
 * <p>
 * If you need to limit the amount of data read during detection, wrap your
 * input stream in a {@link org.apache.tika.io.BoundedInputStream} before
 * passing it to the detector.
 */
public class StreamingZipContainerDetector extends DefaultZipContainerDetector {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2891763938430295453L;

    public StreamingZipContainerDetector() {
        this(new ServiceLoader(StreamingZipContainerDetector.class.getClassLoader(), false));
    }

    public StreamingZipContainerDetector(ServiceLoader loader) {
        super(loader);
    }

    public StreamingZipContainerDetector(List<ZipContainerDetector> zipDetectors) {
        super(zipDetectors);
    }

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext) throws IOException {
        // Check if we have access to the document
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }

        byte[] prefix = new byte[1024]; // enough for all known archive formats
        tis.mark(1024);
        int length = -1;
        try {
            length = IOUtils.read(tis, prefix, 0, 1024);
        } finally {
            tis.reset();
        }

        MediaType type = detectArchiveFormat(prefix, length);

        if (type == TIFF) {
            return TIFF;
        } else if (isZipArchive(type)) {
            return detectStreaming(tis, metadata, false);
        } else if (!type.equals(MediaType.OCTET_STREAM)) {
            return type;
        } else {
            return detectCompressorFormat(prefix, length);
        }
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

    private MediaType detect(ZipArchiveEntry zae, ZipArchiveInputStream zis,
                             StreamingDetectContext detectContext) throws IOException {
        for (ZipContainerDetector d : staticZipDetectors) {
            MediaType mt = d.streamingDetectUpdate(zae, zis, detectContext);
            if (mt != null) {
                return mt;
            }
        }
        return null;
    }

    private MediaType finalDetect(StreamingDetectContext detectContext) {
        for (ZipContainerDetector d : staticZipDetectors) {
            MediaType mt = d.streamingDetectFinal(detectContext);
            if (mt != null) {
                return mt;
            }
        }
        return MediaType.APPLICATION_ZIP;
    }
}
