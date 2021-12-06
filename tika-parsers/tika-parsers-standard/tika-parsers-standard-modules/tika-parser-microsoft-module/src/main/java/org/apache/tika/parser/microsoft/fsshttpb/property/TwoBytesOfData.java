package org.apache.tika.parser.microsoft.fsshttpb.property;

import java.util.List;

import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;

/// <summary>
    /// This class is used to represent the property contains 2 bytes of data in the PropertySet.rgData stream field.
    /// </summary>
    public class TwoBytesOfData implements IProperty
    {
        /// <summary>
        /// Gets or sets the data of property.
        /// </summary>
        public byte[] Data;

        /// <summary>
        /// This method is used to deserialize the TwoBytesOfData from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the TwoBytesOfData.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            this.Data = new byte[] { byteArray[startIndex], byteArray[startIndex + 1] };

            return 2;
        }
        /// <summary>
        /// This method is used to convert the element of TwoBytesOfData into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of TwoBytesOfData.</returns>
        public List<Byte> SerializeToByteList()
        {
            return ByteUtil.toListOfByte(this.Data);
        }
    }