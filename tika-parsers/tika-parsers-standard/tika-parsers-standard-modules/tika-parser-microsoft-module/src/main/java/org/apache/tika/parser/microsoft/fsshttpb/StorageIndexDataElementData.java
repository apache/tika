package org.apache.tika.parser.microsoft.fsshttpb;

public class StorageIndexDataElementData : DataElementData
    {
        /// <summary>
        /// Initializes a new instance of the StorageIndexDataElementData class.
        /// </summary>
        public StorageIndexDataElementData()
        {
            this.StorageIndexManifestMapping = new StorageIndexManifestMapping();
            this.StorageIndexCellMappingList = new List<StorageIndexCellMapping>();
            this.StorageIndexRevisionMappingList = new List<StorageIndexRevisionMapping>();
        }

        /// <summary>
        /// Gets or sets the storage index manifest mappings (with manifest mapping extended GUID and serial number).
        /// </summary>
        public StorageIndexManifestMapping StorageIndexManifestMapping { get; set; }

        /// <summary>
        /// Gets or sets  storage index manifest mappings.
        /// </summary>
        public List<StorageIndexCellMapping> StorageIndexCellMappingList { get; set; }

        /// <summary>
        /// Gets or sets the list of storage index revision mappings (with revision and revision mapping extended GUIDs, and revision mapping serial number).
        /// </summary>
        public List<StorageIndexRevisionMapping> StorageIndexRevisionMappingList { get; set; }

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <returns>A Byte list</returns>
        public override List<byte> SerializeToByteList()
        {
            List<byte> byteList = new List<byte>();

            if (this.StorageIndexManifestMapping != null)
            {
                byteList.AddRange(this.StorageIndexManifestMapping.SerializeToByteList());
            }
            
            if (this.StorageIndexCellMappingList != null)
            {
                foreach (StorageIndexCellMapping cellMapping in this.StorageIndexCellMappingList)
                {
                    byteList.AddRange(cellMapping.SerializeToByteList());
                }
            }

            // Storage Index Revision Mapping 
            if (this.StorageIndexRevisionMappingList != null)
            {
                foreach (StorageIndexRevisionMapping revisionMapping in this.StorageIndexRevisionMappingList)
                {
                    byteList.AddRange(revisionMapping.SerializeToByteList());
                }
            }

            return byteList;
        }

        /// <summary>
        /// Used to de-serialize the data element.
        /// </summary>
        /// <param name="byteArray">Byte array</param>
        /// <param name="startIndex">Start position</param>
        /// <returns>The length of the element</returns>
        public override int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
        {
            int index = startIndex;
            int headerLength = 0;
            StreamObjectHeaderStart header;
            bool isStorageIndexManifestMappingExist = false;
            while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index, out header)) != 0)
            {
                index += headerLength;
                if (header.Type == StreamObjectTypeHeaderStart.StorageIndexManifestMapping)
                {
                    if (isStorageIndexManifestMappingExist)
                    {
                        throw new DataElementParseErrorException(index - headerLength, "Failed to parse StorageIndexDataElement, only can contain zero or one StorageIndexManifestMapping", null);
                    }

                    this.StorageIndexManifestMapping = StreamObject.ParseStreamObject(header, byteArray, ref index) as StorageIndexManifestMapping;
                    isStorageIndexManifestMappingExist = true;
                }
                else if (header.Type == StreamObjectTypeHeaderStart.StorageIndexCellMapping)
                {
                    this.StorageIndexCellMappingList.Add(StreamObject.ParseStreamObject(header, byteArray, ref index) as StorageIndexCellMapping);
                }
                else if (header.Type == StreamObjectTypeHeaderStart.StorageIndexRevisionMapping)
                {
                    this.StorageIndexRevisionMappingList.Add(StreamObject.ParseStreamObject(header, byteArray, ref index) as StorageIndexRevisionMapping);
                }
                else
                {
                    throw new DataElementParseErrorException(index - headerLength, "Failed to parse StorageIndexDataElement, expect the inner object type StorageIndexCellMapping or StorageIndexRevisionMapping, but actual type value is " + header.Type, null);
                }
            }

            return index - startIndex;
        }
    }