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

/**
 * The JCID structure specifies the type of object and the type of data the object contains.
 * A JCID structure can be considered to be an unsigned integer of size four bytes as specified
 * by property set and
 * file data object.
 *
 * <pre>[0,15] - the index</pre>
 * <pre>16 - A</pre>
 * <pre>17 - B</pre>
 * <pre>18 - C</pre>
 * <pre>19 - D</pre>
 * <pre>20 - E</pre>
 * <pre>21 - 31 = reserved</pre>
 * <p>
 * index (2 bytes): An unsigned integer that specifies the type of object.
 * <p>
 * A - IsBinary (1 bit): Specifies whether the object contains encryption data  transmitted over
 * the File Synchronization via SOAP over HTTP Protocol, as specified in [MS-FSSHTTP].
 * <p>
 * B - IsPropertySet (1 bit): Specifies whether the object contains a property set.
 * <p>
 * C - IsGraphNode (1 bit): Undefined and MUST be ignored.
 * <p>
 * D - IsFileData (1 bit): Specifies whether the object is a file data object. If the value of
 * IsFileData is "true", then the values of the IsBinary, IsPropertySet, IsGraphNode, and
 * IsReadOnly fields MUST all be false.
 * <p>
 * E - IsReadOnly (1 bit): Specifies whether the object's data MUST NOT be changed when the
 * object is revised.
 * <p>
 * reserved (11 bits): MUST be zero, and MUST be ignored.
 */
class JCID {
    long jcid;
    long index;
    boolean isBinary;
    boolean isPropertySet;
    boolean isGraphNode;
    boolean isFileData;
    boolean isReadOnly;

    /**
     * If the value of the JCID.IsPropertySet field is "true" or if only JCID.index is specified,
     * then the data for the Object Space Object structure MUST be an ObjectSpaceObjectPropSet
     * structure.
     *
     * @return true if is ObjectSpaceObjectPropSet. false otherwise.
     */
    public boolean isObjectSpaceObjectPropSet() {
        return isPropertySet ||
                !isBinary && !isGraphNode && !isFileData && !isReadOnly && index > 0;
    }

    public void loadFrom32BitIndex(long fullIndex) throws TikaException {
        jcid = fullIndex;
        index = fullIndex & 0xffff;
        isBinary = ((fullIndex >> 16) & 1) == 1;
        isPropertySet = ((fullIndex >> 17) & 1) == 1;
        isGraphNode = ((fullIndex >> 18) & 1) == 1;
        isFileData = ((fullIndex >> 19) & 1) == 1;
        isReadOnly = ((fullIndex >> 20) & 1) == 1;
        if ((fullIndex >> 21) != 0) {
            throw new TikaException("RESERVED_NONZERO");
        }
    }

    @Override
    public String toString() {
        return "JCID{" + "jcid=" + JCIDPropertySetTypeEnum.of(jcid) + " (0x" +
                Long.toHexString(jcid) + ")" + ", index=" + index + ", isBinary=" + isBinary +
                ", isPropertySet=" + isPropertySet + ", isGraphNode=" + isGraphNode +
                ", isFileData=" + isFileData + ", isReadOnly=" + isReadOnly + '}';
    }

    public long getJcid() {
        return jcid;
    }

    public void setJcid(long jcid) {
        this.jcid = jcid;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public boolean isBinary() {
        return isBinary;
    }

    public void setBinary(boolean binary) {
        isBinary = binary;
    }

    public boolean isPropertySet() {
        return isPropertySet;
    }

    public void setPropertySet(boolean propertySet) {
        isPropertySet = propertySet;
    }

    public boolean isGraphNode() {
        return isGraphNode;
    }

    public void setGraphNode(boolean graphNode) {
        isGraphNode = graphNode;
    }

    public boolean isFileData() {
        return isFileData;
    }

    public void setFileData(boolean fileData) {
        isFileData = fileData;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean readOnly) {
        isReadOnly = readOnly;
    }
}
