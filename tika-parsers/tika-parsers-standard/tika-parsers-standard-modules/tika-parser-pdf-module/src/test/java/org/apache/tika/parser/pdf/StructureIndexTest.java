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
package org.apache.tika.parser.pdf;

import static org.apache.tika.parser.pdf.TaggedPdfBuilder.reference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.junit.jupiter.api.Test;

public class StructureIndexTest {

    @Test
    public void testNoTree() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            assertEquals("no-structure-tree", StructureIndex.load(doc).reason());
        }
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            b.root.getCOSObject().removeItem(COSName.K);
            assertEquals("empty-structure-tree", StructureIndex.load(b.doc).reason());
        }
    }

    @Test
    public void testLeavesInheritThePage() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement sect = b.element("Sect", b.document, page);
            b.leaf("P", sect, null, 4);
            StructureIndex index = StructureIndex.load(b.doc);
            assertNull(index.reason());
            StructureIndex.Leaf leaf = index.leaf(page.getCOSObject(), 4);
            assertNotNull(leaf);
            assertEquals("P", leaf.node.type);
            assertEquals("Sect", leaf.node.parent.type);
            assertEquals("Document", leaf.node.parent.parent.type);
            assertNull(leaf.node.parent.parent.parent);
        }
    }

    @Test
    public void testSingleIntegerAndSingleDictKids() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement sect = b.element("Sect", b.document, page);
            PDStructureElement p = new PDStructureElement("P", sect);
            // /K as a bare dictionary and a bare integer, not arrays
            sect.getCOSObject().setItem(COSName.K, p.getCOSObject());
            p.getCOSObject().setItem(COSName.K, COSInteger.get(3));
            StructureIndex index = StructureIndex.load(b.doc);
            assertNull(index.reason());
            assertEquals("P", index.leaf(page.getCOSObject(), 3).node.type);
        }
    }

    @Test
    public void testMarkedContentReferenceWithStream() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            COSStream form = b.doc.getDocument().createCOSStream();
            PDStructureElement p = b.element("P", b.document, page);
            reference(p, form, null, 2);
            reference(p, null, page, 5);
            StructureIndex index = StructureIndex.load(b.doc);
            assertEquals("P", index.leaf(form, 2).node.type);
            assertEquals("P", index.leaf(page.getCOSObject(), 5).node.type);
            assertNull(index.leaf(page.getCOSObject(), 2));
        }
    }

    @Test
    public void testKidCycleTerminates() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement a = b.element("Div", b.document, page);
            PDStructureElement c = b.leaf("P", a, page, 0);
            COSArray kids = new COSArray();
            kids.add(COSInteger.get(0));
            kids.add(a.getCOSObject());
            c.getCOSObject().setItem(COSName.K, kids);
            StructureIndex index = StructureIndex.load(b.doc);
            assertNull(index.reason());
            assertEquals(3, index.nodeCount());
            assertEquals(1, index.leafCount());
        }
    }

    @Test
    public void testDepthCap() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement parent = b.document;
            for (int i = 0; i < StructureIndex.MAX_DEPTH + 2; i++) {
                parent = b.element("Div", parent, page);
            }
            parent.appendKid(0);
            assertEquals("structure-tree-depth", StructureIndex.load(b.doc).reason());
        }
    }

    @Test
    public void testRoleMapChainCycleAndCustomType() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.root.setRoleMap(Map.of("Foo", "Bar", "Bar", "P", "X", "Y", "Y", "X"));
            b.leaf("Foo", b.document, page, 0);
            b.leaf("X", b.document, page, 1);
            b.leaf("Widget", b.document, page, 2);
            StructureIndex index = StructureIndex.load(b.doc);
            assertEquals("P", index.leaf(page.getCOSObject(), 0).node.type);
            assertEquals("X", index.leaf(page.getCOSObject(), 1).node.type);
            assertEquals("Widget", index.leaf(page.getCOSObject(), 2).node.type);
        }
    }

    @Test
    public void testDuplicateLeafFirstWins() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            b.leaf("H1", b.document, page, 0);
            StructureIndex index = StructureIndex.load(b.doc);
            assertEquals("P", index.leaf(page.getCOSObject(), 0).node.type);
            assertEquals(1, index.leafCount());
            assertEquals(3, index.nodeCount());
        }
    }

    @Test
    public void testPageIdentitySurvivesSaveAndLoad() throws Exception {
        byte[] pdf;
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            pdf = b.bytes();
        }
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            StructureIndex index = StructureIndex.load(doc);
            assertNull(index.reason());
            assertNotNull(index.leaf(doc.getPage(0).getCOSObject(), 0));
        }
    }

    @Test
    public void testLinkUri() {
        COSDictionary annotation = new COSDictionary();
        assertNull(StructureIndex.linkUri(annotation));
        COSDictionary action = new COSDictionary();
        action.setItem(COSName.S, COSName.getPDFName("GoTo"));
        action.setString(COSName.URI, "http://example.com/");
        annotation.setItem(COSName.A, action);
        assertNull(StructureIndex.linkUri(annotation));
        action.setItem(COSName.S, COSName.URI);
        assertEquals("http://example.com/", StructureIndex.linkUri(annotation));
        assertNull(StructureIndex.linkUri(null));
    }
}
