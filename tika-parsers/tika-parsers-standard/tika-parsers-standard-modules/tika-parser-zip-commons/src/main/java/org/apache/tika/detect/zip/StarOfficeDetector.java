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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.StoppingEarlyException;
import org.apache.tika.utils.XMLReaderUtils;

public class StarOfficeDetector implements ZipContainerDetector {

    static final Map<String, MediaType> STAR_OFFICE_X = new HashMap<>();
    static final MediaType BAU = MediaType.application("vnd.openofficeorg.autotext");
    private static final int MAX_MANIFEST = 20 * 1024 * 1024;

    static {
        STAR_OFFICE_X
                .put("application/vnd.sun.xml.writer", MediaType.application("vnd.sun.xml.writer"));
        STAR_OFFICE_X
                .put("application/vnd.sun.xml.calc", MediaType.application("vnd.sun.xml.calc"));
        STAR_OFFICE_X
                .put("application/vnd.sun.xml.draw", MediaType.application("vnd.sun.xml.draw"));
        STAR_OFFICE_X.put("application/vnd.sun.xml.impress",
                MediaType.application("vnd.sun.xml.impress"));
        STAR_OFFICE_X.put("application/vnd.sun.star.configuration-data",
                MediaType.application("vnd.openofficeorg.extension"));
    }

    //parse the META-INF/content.xml file
    static MediaType detectStarOfficeX(InputStream is) {
        StarOfficeXHandler handler = new StarOfficeXHandler();
        try {
            XMLReaderUtils.parseSAX(is, handler, new ParseContext());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //swallow
        }
        return handler.mediaType;
    }

    @Override
    public MediaType detect(ZipFile zip, TikaInputStream tis) throws IOException {

        ZipArchiveEntry zae = zip.getEntry("META-INF/manifest.xml");

        if (zae == null) {
            return null;
        }
        return detectStarOfficeX(zip.getInputStream(zae));
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext)
            throws IOException {
        String name = zae.getName();
        if (!"META-INF/manifest.xml".equals(name)) {
            return null;
        }
        //for an unknown reason, passing in the zipArchiveInputStream
        //"as is" can cause the iteration of the entries to stop early
        //without exception or warning.  So, copy the full stream, then
        //process.  TIKA-3061
        UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
        BoundedInputStream bis = new BoundedInputStream(MAX_MANIFEST, zis);
        IOUtils.copy(bis, bos);

        return detectStarOfficeX(bos.toInputStream());

    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        return null;
    }

    private static class StarOfficeXHandler extends DefaultHandler {

        private MediaType mediaType = null;

        @Override
        public void startElement(String uri, String localName, String name, Attributes attrs)
                throws SAXException {
            if (!"file-entry".equals(localName)) {
                return;
            }
            String mediaTypeString = null;
            String fullPath = null;
            for (int i = 0; i < attrs.getLength(); i++) {
                String attrName = attrs.getLocalName(i);
                if (attrName.equals("media-type")) {
                    mediaTypeString = attrs.getValue(i);
                    if (STAR_OFFICE_X.containsKey(mediaTypeString)) {
                        mediaType = STAR_OFFICE_X.get(mediaTypeString);
                        throw StoppingEarlyException.INSTANCE;
                    }
                } else if (attrName.equals("full-path")) {
                    fullPath = attrs.getValue(i);
                }
            }
            if ("".equals(mediaTypeString) && "/".equals(fullPath)) {
                mediaType = BAU;
                throw StoppingEarlyException.INSTANCE;
            }
        }
    }

}
