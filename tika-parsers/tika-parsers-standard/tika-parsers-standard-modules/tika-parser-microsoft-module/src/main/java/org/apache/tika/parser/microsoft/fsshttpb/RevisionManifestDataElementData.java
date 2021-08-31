package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RevisionManifestDataElementData extends DataElementData {
    /// <summary>
    /// Initializes a new instance of the RevisionManifestDataElementData class.
    /// </summary>
    public RevisionManifestDataElementData() {
        this.RevisionManifest = new RevisionManifest();
        this.RevisionManifestRootDeclareList = new ArrayList<>();
        this.RevisionManifestObjectGroupReferencesList = new ArrayList<>();
    }

    /// <summary>
    /// Gets or sets a 16-bit stream object header that specifies a revision manifest.
    /// </summary>
    public RevisionManifest RevisionManifest;

    /// <summary>
    /// Gets or sets  a revision manifest root declare, each followed by root and object extended GUIDs.
    /// </summary>
    public List<RevisionManifestRootDeclare> RevisionManifestRootDeclareList;

    /// <summary>
    /// Gets or sets  a list of revision manifest object group references, each followed by object group extended GUIDs.
    /// </summary>
    public List<RevisionManifestObjectGroupReferences> RevisionManifestObjectGroupReferencesList;

    /// <summary>
    /// Used to return the length of this element.
    /// </summary>
    /// <param name="byteArray">A Byte list</param>
    /// <param name="startIndex">Start position</param>
    /// <returns>The length of the element</returns>
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

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <returns>A Byte list</returns>
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