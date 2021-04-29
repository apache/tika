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

package org.apache.tika.parser.pkg;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.zip.PackageConstants;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class PackageParserTest {

    @Test
    public void testCoverage() throws Exception {
        //test that the package parser covers all inputstreams handled
        //by ArchiveStreamFactory.  When we update commons-compress, and they add
        //a new stream type, we want to make sure that we're handling it.
        ArchiveStreamFactory archiveStreamFactory =
                new ArchiveStreamFactory(StandardCharsets.UTF_8.name());
        PackageParser packageParser = new PackageParser();
        ParseContext parseContext = new ParseContext();
        for (String name : archiveStreamFactory.getInputStreamArchiveNames()) {
            MediaType mt = PackageConstants.getMediaType(name);
            //use this instead of assertNotEquals so that we report the
            //name of the missing stream
            if (mt.equals(MediaType.OCTET_STREAM)) {
                fail("getting octet-stream for: " + name);
            }

            if (!packageParser.getSupportedTypes(parseContext).contains(mt)) {
                fail("PackageParser should support: " + mt.toString());
            }
        }
    }

    @Test
    public void testSpecializations() throws Exception {
        //Test that our manually constructed list of children of zip and tar
        //in PackageParser is current with TikaConfig's defaultConfig.
        TikaConfig config = TikaConfig.getDefaultConfig();
        MediaTypeRegistry mediaTypeRegistry = config.getMimeRepository().getMediaTypeRegistry();
        Set<MediaType> currentSpecializations = new HashSet<>();
        MediaType tar = MediaType.parse("application/x-tar");
        for (MediaType type : mediaTypeRegistry.getTypes()) {
            if (mediaTypeRegistry.isSpecializationOf(type, MediaType.APPLICATION_ZIP) ||
                    mediaTypeRegistry.isSpecializationOf(type, tar)) {
                currentSpecializations.add(type);
//                System.out.println("\""+type.toString()+"\",");
            }
        }
        for (MediaType mediaType : currentSpecializations) {
            assertTrue("missing: " + mediaType,
                    PackageParser.PACKAGE_SPECIALIZATIONS.contains(mediaType));
        }
        assertEquals(currentSpecializations.size(), PackageParser.PACKAGE_SPECIALIZATIONS.size());
    }
    
    @Test
    public void hanldeNonUnicodeEntryName() throws IOException {

        BodyContentHandler handler = new BodyContentHandler();
        EncodingDetector dummyDetector = new EncodingDetector() {
            @Override
            public Charset detect(InputStream inputStream, Metadata metadata) throws IOException {
                return Charset.forName("GB18030");
            }
        };
        PackageParser parser = new PackageParser();
        parser.setEncodingDetector(dummyDetector);
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        Metadata meta = new Metadata();
        try {
            parser.parse(this.getClass().getResourceAsStream("gbk.zip"), handler, meta, context);
            String res = handler.toString();
            assertThat(res,
                    CoreMatchers.containsString("审计压缩包文件检索测试/集团邮件审计系统2021年自动巡检需求文档_V4.0.doc"));
        } catch (SAXException | TikaException ignored) {
        }
    }
}
