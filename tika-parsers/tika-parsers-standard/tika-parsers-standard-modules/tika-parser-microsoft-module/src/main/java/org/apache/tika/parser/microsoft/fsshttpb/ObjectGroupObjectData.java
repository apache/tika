package org.apache.tika.parser.microsoft.fsshttpb;
/// <summary>
    /// object data 
    /// </summary>

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectGroupObjectData extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the ObjectGroupObjectData class.
        /// </summary>
        public ObjectGroupObjectData()
        {
            super(StreamObjectTypeHeaderStart.ObjectGroupObjectData);
            this.ObjectExGUIDArray = new ExGUIDArray();
            this.cellIDArray = new CellIDArray();
            this.Data = new BinaryItem();
        }

        /// <summary>
        /// Gets or sets an extended GUID array that specifies the object group.
        /// </summary>
        public ExGUIDArray ObjectExGUIDArray;

        /// <summary>
        /// Gets or sets a cell ID array that specifies the object group.
        /// </summary>
        public CellIDArray cellIDArray;

        /// <summary>
        /// Gets or sets a byte stream that specifies the binary data which is opaque to this protocol.
        /// </summary>
        public BinaryItem Data;

        /// <summary>
        /// Used to de-serialize the element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            AtomicInteger index = new AtomicInteger(currentIndex.get());
            this.ObjectExGUIDArray = BasicObject.parse(byteArray, index, ExGUIDArray.class);
            this.cellIDArray = BasicObject.parse(byteArray, index, CellIDArray.class);
            this.Data = BasicObject.parse(byteArray, index, BinaryItem.class);

            if (index.get() - currentIndex.get() !=lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectData", "Stream object over-parse error", null);
            }

            currentIndex.set(index.get());
        }

        /// <summary>
        /// Used to convert the element into a byte List 
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>The number of the element</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            int itemsIndex = byteList.size();
            byteList.addAll(this.ObjectExGUIDArray.SerializeToByteList());
            byteList.addAll(this.cellIDArray.SerializeToByteList());
            byteList.addAll(this.Data.SerializeToByteList());
            return byteList.size() - itemsIndex;
        }
    }