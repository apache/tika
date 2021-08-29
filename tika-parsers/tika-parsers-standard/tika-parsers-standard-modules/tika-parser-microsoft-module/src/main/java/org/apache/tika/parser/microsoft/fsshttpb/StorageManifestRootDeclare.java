package org.apache.tika.parser.microsoft.fsshttpb;/// <summary>

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// Specifies one or more storage manifest root declare.
    /// </summary>
    public class StorageManifestRootDeclare extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the StorageManifestRootDeclare class.
        /// </summary>
        public StorageManifestRootDeclare()
        {
            super(StreamObjectTypeHeaderStart.StorageManifestRootDeclare);
        }

        /// <summary>
        /// Gets or sets the root storage manifest.
        /// </summary>
        public ExGuid RootExGUID;

        /// <summary>
        /// Gets or sets the cell identifier.
        /// </summary>
        public CellID cellID;

        /// <summary>
        /// Used to de-serialize the items.
        /// </summary>
        /// <param name="byteArray">Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            AtomicInteger index = new AtomicInteger(currentIndex.get());
            this.RootExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
            this.cellID = BasicObject.parse(byteArray, index, CellID.class);

            if (index.get() - currentIndex.get() != lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "StorageManifestRootDeclare", "Stream object over-parse error", null);
            }

            currentIndex.set(index.get());
        }

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>The length of list</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            int itemsIndex = byteList.size();
            byteList.addAll(this.RootExGUID.SerializeToByteList());
            byteList.addAll(this.cellID.SerializeToByteList());
            return byteList.size() - itemsIndex;
        }
    }