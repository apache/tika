package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BitWriter
    {
        /// <summary>
        /// A byte buffer will contain all the written byte.
        /// </summary>
        private byte[] bytes;

        /// <summary>
        /// An offset which is used to keep trace for the current write position in bit, staring with 0.
        /// </summary>
        private int bitOffset;

        /// <summary>
        /// Initializes a new instance of the BitWriter class with specified buffer size in byte.
        /// </summary>
        /// <param name="bufferSize">Specify the buffer byte size.</param>
        public BitWriter(int bufferSize)
        {
            this.bytes = new byte[bufferSize];
            this.bitOffset = 0;
        }

        /// <summary>
        /// Gets a copy byte array which contains the current written byte.
        /// </summary>
        public byte[] getBytes() {
            if (this.bitOffset % 8 != 0)
            {
                throw new RuntimeException("BitWriter:Bytes, Cannot get the current bytes because the last byte is not written completely.");
            }

            int retByteLength = this.bitOffset / 8;
            byte[] retByteArray = Arrays.copyOfRange(this.bytes, 0, retByteLength);
            return retByteArray;
        }

        public List<Byte> getByteList() {
            byte[] bytes = getBytes();
            List<Byte> byteList = new ArrayList<>(bytes.length);
            for (byte aByte : bytes) {
                byteList.add(aByte);
            }
            return byteList;
        }

        /// <summary>
        /// Append a specified Unit64 type value into the buffer with the specified bit length.
        /// </summary>
        /// <param name="value">Specify the value which needs to be appended.</param>
        /// <param name="length">Specify the bit length which the value will occupy in the buffer.</param>
        public void AppendUInt64(long value, int length)
        {
            byte[] convertedBytes = LittleEndianBitConverter.GetBytes(value);
            this.SetBytes(convertedBytes, length);
        }

        /// <summary>
        /// Append a specified Unit32 type value into the buffer with the specified bit length.
        /// </summary>
        /// <param name="value">Specify the value which needs to be appended.</param>
        /// <param name="length">Specify the bit length which the value will occupy in the buffer.</param>
        public void AppendUInit32(int value, int length)
        {
            byte[] convertedBytes = LittleEndianBitConverter.GetBytes(value);
            this.SetBytes(convertedBytes, length);
        }

        /// <summary>
        /// Append a specified Init32 type value into the buffer with the specified bit length.
        /// </summary>
        /// <param name="value">Specify the value which needs to be appended.</param>
        /// <param name="length">Specify the bit length which the value will occupy in the buffer.</param>
        public void AppendInit32(int value, int length)
        {
            byte[] convertedBytes = LittleEndianBitConverter.GetBytes(value);
            this.SetBytes(convertedBytes, length);
        }

        /// <summary>
        /// Append a specified GUID value into the buffer.
        /// </summary>
        /// <param name="value">Specify the GUID value.</param>
        public void AppendGUID(UUID value)
        {
            this.SetBytes(UuidUtils.asBytes(value), 128);
        }

        /// <summary>
        /// Write the specified byte array into the buffer from the current position with the specified bit length.
        /// </summary>
        /// <param name="needWrittenBytes">Specify the needed written byte array.</param>
        /// <param name="length">Specify the bit length which the byte array will occupy in the buffer.</param>
        private void SetBytes(byte[] needWrittenBytes, int length)
        {
            for (int i = 0; i < length; i++)
            {
                if (Bit.IsBitSet(needWrittenBytes, i))
                {
                    Bit.SetBit(this.bytes, this.bitOffset++);
                }
                else
                {
                    Bit.ClearBit(this.bytes, this.bitOffset++);
                }
            }
        }
    }