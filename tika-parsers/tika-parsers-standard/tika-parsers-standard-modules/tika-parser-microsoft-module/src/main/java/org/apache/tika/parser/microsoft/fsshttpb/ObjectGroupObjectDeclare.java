package org.apache.tika.parser.microsoft.fsshttpb;
/// <summary>
    /// object declaration 
    /// </summary>

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;[System.Diagnostics.CodeAnalysis.SuppressMessage("Microsoft.StyleCop.CSharp.MaintainabilityRules", "SA1402:FileMayOnlyContainASingleClass", Justification = "Easy to maintain one group of classes in one .cs file.")]
    public class ObjectGroupObjectDeclare extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the ObjectGroupObjectDeclare class.
        /// </summary>
        public ObjectGroupObjectDeclare()
            : base(StreamObjectTypeHeaderStart.ObjectGroupObjectDeclare)
        {
            this.ObjectExtendedGUID = new ExGuid();
            this.ObjectPartitionID = new Compact64bitInt();
            this.ObjectDataSize = new Compact64bitInt();
            this.ObjectReferencesCount = new Compact64bitInt();
            this.CellReferencesCount = new Compact64bitInt();

            this.ObjectPartitionID.DecodedValue = 1;
            this.ObjectReferencesCount.DecodedValue = 1;
            this.CellReferencesCount.DecodedValue = 0;
        }

        /// <summary>
        /// Gets or sets an extended GUID that specifies the data element hash.
        /// </summary>
        public ExGuid ObjectExtendedGUID;

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the partition.
        /// </summary>
        public Compact64bitInt ObjectPartitionID;

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the size in bytes of the object.binary data opaque 
        /// to this protocol for the declared object.
        /// This MUST match the size of the binary item in the corresponding object data for this object.
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

            this.ObjectExtendedGUID = BasicObject.Parse<ExGuid>(byteArray, ref index);
            this.ObjectPartitionID = BasicObject.Parse<Compact64bitInt>(byteArray, ref index);
            this.ObjectDataSize = BasicObject.Parse<Compact64bitInt>(byteArray, ref index);
            this.ObjectReferencesCount = BasicObject.Parse<Compact64bitInt>(byteArray, ref index);
            this.CellReferencesCount = BasicObject.Parse<Compact64bitInt>(byteArray, ref index);

            if (index.get() - currentIndex.get() !=lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupObjectDeclare", "Stream object over-parse error", null);
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
            byteList.addAll(this.ObjectExtendedGUID.SerializeToByteList());
            byteList.addAll(this.ObjectPartitionID.SerializeToByteList());
            byteList.addAll(this.ObjectDataSize.SerializeToByteList());
            byteList.addAll(this.ObjectReferencesCount.SerializeToByteList());
            byteList.addAll(this.CellReferencesCount.SerializeToByteList());
            return byteList.size() - itemsIndex;
        }
    }