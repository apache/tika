/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft.fsshttpb.streamobj.chunking;

import static org.apache.tika.parser.microsoft.fsshttpb.unsigned.Unsigned.ubyte;
import static org.apache.tika.parser.microsoft.fsshttpb.unsigned.Unsigned.uint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.LeafNodeObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.SignatureObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.fsshttpb.unsigned.UInteger;
import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to process RDC analysis chunking
 */
public class RDCAnalysisChunking extends AbstractChunking {
    private static final Logger LOGGER = LoggerFactory.getLogger(RDCAnalysisChunking.class);

    /**
     * The max chunk size in RDC analysis chunking.
     */
    private int maxChunkSize = 65535;

    /**
     * Initializes a new instance of the <see cref="RDCAnalysisChunking"/> class
     *
     * @param fileContent The content of the file.
     */
    public RDCAnalysisChunking(byte[] fileContent) {
        super(fileContent);
    }

    /**
     * This method is used to chunk the file data.
     *
     * @return A list of LeafNodeObjectData.
     */
    @Override
    public List<LeafNodeObject> Chunking() {
        int horizon = 16384;
        List<LeafNodeObject> list = new ArrayList<>();
        int inputLength = FileContent.length;

        if (inputLength <= 0) {
            throw new RuntimeException("Cannot support the length less than 0");
        } else if (inputLength <= horizon) {
            list.add(this.GetChunk(0, (int) inputLength));
            return list;
        }

        long chunkStart = 0;
        UInteger[] hashValues = this.GetHashValues();

        while (chunkStart + 1 < inputLength) {
            long chunkEndMax = Math.min(chunkStart + this.maxChunkSize, inputLength);
            long chunkEnd = (int) chunkEndMax;

            for (long n = chunkStart; n < chunkEndMax; n++) {
                boolean isBoundary = true;

                if (n == chunkStart) {
                    n = chunkStart + horizon;
                }

                if (n - chunkStart == this.maxChunkSize) {
                    break;
                }

                long end = Math.min(n + horizon, chunkEndMax);
                for (long i = n - horizon; i < end; i++) {
                    if (i != n && hashValues[(int) n].intValue() <= hashValues[(int) i].intValue()) {
                        isBoundary = false;
                        break;
                    }
                }

                if (!isBoundary) {
                    continue;
                }

                if (n + horizon > inputLength) {
                    n = chunkEndMax;
                    continue;
                }

                if ((n - (n % horizon) + (2 * horizon)) > inputLength) {
                    continue;
                }

                if (inputLength % horizon == 0 &&
                        ((int) chunkStart - ((int) chunkStart % horizon) + (2 * horizon)) == inputLength) {
                    continue;
                }

                chunkEnd = n;
                break;
            }

            list.add(this.GetChunk(chunkStart, chunkEnd));
            chunkStart = chunkEnd;
        }

        return list;
    }

    /**
     * Get a chunk with the input bytes.
     *
     * @param chunkStart The start index of the chunk.
     * @param chunkEnd   The end index of the chunk.
     * @return An LeafNodeObjectData which contains a chunk.
     */
    private LeafNodeObject GetChunk(long chunkStart, long chunkEnd) {
        if (chunkEnd <= chunkStart || (chunkEnd - chunkStart > this.maxChunkSize) || chunkStart > Integer.MAX_VALUE) {
            throw new RuntimeException("ChunkStart out of range");
        }

        byte[] temp = Arrays.copyOfRange(this.FileContent, (int) chunkStart, (int) (chunkEnd - chunkStart));


        SignatureObject signature = new SignatureObject();
        signature.SignatureData = new BinaryItem(ByteUtil.toListOfByte(temp));

//            RDCSignatureGenerator generator = new RDCSignatureGenerator();
//            signatureBytes = generator.ComputeHash(temp);
//
//            SignatureObject signature = new SignatureObject();
//            signature.SignatureData = new BinaryItem(signatureBytes);

        return new LeafNodeObject.IntermediateNodeObjectBuilder().Build(temp, signature);
    }

