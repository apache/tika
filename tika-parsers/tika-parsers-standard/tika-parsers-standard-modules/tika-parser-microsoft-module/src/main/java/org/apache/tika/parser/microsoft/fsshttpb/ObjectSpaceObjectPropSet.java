package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;

/// <summary>
    /// This class is used to represent a ObjectSpaceObjectPropSet.
    /// </summary>
    public class ObjectSpaceObjectPropSet
    {
        /// <summary>
        /// Gets or sets an ObjectSpaceObjectStreamOfOIDs that specifies the count and list of objects that are referenced by this ObjectSpaceObjectPropSet.
        /// </summary>
        public ObjectSpaceObjectStreamOfOIDs OIDs;
        /// <summary>
        /// Gets or sets The value of OSIDs.
        /// </summary>
        public ObjectSpaceObjectStreamOfOSIDs OSIDs;
        /// <summary>
        /// Gets or sets the value of ContextIDs field.
        /// </summary>
        public ObjectSpaceObjectStreamOfContextIDs ContextIDs;
        /// <summary>
        /// Gets or sets the value of body field.
        /// </summary>
        public PropertySet Body;
        /// <summary>
        /// Gets or sets the value of padding field.
        /// </summary>
        public byte[] Padding;

        /// <summary>
        /// This method is used to deserialize the ObjectSpaceObjectPropSet from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the ObjectSpaceObjectPropSet.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            int index = startIndex;
            this.OIDs = new ObjectSpaceObjectStreamOfOIDs();
            int len = this.OIDs.DoDeserializeFromByteArray(byteArray, index);
            index += len;
            if (this.OIDs.Header.OsidStreamNotPresent == 0)
            {
                this.OSIDs = new ObjectSpaceObjectStreamOfOSIDs();
                len = this.OSIDs.DoDeserializeFromByteArray(byteArray, index);
                index += len;

                if (this.OSIDs.Header.ExtendedStreamsPresent == 1)
                {
                    this.ContextIDs = new ObjectSpaceObjectStreamOfContextIDs();
                    len = this.ContextIDs.DoDeserializeFromByteArray(byteArray, index);
                    index += len;
                }
            }
            this.Body = new PropertySet();
            len = this.Body.DoDeserializeFromByteArray(byteArray, index);
            index += len;

            int paddingLength = 8 - (index - startIndex) % 8;
            if (paddingLength < 8)
            {
                this.Padding = new byte[paddingLength];
                index += paddingLength;
            }
            return index - startIndex;
        }
        /// <summary>
        /// This method is used to convert the element of the ObjectSpaceObjectPropSet into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of the ObjectSpaceObjectPropSet.</returns>
        public List<Byte> SerializeToByteList()
        {
            List<Byte> byteList = new ArrayList<>();
            byteList.addAll(this.OIDs.SerializeToByteList());
            byteList.addAll(this.OSIDs.SerializeToByteList());
            byteList.addAll(this.ContextIDs.SerializeToByteList());
            byteList.addAll(this.Body.SerializeToByteList());
            for (byte b : this.Padding) {
                byteList.add(b);
            }
            return byteList;
        }
    }