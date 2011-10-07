/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;

/**
 * General Endian Related Utilties.
 * <p>
 * This class provides static utility methods for input/output operations
 *  on numbers in Big and Little Endian formats.
 * <p>
 * Origin of code: Based on the version in POI
 */
public class EndianUtils {
   /**
    * Get a LE short value from an InputStream
    *
    * @param  stream the InputStream from which the short is to be read
    * @return                              the short (16-bit) value
    * @exception  IOException              will be propagated back to the caller
    * @exception  BufferUnderrunException  if the stream cannot provide enough bytes
    */
   public static short readShortLE(InputStream stream) throws IOException, BufferUnderrunException {
      return (short) readUShortLE(stream);
   }
   /**
    * Get a BE short value from an InputStream
    *
    * @param  stream the InputStream from which the short is to be read
    * @return                              the short (16-bit) value
    * @exception  IOException              will be propagated back to the caller
    * @exception  BufferUnderrunException  if the stream cannot provide enough bytes
    */
   public static short readShortBE(InputStream stream) throws IOException, BufferUnderrunException {
      return (short) readUShortBE(stream);
   }

   public static int readUShortLE(InputStream stream) throws IOException, BufferUnderrunException {
      int ch1 = stream.read();
      int ch2 = stream.read();
      if ((ch1 | ch2) < 0) {
         throw new BufferUnderrunException();
      }
      return (ch2 << 8) + (ch1 << 0);
   }
   public static int readUShortBE(InputStream stream) throws IOException, BufferUnderrunException {
      int ch1 = stream.read();
      int ch2 = stream.read();
      if ((ch1 | ch2) < 0) {
         throw new BufferUnderrunException();
      }
      return (ch1 << 8) + (ch2 << 0);
   }

   /**
    * Get a LE int value from an InputStream
    *
    * @param  stream the InputStream from which the int is to be read
    * @return                              the int (32-bit) value
    * @exception  IOException              will be propagated back to the caller
    * @exception  BufferUnderrunException  if the stream cannot provide enough bytes
    */
   public static int readIntLE(InputStream stream) throws IOException, BufferUnderrunException {
      int ch1 = stream.read();
      int ch2 = stream.read();
      int ch3 = stream.read();
      int ch4 = stream.read();
      if ((ch1 | ch2 | ch3 | ch4) < 0) {
         throw new BufferUnderrunException();
      }
      return (ch4 << 24) + (ch3<<16) + (ch2 << 8) + (ch1 << 0);
   }
   /**
    * Get a BE int value from an InputStream
    *
    * @param  stream the InputStream from which the int is to be read
    * @return                              the int (32-bit) value
    * @exception  IOException              will be propagated back to the caller
    * @exception  BufferUnderrunException  if the stream cannot provide enough bytes
    */
   public static int readIntBE(InputStream stream) throws IOException, BufferUnderrunException {
      int ch1 = stream.read();
      int ch2 = stream.read();
      int ch3 = stream.read();
      int ch4 = stream.read();
      if ((ch1 | ch2 | ch3 | ch4) < 0) {
         throw new BufferUnderrunException();
      }
      return (ch1 << 24) + (ch2<<16) + (ch3 << 8) + (ch4 << 0);
   }

   /**
    * Get a LE long value from an InputStream
    *
    * @param  stream the InputStream from which the long is to be read
    * @return                              the long (64-bit) value
    * @exception  IOException              will be propagated back to the caller
    * @exception  BufferUnderrunException  if the stream cannot provide enough bytes
    */
   public static long readLongLE(InputStream stream) throws IOException, BufferUnderrunException {
      int ch1 = stream.read();
      int ch2 = stream.read();
      int ch3 = stream.read();
      int ch4 = stream.read();
      int ch5 = stream.read();
      int ch6 = stream.read();
      int ch7 = stream.read();
      int ch8 = stream.read();
      if ((ch1 | ch2 | ch3 | ch4 | ch5 | ch6 | ch7 | ch8) < 0) {
         throw new BufferUnderrunException();
      }

      return
      ((long)ch8 << 56) +
      ((long)ch7 << 48) +
      ((long)ch6 << 40) +
      ((long)ch5 << 32) +
      ((long)ch4 << 24) + // cast to long to preserve bit 31 (sign bit for ints)
      (ch3 << 16) +
      (ch2 <<  8) +
      (ch1 <<  0);
   }
   /**
    * Get a NE long value from an InputStream
    *
    * @param  stream the InputStream from which the long is to be read
    * @return                              the long (64-bit) value
    * @exception  IOException              will be propagated back to the caller
    * @exception  BufferUnderrunException  if the stream cannot provide enough bytes
    */
   public static long readLongBE(InputStream stream) throws IOException, BufferUnderrunException {
      int ch1 = stream.read();
      int ch2 = stream.read();
      int ch3 = stream.read();
      int ch4 = stream.read();
      int ch5 = stream.read();
      int ch6 = stream.read();
      int ch7 = stream.read();
      int ch8 = stream.read();
      if ((ch1 | ch2 | ch3 | ch4 | ch5 | ch6 | ch7 | ch8) < 0) {
         throw new BufferUnderrunException();
      }

      return
      ((long)ch1 << 56) +
      ((long)ch2 << 48) +
      ((long)ch3 << 40) +
      ((long)ch4 << 32) +
      ((long)ch5 << 24) + // cast to long to preserve bit 31 (sign bit for ints)
      (ch6 << 16) +
      (ch7 <<  8) +
      (ch8 <<  0);
   }
   
   
   /**
    * Get a LE short value from the beginning of a byte array
    *
    *@param  data  the byte array
    *@return       the short (16-bit) value
    */
   public static short getShortLE(byte[] data) {
      return getShortLE(data, 0);
   }
   /**
    * Get a LE short value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the short (16-bit) value
    */
   public static short getShortLE(byte[] data, int offset) {
      return (short)getUShortLE(data, offset);
   }

