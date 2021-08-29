package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
    /// object data BLOB declaration 
    /// </summary>
    public class ObjectGroupObjectBLOBDataDeclaration extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the ObjectGroupObjectBLOBDataDeclaration class.
        /// </summary>
        public ObjectGroupObjectBLOBDataDeclaration()
        {
            super(StreamObjectTypeHeaderStart.ObjectGroupObjectBLOBDataDeclaration);
            this.ObjectExGUID = new ExGuid();
            this.ObjectDataBLOBExGUID = new ExGuid();
            this.ObjectPartitionID = new Compact64bitInt();
            this.ObjectDataSize = new Compact64bitInt();
            this.ObjectReferencesCount = new Compact64bitInt();
            this.CellReferencesCount = new Compact64bitInt();
        }

        /// <summary>
        /// Gets or sets an extended GUID that specifies the object.
        /// </summary>
        public ExGuid ObjectExGUID;

        /// <summary>
        /// Gets or sets an extended GUID that specifies the object data BLOB.
        /// </summary>
        public ExGuid ObjectDataBLOBExGUID;

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the partition.
        /// </summary>
        public Compact64bitInt ObjectPartitionID;

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the size in bytes of the object.opaque binary data  for the declared object. 
        /// This MUST match the size of the binary item in the corresponding object data BLOB referenced by the Object Data BLOB reference for this object.
        /// </summary>
        public Compact64bitInt ObjectDataSize;

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the number of object references.
        /// </summary>
        public Compact64bitInt ObjectReferencesCount;

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the number of cell references.
        /// </summary>
        public Compact64bitInt CellReferencesCount;

        /// <summary>
        /// Used to de-serialize the element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            AtomicInteger index = new AtomicInteger(currentIndex.get());

            this.ObjectExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
            this.ObjectDataBLOBExGUID = BasicObject.parse(byteArray, index, ExGuid.class);
            this.ObjectPartitionID = BasicObject.parse(byteArray, index, Compact64bitInt.class);
            this.ObjectReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);
            this.CellReferencesCount = BasicObject.parse(byteArray, index, Compact64bitInt.class);

            if (index.get() - currentIndex.get() !=lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectBLOBDataDeclaration", "Stream object over-parse error", null);
            }

            currentIndex.set(index.get());
        }

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>The number of the element</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            int itemsIndex = byteList.size();
            byteList.addAll(this.ObjectExGUID.SerializeToByteList());
            byteList.addAll(this.ObjectDataBLOBExGUID.SerializeToByteList());
            byteList.addAll(this.ObjectPartitionID.SerializeToByteList());
            byteList.addAll(this.ObjectDataSize.SerializeToByteList());
            byteList.addAll(this.ObjectReferencesCount.SerializeToByteList());
            byteList.addAll(this.CellReferencesCount.SerializeToByteList());
            return byteList.size() - itemsIndex;
        }
    }