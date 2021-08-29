package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
    /// Specifies an object group metadata.
    /// </summary>
    public class ObjectGroupMetadata extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the ObjectGroupMetadata class.
        /// </summary>
        public ObjectGroupMetadata()
        {
            super(StreamObjectTypeHeaderStart.ObjectGroupMetadata);
        }

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the expected change frequency of the object.
        /// This value MUST be:
        /// 0, if the change frequency is not known.
        /// 1, if the object is known to change frequently.
        /// 2, if the object is known to change infrequently.
        /// 3, if the object is known to change independently of any other objects.
        /// </summary>
        public Compact64bitInt ObjectChangeFrequency;

        /// <summary>
        /// Used to de-serialize the element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            AtomicInteger index = new AtomicInteger(currentIndex.get());
            this.ObjectChangeFrequency = BasicObject.parse(byteArray, index, Compact64bitInt.class);

            if (index.get() - currentIndex.get() !=lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupMetadata", "Stream object over-parse error", null);
            }

            currentIndex.set(index.get());
        }

        /// <summary>
        /// Used to convert the element into a byte List 
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>The number of elements actually contained in the list</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            List<Byte> tmpList = this.ObjectChangeFrequency.SerializeToByteList();
            byteList.addAll(tmpList);
            return tmpList.size();
        }
    }