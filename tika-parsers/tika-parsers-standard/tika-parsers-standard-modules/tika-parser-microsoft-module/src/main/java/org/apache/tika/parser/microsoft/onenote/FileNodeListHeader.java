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

package org.apache.tika.parser.microsoft.onenote;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.StringUtils;

class FileNodeListHeader {
    public static final long UNIT_MAGIC_CONSTANT = 0xA4567AB1F5F7F4C4L;
    long position;
    long fileNodeListId;
    long nFragmentSequence;

    /**
     * The FileNodeListHeader structure specifies the beginning of a FileNodeListFragment structure.
     *
     * @param position          Position of the file where this header starts.
     * @param uintMagic         An unsigned integer; MUST be "0xA4567AB1F5F7F4C4"
     * @param fileNodeListId    An unsigned integer that specifies the identity of
     *                          the file node list this fragment belongs to. MUST be equal to or
     *                          greater than 0x00000010. The pair of
     *                          FileNodeListID and nFragmentSequence fields MUST be unique
     *                          relative to other FileNodeListFragment structures in the file.
     * @param nFragmentSequence An unsigned integer that specifies the index of the fragment in the
     *                          file node list containing the fragment. The nFragmentSequence
     *                          field of the first fragment in a given file node list MUST be 0
     *                          and the nFragmentSequence fields of all subsequent fragments in
     *                          this list MUST be sequential.
     */
    public FileNodeListHeader(long position, long uintMagic, long fileNodeListId,
                              long nFragmentSequence) throws TikaException {
        if (uintMagic != UNIT_MAGIC_CONSTANT) {
            throw new TikaException(
                    "unitMagic must always be: 0x" + Long.toHexString(UNIT_MAGIC_CONSTANT));
        }
        this.position = position;
        this.fileNodeListId = fileNodeListId;
        if (fileNodeListId < 0x00000010) {
            throw new TikaException("FileNodeListHeader.fileNodeListId MUST be equal " +
                    "to or greater than 0x00000010");
        }
        this.nFragmentSequence = nFragmentSequence;
    }

    public long getFileNodeListId() {
        return fileNodeListId;
    }

    public FileNodeListHeader setFileNodeListId(long fileNodeListId) {
        this.fileNodeListId = fileNodeListId;
        return this;
    }

    public long getnFragmentSequence() {
        return nFragmentSequence;
    }

    public FileNodeListHeader setnFragmentSequence(long nFragmentSequence) {
        this.nFragmentSequence = nFragmentSequence;
        return this;
    }

    public long getPosition() {
        return position;
    }

    public FileNodeListHeader setPosition(long position) {
        this.position = position;
        return this;
    }

    public String getPositionHex() {
        return "0x" + StringUtils.leftPad(Long.toHexString(position), 8, '0');
    }

    @Override
    public String toString() {
        return "FileNodeListHeader{" + "position=" + "0x" +
                StringUtils.leftPad(Long.toHexString(position), 8, '0') + ", fileNodeListId=" +
                fileNodeListId + ", nFragmentSequence=" + nFragmentSequence + '}';
    }
}
