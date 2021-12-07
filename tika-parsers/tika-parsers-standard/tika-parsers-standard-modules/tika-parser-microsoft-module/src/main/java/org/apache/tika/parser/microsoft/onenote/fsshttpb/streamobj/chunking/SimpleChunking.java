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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.chunking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.LeafNodeObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.SignatureObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class SimpleChunking extends AbstractChunking {
    /**
     * Initializes a new instance of the SimpleChunking class
     *
     * @param fileContent The content of the file.
     */
    public SimpleChunking(byte[] fileContent) {
        super(fileContent);
    }

    /**
     * This method is used to chunk the file data.
     *
     * @return A list of LeafNodeObjectData.
     */
    @Override
    public List<LeafNodeObject> chunking() throws TikaException {
        int maxChunkSize = 1 * 1024 * 1024;
        java.util.List<LeafNodeObject> list = new ArrayList<>();
        LeafNodeObject.IntermediateNodeObjectBuilder builder =
                new LeafNodeObject.IntermediateNodeObjectBuilder();
        int chunkStart = 0;

        if (this.fileContent.length <= maxChunkSize) {
            list.add(builder.Build(this.fileContent, this.getSignature(this.fileContent)));

            return list;
        }

        while (chunkStart < this.fileContent.length) {
            int chunkLength = chunkStart + maxChunkSize >= this.fileContent.length ?
                    this.fileContent.length - chunkStart : maxChunkSize;
            byte[] temp = Arrays.copyOfRange(this.fileContent, chunkStart, chunkLength);
            list.add(builder.Build(temp, this.getSignature(temp)));
            chunkStart += chunkLength;
        }

        return list;
    }

    /**
     * Get signature for the chunk.
     *
     * @param array The data of the chunk.
     * @return The signature instance.
     */
    private SignatureObject getSignature(byte[] array) throws TikaException {
        if (this.fileContent.length <= 250 * 1024 * 1024) {
            byte[] temp = DigestUtils.sha1(array);

            SignatureObject signature = new SignatureObject();
            signature.signatureData = new BinaryItem(ByteUtil.toListOfByte(temp));
            return signature;
        } else {
            throw new TikaException(
                    "When the file size is larger than 250MB, the signature method is not implemented.");
        }
    }
}
