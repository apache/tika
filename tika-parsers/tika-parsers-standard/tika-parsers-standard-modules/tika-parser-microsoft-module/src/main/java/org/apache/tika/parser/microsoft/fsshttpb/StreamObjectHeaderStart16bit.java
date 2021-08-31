package org.apache.tika.parser.microsoft.fsshttpb;    /// <summary>

import java.util.ArrayList;
import java.util.List;

/// An 16-bit header for a compound object would indicate the start of a stream object
    /// </summary>
    public class StreamObjectHeaderStart16bit extends StreamObjectHeaderStart
    {
        /// <summary>
        /// Initializes a new instance of the StreamObjectHeaderStart16bit class with specified type and length.
        /// </summary>
        /// <param name="type">Specify the type of the StreamObjectHeaderStart16bit.</param>
        /// <param name="length">Specify the length of the StreamObjectHeaderStart16bit.</param>
        public StreamObjectHeaderStart16bit(StreamObjectTypeHeaderStart type, int length)
        {
            if (this.length > 127)
            {
                throw new RuntimeException("Field Length - 16-bit Stream Object Header Start, Length (7-bits): A 7-bit unsigned integer that specifies the length in bytes for additional data (if any). If the length is more than 127 bytes, a 32-bit stream object header start MUST be used.");
            }

            this.headerType = 0x0;
            this.type = type;
            this.compound = StreamObject.getCompoundTypes().contains(this.type) ? 1 : 0;
            this.length = length;
        }

        /// <summary>
        /// Initializes a new instance of the StreamObjectHeaderStart16bit class with specified type.
        /// </summary>
        /// <param name="type">Specify the type of the StreamObjectHeaderStart16bit.</param>
        public StreamObjectHeaderStart16bit(StreamObjectTypeHeaderStart type) {
            this(type, 0);
        }

        /// <summary>
        /// Initializes a new instance of the StreamObjectHeaderStart16bit class, this is the default constructor.
        /// </summary>
        public StreamObjectHeaderStart16bit()
        {
        }

        /// <summary>
        /// This method is used to convert the element of StreamObjectHeaderStart16bit basic object into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of StreamObjectHeaderStart16bit.</returns>
        @Override
        public List<Byte> SerializeToByteList()
        {
            BitWriter bitField = new BitWriter(2);
            bitField.AppendInit32(this.headerType, 2);
            bitField.AppendInit32(this.compound, 1);
            bitField.AppendUInit32(this.type.getIntVal(), 6);
            bitField.AppendInit32(this.length, 7);
            List<Byte> result = new ArrayList<>();
            ByteUtil.appendByteArrayToListOfByte(result, bitField.getBytes());
            return result;
        }

        /// <summary>
        /// This method is used to get the Uint16 value of the 16bit stream object header.
        /// </summary>
        /// <returns>Return the ushort value.</returns>
        public short ToUint16()
        {
            return LittleEndianBitConverter.ToUInt16(ByteUtil.toByteArray(this.SerializeToByteList()), 0);
        }

        /// <summary>
        /// This method is used to deserialize the StreamObjectHeaderStart16bit basic object from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the StreamObjectHeaderStart16bit basic object.</returns>
        @Override
        protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            BitReader bitReader = new BitReader(byteArray, startIndex);
            this.headerType = bitReader.ReadInt32(2);
            if (this.headerType != StreamObjectHeaderStart.StreamObjectHeaderStart16bit)
            {
                throw new RuntimeException(String.format("Failed to get the StreamObjectHeaderStart16bit header type value, expect value %s, but actual value is %s", StreamObjectHeaderStart16bit, this.headerType));
            }

            this.compound = bitReader.ReadInt32(1);
            int typeValue = bitReader.ReadInt32(6);
            this.type = StreamObjectTypeHeaderStart.fromIntVal(typeValue);
            if (type == null)
            {
                throw new RuntimeException(String.format("Failed to get the StreamObjectHeaderStart16bit type value, the value %s is not defined", typeValue));
            }

            if (StreamObject.getCompoundTypes().contains(type) && this.compound != 1)
            {
                throw new RuntimeException(String.format("Failed to parse the StreamObjectHeaderStart16bit header. If the type value is %s then the compound value should 1, but actual value is 0", typeValue));
            }

            this.length = bitReader.ReadInt32(7);
            if (this.length > 127)
            {
                throw new RuntimeException("16-bit Stream Object Header Start, Length (7-bits): A 7-bit unsigned integer that specifies the length in bytes for additional data (if any). If the length is more than 127 bytes, a 32-bit stream object header start MUST be used.");
            }

            return 2;
        }
    }