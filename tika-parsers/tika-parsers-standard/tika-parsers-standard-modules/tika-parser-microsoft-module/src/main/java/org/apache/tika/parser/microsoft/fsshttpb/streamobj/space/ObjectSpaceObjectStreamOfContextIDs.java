package org.apache.tika.parser.microsoft.fsshttpb.streamobj.space;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.CompactID;

/// <summary>
    /// This class is used to represent a ObjectSpaceObjectStreamOfContextIDs.
    /// </summary>
    public class ObjectSpaceObjectStreamOfContextIDs
    {
        /// <summary>
        /// Gets or sets value of header field.
        /// </summary>
        public ObjectSpaceObjectStreamHeader Header;
        /// <summary>
        /// Gets or sets the value of body field.
        /// </summary>
        public CompactID[] Body;

        /// <summary>
        /// This method is used to convert the element of ObjectSpaceObjectStreamOfContextIDs object into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of ObjectSpaceObjectStreamOfContextIDs</returns>
        public List<Byte> SerializeToByteList()
        {
            List<Byte> byteList = new ArrayList<>();
            byteList.addAll(this.Header.SerializeToByteList());
            for (CompactID compactID : this.Body)
            {
                byteList.addAll(compactID.SerializeToByteList());
            }

            return byteList;
        }

        /// <summary>
        /// This method is used to deserialize the ObjectSpaceObjectStreamOfContextIDs object from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the ObjectSpaceObjectStreamOfContextIDs object.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            int index = startIndex;
            this.Header = new ObjectSpaceObjectStreamHeader();
            int headerCount = this.Header.DoDeserializeFromByteArray(byteArray, index);
            index += headerCount;

            this.Body = new CompactID[(int)this.Header.Count];
            for (int i = 0; i < this.Header.Count; i++)
            {
                CompactID compactID = new CompactID();
                int count = compactID.DoDeserializeFromByteArray(byteArray, startIndex);
                this.Body[i] = compactID;
                index += count;
            }

            return index - startIndex;
        }
    }