    /**
     * Compute the hash value with the file content.
     *
     * @return The array of hash value.
     */
    private UInteger[] GetHashValues() {
        int hashWindowSize = 48;
        UInteger[] hashValues = new UInteger[this.FileContent.length];
        int shiftAmount = this.GetShiftAmount(hashWindowSize);
        int i = 0;

        int[] lookupTable =
                {
                        0x5e3f7c48, 0x796a0d2b, 0xbecd4e32, 0x6f16159c,
                        0x687312bc, 0x12a6f30a, 0x8fca2662, 0x79b83d14,
                        0x3fab3f30, 0x984d6ca2, 0x4df5fe6c, 0x4acd3196,
                        0x6245ad21, 0x3a15e5ba, 0x90db6499, 0x05aacb6b,
                        0x791cf724, 0x504cd910, 0x98093570, 0x090392df,
                        0xf193e5b8, 0x42023c5b, 0x80a95c6a, 0x11e676be,
                        0xc70f2117, 0xeed4587f, 0x6479e9bd, 0x1b0c427c,
                        0x410486ba, 0x30f5b837, 0xf957d307, 0x1535f121,
                        0xabe45e90, 0x7a1ab8f0, 0x1c6887e4, 0x4170b7ba,
                        0x8b491bed, 0x5c920e73, 0x1b1ed791, 0x7a0ed482,
                        0xcce86619, 0x45dc7290, 0x57e71362, 0x2e24f01c,
                        0x0a0637f3, 0x0e8c5565, 0x15944012, 0x34f7eeea,
                        0xbc628141, 0x1e200874, 0xe9244379, 0x3e63aeca,
                        0x7a3b3cce, 0x73f8a245, 0xd734e215, 0x834fa434,
                        0xf96a0904, 0xfb39a424, 0x0bfa963a, 0x9b236ee2,
                        0xa2131005, 0x3eb70acf, 0x2907bcd8, 0x3f685f3a,
                        0x3765fd37, 0x1c1c34d2, 0x03a95179, 0x024be6c3,
                        0x06128960, 0x844e7490, 0xe2b371a3, 0x3382909c,
                        0x3d519a77, 0x90971ec9, 0x6ea745e5, 0x490b3a5c,
                        0x7f3916f7, 0xbc150351, 0x241a7ba0, 0xec93c2bb,
                        0x6c7083aa, 0xf3937751, 0xe6aa1df1, 0x129fc001,
                        0xb90709b9, 0x7e59a4fc, 0x4509e58a, 0x8a93ed43,
                        0x6934ce62, 0x8ec6af1a, 0xf36581a9, 0x53d01d93,
                        0xb34eef69, 0x08494a84, 0x0f6dff34, 0x74729aa3,
                        0x48b5475f, 0xb986dc84, 0xd0424c8d, 0xb72ad089,
                        0x0adbbdb8, 0x824fdbe8, 0x99ad1058, 0x98faec38,
                        0xe746242b, 0x2b7ee7fc, 0x2e151fa7, 0x6413270f,
                        0x68ed7239, 0x7729e2d3, 0x5697b3a5, 0x0b90a6c3,
                        0xdf7cefcf, 0xded46a48, 0x46956888, 0xb3bb6dc4,
                        0xe987578f, 0xf82e74b7, 0xc8eeeba4, 0xdd960ff9,
                        0x482ed28d, 0x4f343078, 0x563ab8a4, 0x3ec7aa0d,
                        0x2481d448, 0x5fe98704, 0x5aafc580, 0x841d81ec,
                        0xae7fe8fd, 0x6b31ccb6, 0x911ebdd4, 0x75f4703d,
                        0xe6855a0f, 0x6184b42e, 0x147a4a95, 0x39528e48,
                        0xe975b416, 0x3cba13d3, 0x1e23e544, 0xf7955286,
                        0xa5f96b7f, 0xaaa697aa, 0x29e794e3, 0x87628c09,
                        0xfeebf5f1, 0xf8b070cd, 0xe361b627, 0x8c7a8682,
                        0x69cab331, 0xca867ad1, 0xd0151a96, 0xfc19a6b9,
                        0x6d7439e7, 0x64cd62ac, 0x4a650747, 0x9ddbfa28,
                        0x337c8bed, 0xf12a6860, 0x3767ffd3, 0x13559ced,
                        0x71ac2011, 0xc11dc687, 0x260b7105, 0xc13bca0c,
                        0xcd0af893, 0x793b54e6, 0x89d27fc3, 0xc6bd1c88,
                        0xe3337313, 0x387bc671, 0x61280de4, 0x76941a36,
                        0xaa52a2b9, 0x6d7cb52c, 0x18ff4d70, 0x8987cf38,
                        0x306e47ed, 0xf7df8135, 0x18a8e024, 0xc9eb085f,
                        0xc1a7c769, 0xd5667a12, 0x9c8be93a, 0x028781b1,
                        0x6213dada, 0x07fef4f5, 0x5e6bf91d, 0x469ea798,
                        0xb9654a37, 0x1cb5e74e, 0x525d502d, 0xe805ec68,
                        0xdd8c4320, 0x7890848f, 0x61e59c8e, 0x1d99f9ef,
                        0x25b60b20, 0x2f198088, 0xe01b6926, 0xffa4917f,
                        0xb2fa0f22, 0xee8ac924, 0x18a1c5a7, 0xb76d8d7f,
                        0x88ad5e0d, 0x7b3fb12b, 0xc8a91add, 0x762a6f4e,
                        0x056fad31, 0xebecfab8, 0xea54cd17, 0x71f5af9f,
                        0xfaececa1, 0x08a52f4d, 0xbb5efebe, 0x5bcb04c2,
                        0xcb2530b0, 0x01bb862b, 0xbb5d54f0, 0x404deb4b,
                        0x038658bd, 0x09399005, 0xddd862c8, 0x8985776f,
                        0xcfcfd717, 0xbec756cb, 0x52aecc5a, 0x09ac3f62,
                        0x62c1c6fb, 0x76cc3221, 0xcde6d028, 0x844d9291,
                        0xc143eeac, 0x0ea5e772, 0x8855456e, 0xeb03a426,
                        0x3398475d, 0x73dc8107, 0x681605d0, 0xd18b6264,
                        0x934e43eb, 0x59e76d21, 0xd3ce2b77, 0x4ccfee1c,
                        0x2f4af76d, 0x8b12a309, 0x849bb415, 0xf45ad809,
                        0xc7bccae7, 0xac891c35, 0x59db2274, 0xbcd71393,
                        0x2c9b1705, 0xcb536a69, 0xb2800f00, 0x111313fc
                };

        while (i < this.FileContent.length) {
            UInteger hashValue = i == 0 ? uint(0) : hashValues[i - 1];
            int trailingEdgeData =
                    i < hashWindowSize ? ubyte(0).intValue() : ubyte(this.FileContent[i - hashWindowSize]).intValue();
            int leadingEdgeData = ubyte(this.FileContent[i]).intValue();
            UInteger val = hashValue.xor(uint(lookupTable[trailingEdgeData])).xor(uint(lookupTable[leadingEdgeData]));
            hashValues[i] = val.leftShift(2).inclusiveOr(val.rightShift(Integer.SIZE - shiftAmount));
            i++;
        }

        return hashValues;
    }

    /**
     * Get the shift amount value.
     *
     * @param hashWindowSize The value of hash window size.
     * @return The value of shift amount.
     */
    private int GetShiftAmount(int hashWindowSize) {
        int shiftAmount = 1;
        int i = 32;

        while (i > 0 && hashWindowSize % i != 0) {
            shiftAmount *= 2;
            i /= 2;
        }

        shiftAmount = shiftAmount % 32;

        return shiftAmount;
    }
}