   /**
    * Get a LE unsigned short value from the beginning of a byte array
    *
    *@param  data  the byte array
    *@return       the unsigned short (16-bit) value in an int
    */
   public static int getUShortLE(byte[] data) {
      return getUShortLE(data, 0);
   }
   /**
    * Get a LE unsigned short value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the unsigned short (16-bit) value in an integer
    */
   public static int getUShortLE(byte[] data, int offset) {
      int b0 = data[offset] & 0xFF;
      int b1 = data[offset+1] & 0xFF;
      return (b1 << 8) + (b0 << 0);
   }
   
   /**
    * Get a BE short value from the beginning of a byte array
    *
    *@param  data  the byte array
    *@return       the short (16-bit) value
    */
   public static short getShortBE(byte[] data) {
      return getShortBE(data, 0);
   }
   /**
    * Get a BE short value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the short (16-bit) value
    */
   public static short getShortBE(byte[] data, int offset) {
      return (short)getUShortBE(data, offset);
   }

   /**
    * Get a BE unsigned short value from the beginning of a byte array
    *
    *@param  data  the byte array
    *@return       the unsigned short (16-bit) value in an int
    */
   public static int getUShortBE(byte[] data) {
      return getUShortBE(data, 0);
   }
   /**
    * Get a BE unsigned short value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the unsigned short (16-bit) value in an integer
    */
   public static int getUShortBE(byte[] data, int offset) {
      int b0 = data[offset] & 0xFF;
      int b1 = data[offset+1] & 0xFF;
      return (b0 << 8) + (b1 << 0);
   }

   /**
    * Get a LE int value from the beginning of a byte array
    *
    *@param  data  the byte array
    *@return the int (32-bit) value
    */
   public static int getIntLE(byte[] data) {
       return getIntLE(data, 0);
   }
   /**
    * Get a LE int value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the int (32-bit) value
    */
   public static int getIntLE(byte[] data, int offset) {
       int i=offset;
       int b0 = data[i++] & 0xFF;
       int b1 = data[i++] & 0xFF;
       int b2 = data[i++] & 0xFF;
       int b3 = data[i++] & 0xFF;
       return (b3 << 24) + (b2 << 16) + (b1 << 8) + (b0 << 0);
   }

   /**
    * Get a BE int value from the beginning of a byte array
    *
    *@param  data  the byte array
    *@return the int (32-bit) value
    */
   public static int getIntBE(byte[] data) {
       return getIntBE(data, 0);
   }
   /**
    * Get a BE int value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the int (32-bit) value
    */
   public static int getIntBE(byte[] data, int offset) {
       int i=offset;
       int b0 = data[i++] & 0xFF;
       int b1 = data[i++] & 0xFF;
       int b2 = data[i++] & 0xFF;
       int b3 = data[i++] & 0xFF;
       return (b0 << 24) + (b1 << 16) + (b2 << 8) + (b3 << 0);
   }

   /**
    * Get a LE unsigned int value from a byte array
    *
    *@param  data    the byte array
    *@return         the unsigned int (32-bit) value in a long
    */
   public static long getUIntLE(byte[] data) {
       return getUIntLE(data,0);
   }
   /**
    * Get a LE unsigned int value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the unsigned int (32-bit) value in a long
    */
   public static long getUIntLE(byte[] data, int offset) {
       long retNum = getIntLE(data, offset);
       return retNum & 0x00FFFFFFFFl;
   }

   /**
    * Get a BE unsigned int value from a byte array
    *
    *@param  data    the byte array
    *@return         the unsigned int (32-bit) value in a long
    */
   public static long getUIntBE(byte[] data) {
       return getUIntBE(data,0);
   }
   /**
    * Get a BE unsigned int value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the unsigned int (32-bit) value in a long
    */
   public static long getUIntBE(byte[] data, int offset) {
       long retNum = getIntBE(data, offset);
       return retNum & 0x00FFFFFFFFl;
   }

   /**
    * Get a LE long value from a byte array
    *
    *@param  data    the byte array
    *@param  offset  a starting offset into the byte array
    *@return         the long (64-bit) value
    */
   public static long getLongLE(byte[] data, int offset) {
      long result = 0;

      for (int j = offset + LONG_SIZE - 1; j >= offset; j--) {
         result <<= 8;
         result |= 0xff & data[j];
      }
      return result;
   }
   private static final int LONG_SIZE = 8;

   
   /**
    *  Convert an 'unsigned' byte to an integer. ie, don't carry across the
    *  sign.
    *
    * @param  b  Description of the Parameter
    * @return    Description of the Return Value
    */
   public static int ubyteToInt(byte b) {
      return b & 0xFF;
   }

   /**
    * get the unsigned value of a byte.
    * 
    * @param data
    *            the byte array.
    * @param offset
    *            a starting offset into the byte array.
    * @return the unsigned value of the byte as a 16 bit short
    */
   public static short getUByte( byte[] data, int offset )
   {
      return (short) ( data[offset] & 0xFF );
   }
   
   
   public static class BufferUnderrunException extends TikaException {
      private static final long serialVersionUID = 8358288231138076276L;
      public BufferUnderrunException() {
         super("Insufficient data left in stream for required read");
      }
   }
}
