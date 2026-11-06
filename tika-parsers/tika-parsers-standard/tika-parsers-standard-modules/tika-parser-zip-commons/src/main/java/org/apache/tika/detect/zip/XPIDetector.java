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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * Mozilla add-ons: a legacy install manifest ({@code install.rdf}) or a Mozilla signature
 * ({@code META-INF/mozilla.rsa}). An unsigned WebExtension has only {@code manifest.json},
 * which Chromium extensions share, so it is left alone.
 */
public class XPIDetector implements ZipContainerDetector {

    static final MediaType XPI = MediaType.application("x-xpinstall");

    private static final String INSTALL_RDF = "install.rdf";

    private static final String MOZILLA_RSA = "META-INF/mozilla.rsa";

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {
        if (zip.getEntry(INSTALL_RDF) != null || zip.getEntry(MOZILLA_RSA) != null) {
            return XPI;
        }
        return null;
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {
        String name = zae.getName();
        if (name.equals(INSTALL_RDF) || name.equals(MOZILLA_RSA)) {
            return XPI;
        }
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        return null;
    }
}
