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
package org.apache.tika.detect.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.zip.DefaultZipContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * OLE2 and OPC formats recognized by their stream names or package relationships.
 */
public class ContainerFormatDetectionTest {

    // a trailing slash makes a storage
    private static String ole2(String... names) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            for (String name : names) {
                if (name.endsWith("/")) {
                    fs.getRoot().createDirectory(name.substring(0, name.length() - 1));
                } else {
                    fs.createDocument(new ByteArrayInputStream(new byte[]{1, 2, 3}), name);
                }
            }
            fs.writeFilesystem(bos);
        }
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            return new POIFSContainerDetector().detect(tis, new Metadata(), new ParseContext())
                    .toString();
        }
    }

    private static String opc(String relType, String... extra) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "[Content_Types].xml", "<?xml version=\"1.0\"?><Types "
                    + "xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" "
                    + "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"nuspec\" ContentType=\"application/octet\"/></Types>");
            put(zos, "_rels/.rels", "<?xml version=\"1.0\"?><Relationships "
                    + "xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Type=\"" + relType + "\" Target=\"/a.nuspec\" Id=\"R1\"/>"
                    + "</Relationships>");
            put(zos, "a.nuspec", "<package/>");
            for (String name : extra) {
                put(zos, name, "x");
            }
        }
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            return new DefaultZipContainerDetector().detect(tis, new Metadata(), new ParseContext())
                    .toString();
        }
    }

    private static void put(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    @Test
    public void testOle2Formats() throws Exception {
        assertEquals("application/x-autodesk-revit",
                ole2("BasicFileInfo", "Contents", "PartAtom", "TransmissionData", "Formats/",
                        "Global/", "Partitions/"));
        assertEquals("application/x-3ds-max",
                ole2("ClassData", "ClassDirectory3", "Config", "DllDirectory", "Scene",
                        "VideoPostQueue"));
        assertEquals("application/x-ichitaro",
                ole2("\u0005JSRV_SummaryInformation", "DocumentText", "DocumentEditStyles"));
        assertEquals("application/x-ms-thumbs-db", ole2("Catalog", "1", "01", "11"));
        assertEquals("application/sldworks",
                ole2("ISolidWorksInformation", "Header2", "Preview", "Contents/"));
    }

    @Test
    public void testOle2LookalikesStayGeneric() throws Exception {
        assertEquals("application/x-tika-msoffice", ole2("Catalog", "1", "Data"));
        assertEquals("application/x-tika-msoffice", ole2("Catalog"));
        assertEquals("application/x-tika-msoffice", ole2("BasicFileInfo", "Contents"));
        assertEquals("application/x-tika-msoffice", ole2("Scene", "ClassDirectory3"));
    }

    @Test
    public void testOpcPackageRelationships() throws Exception {
        assertEquals("application/x-nupkg",
                opc("http://schemas.microsoft.com/packaging/2010/07/manifest"));
        assertEquals("application/x-fictionbook3",
                opc("http://www.fictionbook.org/FictionBook3/relationships/Book"));
        assertEquals("application/x-ms-hlk-package",
                opc("http://microsoft.com/schemas/windows/kits/hardware/2010/coredata"));
        // the zip layer leaves an unknown package generic
        assertEquals("application/zip", opc("http://example.com/unknown"));
    }
}
