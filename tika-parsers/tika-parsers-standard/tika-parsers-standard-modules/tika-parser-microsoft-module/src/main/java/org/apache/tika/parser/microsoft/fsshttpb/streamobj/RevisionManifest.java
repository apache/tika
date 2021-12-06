package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;

public class RevisionManifest extends StreamObject {
    /**
     * Initializes a new instance of the RevisionManifest class.
     */
    public RevisionManifest() {
        super(StreamObjectTypeHeaderStart.RevisionManifest);
    }

    /**
     * Gets or sets an extended GUID that specifies the revision identifier represented by this data element.
     */
    public ExGuid RevisionID;

    /**
     * Gets or sets an extended GUID that specifies the revision identifier of a base revision that could contain additional information for this revision.
     */
    public ExGuid BaseRevisionID;

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
        this.RevisionID = BasicObject.parse(byteArray, index, ExGuid.class);
        this.BaseRevisionID = BasicObject.parse(byteArray, index, ExGuid.class);

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "RevisionManifest",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The length of list
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.RevisionID.SerializeToByteList());
        byteList.addAll(this.BaseRevisionID.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}