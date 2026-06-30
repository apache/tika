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
package org.apache.tika.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.v1.ParseResponse;

/**
 * Field-presence sweeps for every typed {@link ParseResponse} format.
 */
class ParseResponseMapperAllFormatsFieldCoverageTest extends ParseFixtureSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ParseResponseMapperAllFormatsFieldCoverageTest.class);

    @Test
    void htmlFieldCoverage() throws Exception {
        sweep("HTML", ClasspathTestDocuments.listByExtensions(".html", ".htm", ".xhtml"),
                ParseResponse::hasHtml, ParseResponse::getHtml);
    }

    @Test
    void imageFieldCoverage() throws Exception {
        sweep("Image", ClasspathTestDocuments.listByExtensions(".jpg", ".jpeg", ".png", ".gif", ".tif", ".tiff"),
                ParseResponse::hasImage, ParseResponse::getImage);
    }

    @Test
    void emailFieldCoverage() throws Exception {
        List<String> files = ClasspathTestDocuments.listByExtension(".eml");
        files.add("testRFC822");
        sweep("Email", files, ParseResponse::hasEmail, ParseResponse::getEmail);
    }

    @Test
    void rtfFieldCoverage() throws Exception {
        sweep("RTF", ClasspathTestDocuments.listByExtension(".rtf"),
                ParseResponse::hasRtf, ParseResponse::getRtf);
    }

    @Test
    void epubFieldCoverage() throws Exception {
        sweep("EPUB", ClasspathTestDocuments.listByExtension(".epub"),
                ParseResponse::hasEpub, ParseResponse::getEpub);
    }

    @Test
    void warcFieldCoverage() throws Exception {
        sweep("WARC", ClasspathTestDocuments.listByExtensions(".warc", ".arc", ".warc.gz"),
                ParseResponse::hasWarc, ParseResponse::getWarc);
    }

    @Test
    void fontFieldCoverage() throws Exception {
        sweep("Font", ClasspathTestDocuments.listByExtensions(".ttf", ".afm", ".otf", ".woff", ".woff2"),
                ParseResponse::hasFont, ParseResponse::getFont);
    }

    @Test
    void mediaFieldCoverage() throws Exception {
        sweep("Media", ClasspathTestDocuments.listByExtensions(".mp3", ".mp4", ".wav", ".ogg", ".flac"),
                ParseResponse::hasMedia, ParseResponse::getMedia);
    }

    @Test
    void databaseFieldCoverage() throws Exception {
        sweep("Database", ClasspathTestDocuments.listByExtensions(".dbf", ".mdb", ".accdb", ".sqlite"),
                ParseResponse::hasDatabase, ParseResponse::getDatabase);
    }

    @Test
    void climateFieldCoverage() throws Exception {
        sweep("Climate", ClasspathTestDocuments.listByExtensions(".nc", ".netcdf"),
                ParseResponse::hasClimateForecast, ParseResponse::getClimateForecast);
    }

    private void sweep(String label, List<String> files,
                       java.util.function.Predicate<ParseResponse> isTyped,
                       java.util.function.Function<ParseResponse, com.google.protobuf.Message> message)
            throws Exception {
        Assumptions.assumeFalse(files.isEmpty(), "No " + label + " fixtures on classpath");
        FormatFieldCoverage.SweepResult result = FormatFieldCoverage.sweep(this, files, isTyped, message);
        LOG.info("{} coverage: processed={}, typed={}", label, result.processed(), result.typed());
        FormatFieldCoverage.logTopFields(LOG, result.fieldCounts(), 15);
        assertTrue(result.processed() > 0, label + ": should parse at least one fixture");
        assertTrue(result.typed() >= 1, label + ": should type at least one fixture");
    }

}
