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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.zip.PackageConstants;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;

public class PackageParserTest extends TikaTest {

    @Test
    public void testCoverage() throws Exception {
        // Test that the archive parsers collectively cover all input streams handled
        // by ArchiveStreamFactory. When we update commons-compress, and they add
        // a new stream type, we want to make sure that we're handling it.
        ArchiveStreamFactory archiveStreamFactory =
                new ArchiveStreamFactory(StandardCharsets.UTF_8.name());

        PackageParser packageParser = new PackageParser();
        ZipParser zipParser = new ZipParser();
        SevenZParser sevenZParser = new SevenZParser();
        ParseContext parseContext = new ParseContext();

        // Combine supported types from all archive parsers
        Set<MediaType> allSupportedTypes = new HashSet<>();
        allSupportedTypes.addAll(packageParser.getSupportedTypes(parseContext));
        allSupportedTypes.addAll(zipParser.getSupportedTypes(parseContext));
        allSupportedTypes.addAll(sevenZParser.getSupportedTypes(parseContext));

        for (String name : archiveStreamFactory.getInputStreamArchiveNames()) {
            MediaType mt = PackageConstants.getMediaType(name);
            // Use this instead of assertNotEquals so that we report the
            // name of the missing stream
            if (mt.equals(MediaType.OCTET_STREAM)) {
                fail("getting octet-stream for: " + name);
            }

            if (!allSupportedTypes.contains(mt)) {
                fail("Archive parsers should support: " + mt.toString());
            }
        }
    }

    @Test
    public void testZipSpecializations() throws Exception {
        // Test that our manually constructed list of ZIP specializations
        // in ZipParser is current with TikaLoader's media type registry.
        MediaTypeRegistry mediaTypeRegistry = TikaLoader.getMediaTypeRegistry();
        Set<MediaType> currentZipSpecializations = new HashSet<>();
        for (MediaType type : mediaTypeRegistry.getTypes()) {
            if (mediaTypeRegistry.isSpecializationOf(type, MediaType.APPLICATION_ZIP)) {
                currentZipSpecializations.add(type);
            }
        }
        for (MediaType mediaType : currentZipSpecializations) {
            assertTrue(ZipParser.ZIP_SPECIALIZATIONS.contains(mediaType),
                    "missing: " + mediaType);
        }
        assertEquals(currentZipSpecializations.size(), ZipParser.ZIP_SPECIALIZATIONS.size());
    }
}
