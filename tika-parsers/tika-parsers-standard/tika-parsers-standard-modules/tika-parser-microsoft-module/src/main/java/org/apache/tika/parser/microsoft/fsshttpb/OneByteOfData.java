package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;

/// <summary>
    /// This class is used to represent the property contains 1 byte of data in the PropertySet.rgData stream field.
    /// </summary>
    public class OneByteOfData implements IProperty
    {
        /// <summary>
        /// Gets or sets the data of property.
        /// </summary>
        public byte Data;

        /// <summary>
        /// This method is used to deserialize the OneByteOfData from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the OneByteOfData.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            this.Data = byteArray[startIndex];
            return 1;
        }
        /// <summary>
        /// This method is used to convert the element of OneByteOfData into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of OneByteOfData.</returns>
        public List<Byte> SerializeToByteList()
        {
            return new ArrayList<>(this.Data);
        }
    }