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
package org.apache.tika.zip.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ZipEntryPathsTest {

    @Test
    public void testRelativeToTheEntry() {
        assertEquals("OEBPS/Text/ch1.xhtml", ZipEntryPaths.resolve("OEBPS/content.opf",
                "Text/ch1.xhtml"));
        assertEquals("OEBPS/Text/ch1.xhtml", ZipEntryPaths.resolve("OEBPS/content.opf",
                "./Text/ch1.xhtml"));
        assertEquals("Images/cover.jpg", ZipEntryPaths.resolve("OEBPS/content.opf",
                "../Images/cover.jpg"));
        assertEquals("ch1.xhtml", ZipEntryPaths.resolve("content.opf", "ch1.xhtml"));
        assertEquals("Metadata/thumbnail.png", ZipEntryPaths.resolve("", "Metadata/thumbnail.png"));
    }

    @Test
    public void testAbsolutePath() {
        assertEquals("Metadata/thumbnail.png", ZipEntryPaths.resolve("",
                "/Metadata/thumbnail.png"));
        assertEquals("Thumbnails/box.png", ZipEntryPaths.resolve("3D/3dmodel.model",
                "/Thumbnails/box.png"));
    }

    @Test
    public void testDecoding() {
        assertEquals("OEBPS/Text/c++ b.xhtml", ZipEntryPaths.resolve("OEBPS/content.opf",
                "Text/c++%20b.xhtml"));
        assertEquals("OEBPS/Text/Kapitel ä.xhtml", ZipEntryPaths.resolve("OEBPS/content.opf",
                "Text/Kapitel%20%C3%A4.xhtml"));
        assertEquals("OEBPS/Text/ch1.xhtml", ZipEntryPaths.resolve("OEBPS/content.opf",
                "Text/ch1.xhtml#section2"));
    }

    @Test
    public void testNotAValidUri() {
        assertEquals("OEBPS/Text/my chapter.xhtml", ZipEntryPaths.resolve("OEBPS/content.opf",
                "Text/my chapter.xhtml"));
        assertEquals("OEBPS/Text/100%.xhtml", ZipEntryPaths.resolve("OEBPS/content.opf",
                "Text/100%.xhtml"));
        assertEquals("My Book/Text/ch1.xhtml", ZipEntryPaths.resolve("My Book/content.opf",
                "Text/ch1.xhtml"));
    }

    /** The entry the reference sits in is a name, not a URI: its %, # and ? are literal. */
    @Test
    public void testLiteralBase() {
        assertEquals("My%20Book/Text/ch1.xhtml", ZipEntryPaths.resolve("My%20Book/content.opf",
                "Text/ch1.xhtml"));
        assertEquals("OEBPS#1/Text/ch1.xhtml", ZipEntryPaths.resolve("OEBPS#1/content.opf",
                "Text/ch1.xhtml"));
        assertEquals("a?b/ch1.xhtml", ZipEntryPaths.resolve("a?b/content.opf", "ch1.xhtml"));
    }

    /** A reference without a path points at no other entry. */
    @Test
    public void testNoPath() {
        assertNull(ZipEntryPaths.resolve("OEBPS/content.opf", ""));
        assertNull(ZipEntryPaths.resolve("OEBPS/content.opf", "#frag"));
        assertNull(ZipEntryPaths.resolve("OEBPS/content.opf", "?query"));
    }

    @Test
    public void testOutsideThePackage() {
        assertNull(ZipEntryPaths.resolve("OEBPS/content.opf", "../../etc/passwd"));
        assertNull(ZipEntryPaths.resolve("", "../x"));
        assertNull(ZipEntryPaths.resolve("", "http://example.com/x.png"));
        assertNull(ZipEntryPaths.resolve("", "//example.com/x.png"));
        assertNull(ZipEntryPaths.resolve("", "file:///etc/passwd"));
        assertNull(ZipEntryPaths.resolve("", null));
    }
}
