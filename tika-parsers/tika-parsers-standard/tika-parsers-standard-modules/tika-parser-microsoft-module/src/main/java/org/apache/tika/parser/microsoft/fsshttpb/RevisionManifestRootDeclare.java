package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// Specifies a revision manifest root declare, each followed by root and object extended GUIDs.
/// </summary>
public class RevisionManifestRootDeclare extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the RevisionManifestRootDeclare class.
    /// </summary>
    public RevisionManifestRootDeclare() {
        super(StreamObjectTypeHeaderStart.RevisionManifestRootDeclare);
    }

    /// <summary>
    /// Gets or sets an extended GUID that specifies the root revision for each revision manifest root declare.
    /// </summary>
    public ExGuid RootExGuid;

    /// <summary>
    /// Gets or sets an extended GUID that specifies the object for each revision manifest root declare.
    /// </summary>
    public ExGuid ObjectExGuid;

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte list</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        this.RootExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
        this.ObjectExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "RevisionManifestRootDeclare",
                    "Stream object over-parse error", null);
        }

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The length of list</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int itemsIndex = byteList.size();
        byteList.addAll(this.RootExGuid.SerializeToByteList());
        byteList.addAll(this.ObjectExGuid.SerializeToByteList());
        return byteList.size() - itemsIndex;
    }
}