package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// <summary>
    /// This class is used to represent the prtFourBytesOfLengthFollowedByData.
    /// </summary>
    public class PrtFourBytesOfLengthFollowedByData implements IProperty
    {
        /// <summary>
        /// Gets or sets an unsigned integer that specifies the size, in bytes, of the Data field.
        /// </summary>
        public int CB;

        /// <summary>
        /// Gets or sets the value of Data field.
        /// </summary>
        public byte[] Data;
        /// <summary>
        /// This method is used to deserialize the prtFourBytesOfLengthFollowedByData from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the prtFourBytesOfLengthFollowedByData.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            int index = startIndex;
            this.CB = (int)BitConverter.ToUInt32(byteArray, startIndex);
            index += 4;
            this.Data = Arrays.copyOfRange(byteArray, index, index + this.CB);
            index += this.CB;

            return index - startIndex;
        }

        /// <summary>
        /// This method is used to convert the element of prtFourBytesOfLengthFollowedByData into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of prtFourBytesOfLengthFollowedByData.</returns>
        public List<Byte> SerializeToByteList()
        {
            List<Byte> byteList = new ArrayList<>();
            for (byte b : BitConverter.getBytes(this.CB)) {
                byteList.add(b);
            }
            for (byte b : this.Data) {
                byteList.add(b);
            }
            return byteList;
        }
    }