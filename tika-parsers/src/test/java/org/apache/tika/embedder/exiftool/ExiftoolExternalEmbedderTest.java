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
package org.apache.tika.embedder.exiftool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import org.apache.tika.embedder.Embedder;
import org.apache.tika.embedder.ExternalEmbedderTest;
import org.apache.tika.embedder.exiftool.ExiftoolExternalEmbedder;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.exiftool.ExiftoolImageParser;
import org.apache.tika.parser.exiftool.ExiftoolTikaIptcMapper;

/**
 * Unit test for the ExiftoolExternalEmbedder
 */
public class ExiftoolExternalEmbedderTest extends ExternalEmbedderTest {

    private static final String TEST_IMAGE_PATH = "/test-documents/testJPEG_IPTC_EXT.jpg";

    @Override
    protected org.apache.tika.metadata.Metadata getMetadataToEmbed(Date timestamp) {
        Metadata metadataToEmbed = new Metadata();

        metadataToEmbed.add(IPTC.COPYRIGHT_NOTICE.getName(),
                getExpectedMetadataValueString(IPTC.COPYRIGHT_NOTICE.getName(), timestamp));
        metadataToEmbed.add(IPTC.DESCRIPTION.getName(),
                getExpectedMetadataValueString(IPTC.DESCRIPTION.getName(), timestamp));
        metadataToEmbed.add(IPTC.CREDIT_LINE.getName(),
                getExpectedMetadataValueString(IPTC.CREDIT_LINE.getName(), timestamp));
        metadataToEmbed.add(IPTC.KEYWORDS.getName(),
                this.getClass().getSimpleName() + "_keyword1");
        metadataToEmbed.add(IPTC.KEYWORDS.getName(),
                this.getClass().getSimpleName() + "_keyword2");
        metadataToEmbed.add(IPTC.CONTACT_INFO_EMAIL.getName(),
                getExpectedMetadataValueString(IPTC.CONTACT_INFO_EMAIL.getName(), timestamp));
        metadataToEmbed.add(IPTC.ARTWORK_OR_OBJECT_DETAIL_TITLE.getName(),
                getExpectedMetadataValueString(IPTC.ARTWORK_OR_OBJECT_DETAIL_TITLE.getName(), timestamp));

        return metadataToEmbed;
    }

    @Override
    protected Embedder getEmbedder() {
        return new ExiftoolExternalEmbedder(new ExiftoolTikaIptcMapper());
    }

    @Override
    protected InputStream getSourceStandardInputStream() {
        return this.getClass().getResourceAsStream(TEST_IMAGE_PATH);
    }
    
    @Override
    protected File getSourceInputFile() throws FileNotFoundException {
        URL origUrl = this.getClass().getResource(TEST_IMAGE_PATH);
        if (origUrl == null) {
            throw new FileNotFoundException("could not load " + TEST_IMAGE_PATH);
        }
        try {
            return new File(origUrl.toURI());
        } catch (URISyntaxException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    protected Parser getParser() {
        return new ExiftoolImageParser();
    }

    @Override
    protected boolean getIsMetadataExpectedInOutput() {
        return false;
    }

}
