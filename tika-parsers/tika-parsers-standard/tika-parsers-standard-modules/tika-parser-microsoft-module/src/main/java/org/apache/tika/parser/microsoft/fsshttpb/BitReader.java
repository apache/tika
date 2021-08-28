package org.apache.tika.parser.microsoft.fsshttpb;    /// <summary>

import java.util.UUID;

/// A class is used to extract values across byte boundaries with arbitrary bit positions.
    /// </summary>
    public class BitReader {
        /// <summary>
        /// A byte array which contains the bytes need to be read.
        /// </summary>
        private byte[] byteArray;

        /// <summary>
        /// A start position which will be not changed in the process of reading.
        /// This value will be used for recording the start position and will be used by the function reset.
        /// </summary>
        private long startPosition;

        /// <summary>
        /// An offset which is used to keep trace for the current read position in bit.
        /// </summary>
        private long offset;

        /// <summary>
        /// The length of the byte Array which contains the byte need to be read.
        /// </summary>
        private long length;

        /// <summary>
        /// Initializes a new instance of the BitReader class with specified bytes buffer and start position in byte.
        /// </summary>
        /// <param name="array">Specify the byte array which contains the bytes need to be read.</param>
        /// <param name="index">Specify the start position in byte.</param>
        public BitReader(byte[] array, int index)
        {
            this.byteArray = array;
            this.offset = ((long)index * 8) - 1;
            this.startPosition = this.offset;
            this.length = (long)array.length * 8;
        }

        public boolean getCurrent() {
            return Bit.IsBitSet(this.byteArray, this.offset);
        }

        /// <summary>
        /// Read specified bit length content as an UInt64 type and increase the bit offset. 
        /// </summary>
        /// <param name="readingLength">Specify the reading bit length.</param>
        /// <returns>Return the UInt64 type value.</returns>
        public long ReadUInt64(int readingLength)
        {
            byte[] uint64Bytes = this.GetBytes(readingLength, 8);
            return LittleEndianBitConverter.ToUInt64(uint64Bytes, 0);
        }

        /// <summary>
        /// Read specified bit length content as an UInt32 type and increase the bit offset with the specified length. 
        /// </summary>
        /// <param name="readingLength">Specify the reading bit length.</param>
        /// <returns>Return the UInt32 type value.</returns>
        public int ReadUInt32(int readingLength)
        {
            byte[] uint32Bytes = this.GetBytes(readingLength, 4);
            return LittleEndianBitConverter.ToUInt32(uint32Bytes, 0);
        }

        /// <summary>
        /// Reading the bytes specified by the byte length.
        /// </summary>
        /// <param name="readingLength">Specify the reading byte length.</param>
        /// <returns>Return the read bytes array.</returns>
        public byte[] ReadBytes(int readingLength)
        {
            byte[] readingByteArray = this.GetBytes(readingLength * 8, readingLength);
            return readingByteArray;
        }

        /// <summary>
        /// Read specified bit length content as an byte type and increase the bit offset with the specified length. 
        /// </summary>
        /// <param name="readingBitLength">Specify the reading bit length.</param>
        /// <returns>Return the byte value.</returns>
        public byte ReadByte(int readingBitLength)
        {
            byte[] readingByteArray = this.GetBytes(readingBitLength, 1);
            return readingByteArray[0];
        }

        /// <summary>
        /// Read specified bit length content as an UInt16 type and increase the bit offset with the specified length. 
        /// </summary>
        /// <param name="readingLength">Specify the reading bit length.</param>
        /// <returns>Return the UInt16 value.</returns>
        public short ReadInt16(int readingLength)
        {
            byte[] uint16Bytes = this.GetBytes(readingLength, 2);
            return LittleEndianBitConverter.ToInt16(uint16Bytes, 0);
        }

        /// <summary>
        /// Read specified bit length content as an Int32 type and increase the bit offset with the specified length. 
        /// </summary>
        /// <param name="readingLength">Specify the reading bit length.</param>
        /// <returns>Return the Int32 type value.</returns>
        public int ReadInt32(int readingLength)
        {
            byte[] uint32Bytes = this.GetBytes(readingLength, 4);
            return LittleEndianBitConverter.ToInt32(uint32Bytes, 0);
        }

        /// <summary>
        /// Read as a GUID from the current offset position and increate the bit offset with 128 bit.
        /// </summary>
        /// <returns>Return the GUID value.</returns>
        public UUID ReadGuid()
        {
            return UUID.nameUUIDFromBytes(this.GetBytes(128, 16));
        }

        /// <summary>
        /// Advances the enumerator to the next bit of the byte array.
        /// </summary>
        /// <returns>true if the enumerator was successfully advanced to the next bit; false if the enumerator has passed the end of the byte array.</returns>
        public boolean MoveNext()
        {
            return ++this.offset < this.length;
        }

        /// <summary>
        /// Assign the internal read buffer to null.
        /// </summary>
        public void Dispose()
        {
            this.byteArray = null;
        }

        /// <summary>
        /// Sets the enumerator to its initial position, which is before the first bit in the byte array.
        /// </summary>
        public void Reset()
        {
            this.offset = this.startPosition;
        }

        /// <summary>
        /// Construct a byte array with specified bit length and the specified the byte array size.
        /// </summary>
        /// <param name="needReadlength">Specify the need read bit length.</param>
        /// <param name="size">Specify the byte array size.</param>
        /// <returns>Returns the constructed byte array.</returns>
        private byte[] GetBytes(int needReadlength, int size)
        {
            byte[] retBytes = new byte[size];
            int i = 0;
            while (i < needReadlength)
            {
                if (!this.MoveNext())
                {
                    throw new RuntimeException("Unexpected to meet the byte array end.");
                }

                if (getCurrent())
                {
                    Bit.SetBit(retBytes, i);
                }
                else
                {
                    Bit.ClearBit(retBytes, i);
                }

                i++;
            }

            return retBytes;
        }
    }
