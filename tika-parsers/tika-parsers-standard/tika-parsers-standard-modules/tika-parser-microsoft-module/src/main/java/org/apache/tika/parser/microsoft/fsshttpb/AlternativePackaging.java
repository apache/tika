package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AlternativePackaging
    {
        /// <summary>
        /// Gets or sets the value of guidFileType.
        /// </summary>
        public UUID guidFileType;
        /// <summary>
        /// Gets or sets the value of guidFile.
        /// </summary>
        public UUID guidFile;
        /// <summary>
        /// Gets or sets the value of guidLegacyFileVersion.
        /// </summary>
        public UUID guidLegacyFileVersion;
        /// <summary>
        /// Gets or sets the value of guidFileFormat.
        /// </summary>
        public UUID guidFileFormat;
        /// <summary>
        /// Gets or sets the value of rgbReserved.
        /// </summary>
        public long rgbReserved;
        /// <summary>
        /// Gets or sets the value of PackagingStart field.
        /// </summary>
        public StreamObjectHeaderStart32bit packagingStart;
        /// <summary>
        /// Gets or sets the value of StorageIndexExtendedGUID field.
        /// </summary>
        public ExGuid storageIndexExtendedGUID;
        /// <summary>
        /// Gets or sets the value of guidCellSchemaId field.
        /// </summary>
        public UUID guidCellSchemaId;
        /// <summary>
        /// Gets or sets the value of dataElementPackage field.
        /// </summary>
        public DataElementPackage dataElementPackage;
        /// <summary>
        /// Gets or sets the value of packagingEnd field.
        /// </summary>
        public StreamObjectHeaderEnd packagingEnd;

        /// <summary>
        /// This method is used to deserialize the Alternative Packaging object from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the Alternative Packaging object.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            AtomicInteger index = new AtomicInteger(startIndex);
            this.guidFileType = AdapterHelper.ReadGuid(byteArray, index.get());
            index.addAndGet(16);
            this.guidFile = AdapterHelper.ReadGuid(byteArray, index.get());
            index.addAndGet(16);
            this.guidLegacyFileVersion = AdapterHelper.ReadGuid(byteArray, index.get());
            index.addAndGet(16);
            this.guidFileFormat = AdapterHelper.ReadGuid(byteArray, index.get());
            index.addAndGet(16);
            this.rgbReserved = BitConverter.ToUInt32(byteArray, index.get());
            index.addAndGet(4);
            this.packagingStart = new StreamObjectHeaderStart32bit();
            this.packagingStart.DeserializeFromByteArray(byteArray, index.get());
            index.addAndGet(4);
            this.storageIndexExtendedGUID = BasicObject.parse(byteArray, index, ExGuid.class);
            this.guidCellSchemaId = AdapterHelper.ReadGuid(byteArray, index.get());
            index.addAndGet(16);
            AtomicReference<DataElementPackage> pkg = new AtomicReference<>();
            StreamObject.TryGetCurrent(byteArray, index, pkg, DataElementPackage.class);
            this.dataElementPackage = pkg.get();
            this.packagingEnd = new StreamObjectHeaderEnd16bit();
            this.packagingEnd.DeserializeFromByteArray(byteArray, index.get());
            index.addAndGet(2);

            return index.get() - startIndex;
        }
    }