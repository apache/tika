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
package org.apache.tika.parser.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

// sets the JVM-wide default locale, so it must not overlap other test classes
@Isolated
public class ImageMetadataExtractorLocaleTest {

    @Test
    public void testIccCurveFormattingIgnoresDefaultLocale() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("0.0, 0.4999924, 1.0", ImageMetadataExtractor.CopyUnknownFieldsHandler
                    .formatIccCurve(ImageMetadataExtractorTest.iccCurve(3)));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    // the Japanese imperial calendar makes a default-locale SimpleDateFormat read 2009 as Reiwa 2009
    @Test
    public void testExifDatesIgnoreDefaultCalendar() throws Exception {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ja-JP-u-ca-japanese"));
            Metadata metadata = parse("/test-documents/testJPEG_EXIF.jpg");
            assertEquals("2009-08-11T09:09:45", metadata.get(TikaCoreProperties.CREATED));
            assertEquals("2009-10-02T23:02:49", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("2009-08-11T09:09:45", metadata.get(TIFF.ORIGINAL_DATE));

            metadata = parse("/test-documents/testJPEG_GEO_2.jpg");
            assertEquals("2012-02-20T16:44:22Z", metadata.get(Geographic.TIMESTAMP));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static Metadata parse(String resource) throws Exception {
        Metadata metadata = new Metadata();
        try (InputStream is = ImageMetadataExtractorLocaleTest.class.getResourceAsStream(resource);
                TikaInputStream tis = TikaInputStream.get(is)) {
            new JpegParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }
}
