package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Specifies a revision manifest object group references, each followed by object group extended GUIDs
 */
public class RevisionManifestObjectGroupReferences extends StreamObject {
    /**
     * Initializes a new instance of the RevisionManifestObjectGroupReferences class.
     */
    public RevisionManifestObjectGroupReferences() {
        super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
    }

    /**
     * Initializes a new instance of the RevisionManifestObjectGroupReferences class.
     *
     * @param objectGroupExtendedGUID Extended GUID
     */
    public RevisionManifestObjectGroupReferences(ExGuid objectGroupExtendedGUID) {
        super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
        this.ObjectGroupExtendedGUID = objectGroupExtendedGUID;
    }

    /**
     * Gets or sets an extended GUID that specifies the object group for each Revision Manifest Object Group References.
     */
    public ExGuid ObjectGroupExtendedGUID;

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.ObjectGroupExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "RevisionManifestObjectGroupReferences",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The number of elements actually contained in the list.
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        List<Byte> tmpList = this.ObjectGroupExtendedGUID.SerializeToByteList();
        byteList.addAll(tmpList);
        return tmpList.size();
    }
}