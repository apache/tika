package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RevisionManifestDataElementData extends DataElementData {
    /**
     * Initializes a new instance of the RevisionManifestDataElementData class.
     */
    public RevisionManifestDataElementData() {
        this.RevisionManifest = new RevisionManifest();
        this.RevisionManifestRootDeclareList = new ArrayList<>();
        this.RevisionManifestObjectGroupReferencesList = new ArrayList<>();
    }

    /**
     * Gets or sets a 16-bit stream object header that specifies a revision manifest.
     */
    public RevisionManifest RevisionManifest;

    /**
     * Gets or sets  a revision manifest root declare, each followed by root and object extended GUIDs.
     */
    public List<RevisionManifestRootDeclare> RevisionManifestRootDeclareList;

    /**
     * Gets or sets  a list of revision manifest object group references, each followed by object group extended GUIDs.
     */
    public List<RevisionManifestObjectGroupReferences> RevisionManifestObjectGroupReferencesList;

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  A Byte list
     * @param startIndex Start position
     * @return The length of the element
     */
    @Override
    public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.RevisionManifest = StreamObject.GetCurrent(byteArray, index, RevisionManifest.class);

        this.RevisionManifestRootDeclareList = new ArrayList<>();
        this.RevisionManifestObjectGroupReferencesList = new ArrayList<>();
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        int headerLength = 0;
        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            if (header.get().type == StreamObjectTypeHeaderStart.RevisionManifestRootDeclare) {
                index.addAndGet(headerLength);
                this.RevisionManifestRootDeclareList.add(
                        (RevisionManifestRootDeclare) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else if (header.get().type == StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences) {
                index.addAndGet(headerLength);
                this.RevisionManifestObjectGroupReferencesList.add(
                        (RevisionManifestObjectGroupReferences) StreamObject.ParseStreamObject(header.get(), byteArray,
                                index));
            } else {
                throw new DataElementParseErrorException(index.get(),
                        "Failed to parse RevisionManifestDataElement, expect the inner object type RevisionManifestRootDeclare or RevisionManifestObjectGroupReferences, but actual type value is " +
                                header.get().type, null);
            }
        }

        return index.get() - startIndex;
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
    @Override
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<>();
        byteList.addAll(this.RevisionManifest.SerializeToByteList());

        if (this.RevisionManifestRootDeclareList != null) {
            for (RevisionManifestRootDeclare revisionManifestRootDeclare : this.RevisionManifestRootDeclareList) {
                byteList.addAll(revisionManifestRootDeclare.SerializeToByteList());
            }
        }

        if (this.RevisionManifestObjectGroupReferencesList != null) {
            for (RevisionManifestObjectGroupReferences revisionManifestObjectGroupReferences : this.RevisionManifestObjectGroupReferencesList) {
                byteList.addAll(revisionManifestObjectGroupReferences.SerializeToByteList());
            }
        }

        return byteList;
    }
}