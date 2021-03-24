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

import org.apache.commons.compress.archivers.ArchiveStreamFactory;

import org.apache.tika.mime.MediaType;

public class PackageConstants {


    public static final MediaType ZIP = MediaType.APPLICATION_ZIP;
    public static final MediaType JAR = MediaType.application("java-archive");
    public static final MediaType AR = MediaType.application("x-archive");
    public static final MediaType ARJ = MediaType.application("x-arj");
    public static final MediaType CPIO = MediaType.application("x-cpio");
    public static final MediaType DUMP = MediaType.application("x-tika-unix-dump");
    public static final MediaType TAR = MediaType.application("x-tar");
    public static final MediaType SEVENZ = MediaType.application("x-7z-compressed");

    public static final MediaType TIKA_OOXML = MediaType.application("x-tika-ooxml");
    public static final MediaType GTAR = MediaType.application("x-gtar");
    public static final MediaType KMZ = MediaType.application("vnd.google-earth.kmz");

    public static MediaType getMediaType(String name) {
        if (ArchiveStreamFactory.JAR.equals(name)) {
            return JAR;
        } else if (ArchiveStreamFactory.ZIP.equals(name)) {
            return ZIP;
        } else if (ArchiveStreamFactory.AR.equals(name)) {
            return AR;
        } else if (ArchiveStreamFactory.ARJ.equals(name)) {
            return ARJ;
        } else if (ArchiveStreamFactory.CPIO.equals(name)) {
            return CPIO;
        } else if (ArchiveStreamFactory.DUMP.equals(name)) {
            return DUMP;
        } else if (ArchiveStreamFactory.TAR.equals(name)) {
            return TAR;
        } else if (ArchiveStreamFactory.SEVEN_Z.equals(name)) {
            return SEVENZ;
        } else {
            return MediaType.OCTET_STREAM;
        }
    }
}
