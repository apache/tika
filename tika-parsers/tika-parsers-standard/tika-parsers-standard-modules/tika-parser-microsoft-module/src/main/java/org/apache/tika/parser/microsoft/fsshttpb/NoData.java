package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;

/// <summary>
    /// This class is used to represent the property contains no data.
    /// </summary>
    public class NoData implements IProperty
    {
        /// <summary>
        /// This method is used to deserialize the NoData from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the NoData.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            return 0;
        }
        /// <summary>
        /// This method is used to convert the element of NoData into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of NoData.</returns>
        public List<Byte> SerializeToByteList()
        {
            return new ArrayList<>();
        }
    }