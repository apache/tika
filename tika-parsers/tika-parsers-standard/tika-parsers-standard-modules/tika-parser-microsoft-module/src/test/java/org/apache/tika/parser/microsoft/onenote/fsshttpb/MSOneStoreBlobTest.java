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
package org.apache.tika.parser.microsoft.onenote.fsshttpb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.onenote.OneNoteTreeWalkerOptions;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElement;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.FileDataObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectDataBLOB;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectDataBLOBDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupObjectBLOBDataDeclaration;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupObjectData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupObjectDataBLOBReference;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupObjectDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObjectGroup;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StreamObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StreamObjectParseErrorException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Tests for object data BLOBs (embedded images and files) in the MS-FSSHTTPB packaged
 * revision store.
 */
public class MSOneStoreBlobTest {

    private static final byte[] BLOB_BYTES =
            "these are the bytes of an embedded image".getBytes(StandardCharsets.UTF_8);

    /**
     * An object data BLOB data element must survive a serialization round trip. Before
     * ObjectDataBLOBDataElementData existed, deserializing a data element of this type threw,
     * so any OneNote file with an embedded image or file lost its structure entirely.
     */
    @Test
    public void testObjectDataBLOBDataElementRoundTrip() throws Exception {
        DataElement reparsed = roundTripBlobElement();
        assertEquals(DataElementType.ObjectDataBLOBDataElementData, reparsed.dataElementType);
        assertArrayEquals(BLOB_BYTES,
                ((ObjectDataBLOBDataElementData) reparsed.data).objectDataBLOB.getData());
    }

