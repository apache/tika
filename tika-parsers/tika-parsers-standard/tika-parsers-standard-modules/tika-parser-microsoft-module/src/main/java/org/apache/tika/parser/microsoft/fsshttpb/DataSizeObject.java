package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
    /// Data Size Object
    /// </summary>
    public class DataSizeObject extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the DataSizeObject class.
        /// </summary>
        public DataSizeObject()
        {
            super(StreamObjectTypeHeaderStart.DataSizeObject);
        }

        /// <summary>
        /// Gets or sets an unsigned 64-bit integer that specifies the size of the file data represented by this root node object.
        /// </summary>
        public long DataSize;

        /// <summary>
        /// Used to de-serialize the element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override
        protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            if (lengthOfItems != 8)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "DataSize", "Stream Object over-parse error", null);
            }

            this.DataSize = LittleEndianBitConverter.ToUInt64(byteArray, currentIndex.get());
            currentIndex.addAndGet(8);
        }

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>A constant value 8</returns>
        @Override
        protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            ByteUtil.appendByteArrayToListOfByte(byteList, LittleEndianBitConverter.GetBytes(this.DataSize));
            return 8;
        }
    }