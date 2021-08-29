package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StorageIndexManifestMapping extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the StorageIndexManifestMapping class.
        /// </summary>
        public StorageIndexManifestMapping()
        {
                super(StreamObjectTypeHeaderStart.StorageIndexManifestMapping);
        }

        /// <summary>
        /// Gets or sets the extended GUID of the manifest mapping.
        /// </summary>
        public ExGuid ManifestMappingExGuid;

        /// <summary>
        /// Gets or sets the serial number of the manifest mapping.
        /// </summary>
        public SerialNumber ManifestMappingSerialNumber;

        /// <summary>
        /// Used to Deserialize the items.
        /// </summary>
        /// <param name="byteArray">Byte array</param>
        /// <param name="currentIndex">Start position</param>
        /// <param name="lengthOfItems">The length of the items</param>
        @Override protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems)
        {
            AtomicInteger index = new AtomicInteger(currentIndex.get());
            this.ManifestMappingExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
            this.ManifestMappingSerialNumber = BasicObject.parse(byteArray, index, SerialNumber.class);

            if (index.get() - currentIndex.get() != lengthOfItems)
            {
                throw new StreamObjectParseErrorException(currentIndex.get(), "StorageIndexManifestMapping", "Stream object over-parse error", null);
            }

            currentIndex.set(index.get());
        }

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <param name="byteList">A Byte list</param>
        /// <returns>The length of list</returns>
        @Override protected int SerializeItemsToByteList(List<Byte> byteList)
        {
            int itemsIndex = byteList.size();
            byteList.addAll(this.ManifestMappingExGuid.SerializeToByteList());
            byteList.addAll(this.ManifestMappingSerialNumber.SerializeToByteList());
            return byteList.size() - itemsIndex;
        }
    }