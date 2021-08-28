package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
    /// Object Metadata Declaration
    /// </summary>
    public class ObjectGroupMetadataDeclarations extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the ObjectGroupMetadataDeclarations class.
        /// </summary>
        public ObjectGroupMetadataDeclarations()
            : base(StreamObjectTypeHeaderStart.ObjectGroupMetadataDeclarations)
        {
            this.ObjectGroupMetadataList = new ArrayList<ObjectGroupMetadata>();
        }

        /// <summary>
        /// Gets or sets a list of Object Metadata.
        /// </summary>
        public List<ObjectGroupMetadata> ObjectGroupMetadataList;

        /// <summary>
        /// Used to convert the element into a byte List 
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>A constant value 0</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            if (this.ObjectGroupMetadataList != null)
            {
                foreach (ObjectGroupMetadata objectGroupMetadata in this.ObjectGroupMetadataList)
                {
                    byteList.addAll(objectGroupMetadata.SerializeToByteList());
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
                throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupMetadataDeclarations", "Stream object over-parse error", null);
            }

            AtomicInteger index = new AtomicInteger(currentIndex.get());
            int headerLength = 0;
            StreamObjectHeaderStart header;
            this.ObjectGroupMetadataList = new ArrayList<ObjectGroupMetadata>();

            while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index, out header)) != 0)
            {
                index += headerLength;
                if (header.Type == StreamObjectTypeHeaderStart.ObjectGroupMetadata)
                {
                    this.ObjectGroupMetadataList.Add(StreamObject.ParseStreamObject(header, byteArray, ref index) as ObjectGroupMetadata);
                }
                else
                {
                    throw new StreamObjectParseErrorException(index.get(),  "ObjectGroupDeclarations", "Failed to parse ObjectGroupMetadataDeclarations, expect the inner object type ObjectGroupMetadata, but actual type value is " + header.Type, null);
                }
            }

            currentIndex.set(index.get());
        }
    }