    /**
     * A revision store object whose BLOB reference resolves to an object data BLOB element
     * must expose the BLOB bytes and hand them to the embedded document extractor during the
     * tree walk.
     */
    @Test
    public void testBlobIsEmittedAsEmbeddedDocument() throws Exception {
        DataElement blobElement = roundTripBlobElement();

        ObjectGroupObjectBLOBDataDeclaration declaration =
                new ObjectGroupObjectBLOBDataDeclaration();
        declaration.objectExGUID = new ExGuid(1, UUID.randomUUID());
        declaration.objectPartitionID.setDecodedValue(2);
        ObjectGroupObjectDataBLOBReference blobReference =
                new ObjectGroupObjectDataBLOBReference();
        blobReference.blobExtendedGUID = blobElement.dataElementExGuid;

        ObjectGroupDataElementData groupData = new ObjectGroupDataElementData();
        groupData.objectGroupDeclarations.objectGroupObjectBLOBDataDeclarationList
                .add(declaration);
        groupData.objectGroupData.objectGroupObjectDataBLOBReferenceList.add(blobReference);

        RevisionStoreObjectGroup objectGroup =
                RevisionStoreObjectGroup.createInstance(new ExGuid(2, UUID.randomUUID()),
                        groupData, false,
                        Collections.singletonMap(blobElement.dataElementExGuid, blobElement));
        assertEquals(1, objectGroup.objects.size());
        assertArrayEquals(BLOB_BYTES, objectGroup.objects.get(0).fileDataObject.getData());

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.OtherFileNodeList.add(objectGroup);

        List<byte[]> embedded = new ArrayList<>();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                      Metadata metadata, ParseContext context,
                                      boolean outputHtml) throws IOException {
                embedded.add(stream.readAllBytes());
            }
        });

        Metadata metadata = new Metadata();
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(new ToTextContentHandler(new StringWriter()), metadata,
                        context);
        xhtml.startDocument();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata, xhtml, context);
        xhtml.endDocument();

        assertEquals(1, embedded.size());
        assertArrayEquals(BLOB_BYTES, embedded.get(0));
    }

    @Test
    public void testMissingBlobDataAndMalformedLengthAreHandled() throws Exception {
        FileDataObject fileData = new FileDataObject();
        assertNull(fileData.getData());

        DataElement wrongType = new DataElement();
        fileData.objectDataBLOBDataElement = wrongType;
        assertNull(fileData.getData());

        ObjectDataBLOBDataElementData emptyBlobData = new ObjectDataBLOBDataElementData();
        emptyBlobData.objectDataBLOB = null;
        wrongType.data = emptyBlobData;
        assertNull(fileData.getData());

        ObjectDataBLOB blob = new ObjectDataBLOB();
        blob.data = null;
        assertNull(blob.getData());
        blob.data = new BinaryItem();
        blob.data.content = null;
        assertNull(blob.getData());
        blob.data = new BinaryItem();
        blob.data.content.add((byte) 1);
        // Exercise serialization of a non-null BLOB; the round-trip assertion is above.
        blob.serializeToByteList();

        ObjectDataBLOB validBlob = new ObjectDataBLOB();
        validBlob.data.content.add((byte) 1);
        byte[] itemBytes = ByteUtil.toByteArray(validBlob.data.serializeToByteList());
        AtomicInteger index = new AtomicInteger(0);
        java.lang.reflect.Method deserialize = ObjectDataBLOB.class.getDeclaredMethod(
                "deserializeItemsFromByteArray", byte[].class, AtomicInteger.class, int.class);
        deserialize.setAccessible(true);
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> deserialize.invoke(new ObjectDataBLOB(), itemBytes, index,
                        itemBytes.length + 1));
        assertTrue(exception.getCause() instanceof StreamObjectParseErrorException);
    }

    @Test
    public void testEmptyAndFailedEmbeddedDataAreIgnored() throws Exception {
        MSOneStorePackage emptyPackage = packageWithFileData(new ObjectDataBLOBDataElementData());
        walkWithExtractor(emptyPackage, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                throw new AssertionError("empty data must not reach the extractor");
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                      Metadata metadata, ParseContext context,
                                      boolean outputHtml) {
            }
        });

        DataElement blobElement = roundTripBlobElement();
        MSOneStorePackage failedPackage = packageWithFileData(
                (ObjectDataBLOBDataElementData) blobElement.data);
        walkWithExtractor(failedPackage, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                      Metadata metadata, ParseContext context,
                                      boolean outputHtml) throws IOException {
                throw new IOException("synthetic embedded parse failure");
            }
        });
    }

    @Test
    public void testEncryptedObjectGroupAndMissingBlobReference() throws Exception {
        ObjectGroupDataElementData encryptedData = new ObjectGroupDataElementData();
        ObjectGroupObjectDeclare declaration = new ObjectGroupObjectDeclare();
        declaration.objectPartitionID.setDecodedValue(1);
        ObjectGroupObjectData objectData = new ObjectGroupObjectData();
        objectData.data.content.add((byte) 42);
        encryptedData.objectGroupDeclarations.objectDeclarationList.add(declaration);
        encryptedData.objectGroupData.objectGroupObjectDataList.add(objectData);

        RevisionStoreObjectGroup encrypted = RevisionStoreObjectGroup.createInstance(
                new ExGuid(3, UUID.randomUUID()), encryptedData, true);
        assertEquals(1, encrypted.encryptionObjects.size());

        ObjectGroupDataElementData missingBlobData = new ObjectGroupDataElementData();
        ObjectGroupObjectBLOBDataDeclaration blobDeclaration =
                new ObjectGroupObjectBLOBDataDeclaration();
        blobDeclaration.objectExGUID = new ExGuid(4, UUID.randomUUID());
        blobDeclaration.objectPartitionID.setDecodedValue(2);
        ObjectGroupObjectDataBLOBReference reference =
                new ObjectGroupObjectDataBLOBReference();
        reference.blobExtendedGUID = new ExGuid(5, UUID.randomUUID());
        missingBlobData.objectGroupDeclarations.objectGroupObjectBLOBDataDeclarationList
                .add(blobDeclaration);
        missingBlobData.objectGroupData.objectGroupObjectDataBLOBReferenceList.add(reference);
        RevisionStoreObjectGroup missing = RevisionStoreObjectGroup.createInstance(
                new ExGuid(6, UUID.randomUUID()), missingBlobData, false);
        assertEquals(1, missing.objects.size());
        assertNull(missing.objects.get(0).fileDataObject.getData());
    }

    private MSOneStorePackage packageWithFileData(ObjectDataBLOBDataElementData blobData) {
        DataElement element = new DataElement();
        element.data = blobData;
        FileDataObject fileData = new FileDataObject();
        fileData.objectDataBLOBDataElement = element;
        RevisionStoreObject object = new RevisionStoreObject();
        object.objectID = new ExGuid(7, UUID.randomUUID());
        object.fileDataObject = fileData;
        RevisionStoreObjectGroup group = new RevisionStoreObjectGroup(
                new ExGuid(8, UUID.randomUUID()));
        group.objects.add(object);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.OtherFileNodeList.add(group);
        return pkg;
    }

    private void walkWithExtractor(MSOneStorePackage pkg, EmbeddedDocumentExtractor extractor)
            throws Exception {
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, extractor);
        Metadata metadata = new Metadata();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new ToTextContentHandler(new StringWriter()), metadata, context);
        xhtml.startDocument();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata, xhtml, context);
        xhtml.endDocument();
    }

    private DataElement roundTripBlobElement() throws Exception {
        ObjectDataBLOBDataElementData blobData = new ObjectDataBLOBDataElementData();
        for (byte b : BLOB_BYTES) {
            blobData.objectDataBLOB.data.content.add(b);
        }
        DataElement blobElement =
                new DataElement(DataElementType.ObjectDataBLOBDataElementData, blobData);
        byte[] serialized = ByteUtil.toByteArray(blobElement.serializeToByteList());
        return StreamObject.getCurrent(serialized, new AtomicInteger(0), DataElement.class);
    }
}
