package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RevisionManifest extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the RevisionManifest class.
        /// </summary>
        public RevisionManifest()
        {
                super(StreamObjectTypeHeaderStart.RevisionManifest);
        }

        /// <summary>
        /// Gets or sets an extended GUID that specifies the revision identifier represented by this data element.
        /// </summary>
        public ExGuid RevisionID;

        /// <summary>
        /// Gets or sets an extended GUID that specifies the revision identifier of a base revision that could contain additional information for this revision.
        /// </summary>
        public ExGuid BaseRevisionID;

        /// <summary>
        /// Used to de-serialize the element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            AtomicInteger index = new AtomicInteger(currentIndex.get());
            this.RevisionID = BasicObject.parse(byteArray, index, ExGuid.class);
            this.BaseRevisionID = BasicObject.parse(byteArray, index, ExGuid.class);

            if (index.get() - currentIndex.get() != lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "RevisionManifest", "Stream object over-parse error", null);
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
            byteList.addAll(this.RevisionID.SerializeToByteList());
            byteList.addAll(this.BaseRevisionID.SerializeToByteList());
            return byteList.size() - itemsIndex;
        }
    }