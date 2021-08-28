package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/// <summary>
    /// Object Data
    /// </summary>
    public class ObjectGroupData extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the ObjectGroupData class.
        /// </summary>
        public ObjectGroupData()
        {
            super(StreamObjectTypeHeaderStart.ObjectGroupData);
            this.ObjectGroupObjectDataList = new ArrayList<ObjectGroupObjectData>();
            this.ObjectGroupObjectDataBLOBReferenceList = new ArrayList<ObjectGroupObjectDataBLOBReference>();
        }

        /// <summary>
        /// Gets or sets a list of Object Data.
        /// </summary>
        public List<ObjectGroupObjectData> ObjectGroupObjectDataList;

        /// <summary>
        /// Gets or sets a list of object data BLOB references that specifies the object.
        /// </summary>
        public List<ObjectGroupObjectDataBLOBReference> ObjectGroupObjectDataBLOBReferenceList;

        /// <summary>
        /// Used to convert the element into a byte List 
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>A constant value 0</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            if (this.ObjectGroupObjectDataList != null)
            {
                for (ObjectGroupObjectData objectGroupObjectData : this.ObjectGroupObjectDataList)
                {
                    byteList.addAll(objectGroupObjectData.SerializeToByteList());
                }
            }

            if (this.ObjectGroupObjectDataBLOBReferenceList != null)
            {
                for (ObjectGroupObjectDataBLOBReference objectGroupObjectDataBLOBReference : this.ObjectGroupObjectDataBLOBReferenceList)
                {
                    byteList.addAll(objectGroupObjectDataBLOBReference.SerializeToByteList());
                }
            }

            return 0;
        }

        /// <summary>
        /// Used to de-serialize the element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            if (lengthOfItems != 0)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupDeclarations", "Stream object over-parse error", null);
            }

            AtomicInteger index = new AtomicInteger(currentIndex.get());
            int headerLength = 0;
            AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();

            this.ObjectGroupObjectDataList = new ArrayList<>();
            this.ObjectGroupObjectDataBLOBReferenceList = new ArrayList<>();

            while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0)
            {
                StreamObjectTypeHeaderStart type = header.get().type;
                if (type == StreamObjectTypeHeaderStart.ObjectGroupObjectData)
                {
                    index.addAndGet(headerLength);
                    this.ObjectGroupObjectDataList.add((ObjectGroupObjectData)StreamObject.ParseStreamObject(header.get(), byteArray, index));
                }
                else if (type == StreamObjectTypeHeaderStart.ObjectGroupObjectDataBLOBReference)
                {
                    index.addAndGet(headerLength);
                    this.ObjectGroupObjectDataBLOBReferenceList.add((ObjectGroupObjectDataBLOBReference)StreamObject.ParseStreamObject(header.get(), byteArray, index));
                }
                else
                {
                    throw new StreamObjectParseErrorException(index.get(),  "ObjectGroupDeclarations", "Failed to parse ObjectGroupData, expect the inner object type either ObjectGroupObjectData or ObjectGroupObjectDataBLOBReference, but actual type value is " +
                            type, null);
                }
            }

            currentIndex.set(index.get());
        }
    }