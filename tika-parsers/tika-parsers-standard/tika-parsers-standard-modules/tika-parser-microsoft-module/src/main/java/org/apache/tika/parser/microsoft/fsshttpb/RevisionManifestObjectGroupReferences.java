package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
    /// Specifies a revision manifest object group references, each followed by object group extended GUIDs.
    /// </summary>
    public class RevisionManifestObjectGroupReferences extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the RevisionManifestObjectGroupReferences class.
        /// </summary>
        public RevisionManifestObjectGroupReferences()
        {
            super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
        }

        /// <summary>
        /// Initializes a new instance of the RevisionManifestObjectGroupReferences class.
        /// </summary>
        /// <param name="objectGroupExtendedGUID">Extended GUID</param>
        public RevisionManifestObjectGroupReferences(ExGuid objectGroupExtendedGUID)
        {
            super(StreamObjectTypeHeaderStart.RevisionManifestObjectGroupReferences);
            this.ObjectGroupExtendedGUID = objectGroupExtendedGUID;
        }

        /// <summary>
        /// Gets or sets an extended GUID that specifies the object group for each Revision Manifest Object Group References.
        /// </summary>
        public ExGuid ObjectGroupExtendedGUID;

        /// <summary>
        /// Used to de-serialize the element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            AtomicInteger index = new AtomicInteger(currentIndex.get());
            this.ObjectGroupExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
            if (index.get() - currentIndex.get() != lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "RevisionManifestObjectGroupReferences", "Stream object over-parse error", null);
            }

            currentIndex.set(index.get());
        }

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>The number of elements actually contained in the list.</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            List<Byte> tmpList = this.ObjectGroupExtendedGUID.SerializeToByteList();
            byteList.addAll(tmpList);
            return tmpList.size();
        }
    }