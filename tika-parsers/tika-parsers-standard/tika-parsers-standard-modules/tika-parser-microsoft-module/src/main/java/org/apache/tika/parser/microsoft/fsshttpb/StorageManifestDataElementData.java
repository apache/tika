package org.apache.tika.parser.microsoft.fsshttpb;

public class StorageManifestDataElementData : DataElementData
    {
        /// <summary>
        /// Initializes a new instance of the StorageManifestDataElementData class.
        /// </summary>
        public StorageManifestDataElementData()
        {
            // Storage Manifest
            this.StorageManifestSchemaGUID = new StorageManifestSchemaGUID();
            this.StorageManifestRootDeclareList = new List<StorageManifestRootDeclare>();
        }

        /// <summary>
        /// Gets or sets storage manifest schema GUID.
        /// </summary>
        public StorageManifestSchemaGUID StorageManifestSchemaGUID { get; set; }

        /// <summary>
        /// Gets or sets storage manifest root declare.
        /// </summary>
        public List<StorageManifestRootDeclare> StorageManifestRootDeclareList { get; set; }

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <returns>A Byte list</returns>
        public override List<byte> SerializeToByteList()
        {
            List<byte> byteList = new List<byte>();
            byteList.AddRange(this.StorageManifestSchemaGUID.SerializeToByteList());

            if (this.StorageManifestRootDeclareList != null)
            {
                foreach (StorageManifestRootDeclare storageManifestRootDeclare in this.StorageManifestRootDeclareList)
                {
                    byteList.AddRange(storageManifestRootDeclare.SerializeToByteList());
                }
            }

            return byteList;
        }

        /// <summary>
        /// Used to de-serialize data element.
        /// </summary>
        /// <param name="byteArray">Byte array</param>
        /// <param name="startIndex">Start position</param>
        /// <returns>The length of the array</returns>
        public override int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
        {
            int index = startIndex;

            this.StorageManifestSchemaGUID = StreamObject.GetCurrent<StorageManifestSchemaGUID>(byteArray, ref index);
            this.StorageManifestRootDeclareList = new List<StorageManifestRootDeclare>();

            StreamObjectHeaderStart header;
            int headerLength = 0;
            while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index, out header)) != 0)
            {
                if (header.Type == StreamObjectTypeHeaderStart.StorageManifestRootDeclare)
                {
                    index += headerLength;
                    this.StorageManifestRootDeclareList.Add(StreamObject.ParseStreamObject(header, byteArray, ref index) as StorageManifestRootDeclare);
                }
                else
                {
                    throw new DataElementParseErrorException(index, "Failed to parse StorageManifestDataElement, expect the inner object type StorageManifestRootDeclare, but actual type value is " + header.Type, null);
                }
            }

            return index - startIndex;
        }
    }