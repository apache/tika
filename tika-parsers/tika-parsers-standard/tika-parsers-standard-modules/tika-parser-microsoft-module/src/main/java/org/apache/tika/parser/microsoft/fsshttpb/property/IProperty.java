package org.apache.tika.parser.microsoft.fsshttpb.property;

import java.util.List;

/// <summary>
    /// The interface of the property in OneNote file.
    /// </summary>
    public interface IProperty
    {
        /// <summary>
        /// This method is used to convert the element of property into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of property.</returns>
        List<Byte> SerializeToByteList();

        /// <summary>
        /// This method is used to deserialize the property from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the property.</returns>
        int DoDeserializeFromByteArray(byte[] byteArray, int startIndex);
    }