package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.tika.parser.microsoft.onenote.ExtendedGUID;

public class AdapterHelper
    {
        /// <summary>
        /// This method is used to read the Guid for byte array.
        /// </summary>
        /// <param name="byteArray">The byte array.</param>
        /// <param name="startIndex">The offset of the Guid value.</param>
        /// <returns>Return the value of Guid.</returns>
        public static UUID ReadGuid(byte[] byteArray, int startIndex) {
            byte[] bytes = Arrays.copyOfRange(byteArray, startIndex, startIndex + 16);
            return UUID.nameUUIDFromBytes(bytes);
        }
        /// <summary>
        /// XOR two ExtendedGUID instances.
        /// </summary>
        /// <param name="exGuid1">The first ExtendedGUID instance.</param>
        /// <param name="exGuid2">The second ExtendedGUID instance.</param>
        /// <returns>Returns the result of XOR two ExtendedGUID instances.</returns>
        public static ExGuid XORExtendedGUID(ExtendedGUID exGuid1, ExtendedGUID exGuid2)
        {
            List<Byte> exGuid1Buffer = exGuid1.SerializeToByteList();
            List<Byte> exGuid2Buffer = exGuid2.SerializeToByteList();
            List<Byte> resultBuffer = new ArrayList<>(exGuid1Buffer.size());

            for (int i = 0; i < exGuid1Buffer.size(); i++)
            {
                byte fromExGuid1 = exGuid1Buffer.get(i);
                byte fromExGuid2 = exGuid2Buffer.get(i);
                resultBuffer.set(i, (byte)(fromExGuid1 ^ fromExGuid2));
            }
            ExGuid resultExGuid = new ExGuid();
            resultExGuid.DoDeserializeFromByteArray(ByteUtil.toByteArray(resultBuffer), 0);
            return resultExGuid;
        }
    }