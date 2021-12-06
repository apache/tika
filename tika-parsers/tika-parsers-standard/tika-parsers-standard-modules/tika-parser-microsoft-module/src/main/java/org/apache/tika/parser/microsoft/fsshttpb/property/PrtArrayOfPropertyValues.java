package org.apache.tika.parser.microsoft.fsshttpb.property;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.PropertySet;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.fsshttpb.util.BitConverter;

/// <summary>
    /// The class is used to represent the prtArrayOfPropertyValues . 
    /// </summary>
    public class PrtArrayOfPropertyValues implements IProperty
    {
        /// <summary>
        /// Gets or sets an unsigned integer that specifies the number of properties in Data.
        /// </summary>
        public int CProperties;
        /// <summary>
        /// Gets or sets the value of prid field.
        /// </summary>
        public PropertyID Prid;
        /// <summary>
        /// Gets or sets the value of Data field.
        /// </summary>
        public PropertySet[] Data;
        /// <summary>
        /// This method is used to deserialize the prtArrayOfPropertyValues from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the prtArrayOfPropertyValues.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            int index = startIndex;
            this.CProperties = BitConverter.toInt32(byteArray, index);
            index += 4;
            this.Prid = new PropertyID();
            int len = this.Prid.DoDeserializeFromByteArray(byteArray, index);
            index += len;
            this.Data = new PropertySet[this.CProperties];
            for (int i = 0; i < this.CProperties; i++)
            {
                this.Data[i] = new PropertySet();
                int length = this.Data[i].DoDeserializeFromByteArray(byteArray, index);
                index += length;
            }

            return index - startIndex;
        }
        /// <summary>
        /// This method is used to convert the element of the prtArrayOfPropertyValues into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of the prtArrayOfPropertyValues.</returns>
        public List<Byte> SerializeToByteList()
        {
            List<Byte> byteList = new ArrayList<>();
            for (byte b : BitConverter.getBytes(this.CProperties)) {
                byteList.add(b);
            }
            byteList.addAll(this.Prid.SerializeToByteList());
            for (PropertySet ps : this.Data)
            {
                byteList.addAll(ps.SerializeToByteList());
            }
            return byteList;
        }
    }