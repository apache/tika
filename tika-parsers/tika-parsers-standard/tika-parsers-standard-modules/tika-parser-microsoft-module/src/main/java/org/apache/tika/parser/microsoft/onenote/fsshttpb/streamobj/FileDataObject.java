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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj;

public class FileDataObject {
    public ObjectGroupObjectBLOBDataDeclaration objectDataBLOBDeclaration;
    public ObjectGroupObjectDataBLOBReference objectDataBLOBReference;
    public DataElement objectDataBLOBDataElement;

    public byte[] getData() {
        ObjectDataBLOB blob = getBlob();
        return blob == null ? null : blob.getData();
    }

    public int getDataLength() {
        ObjectDataBLOB blob = getBlob();
        return blob == null ? 0 : blob.getDataLength();
    }

    public boolean hasData() {
        return getDataLength() > 0;
    }

    /** @return the referenced BLOB, which several file data objects may share; or null */
    private ObjectDataBLOB getBlob() {
        if (objectDataBLOBDataElement != null &&
                objectDataBLOBDataElement.data instanceof ObjectDataBLOBDataElementData) {
            return ((ObjectDataBLOBDataElementData) objectDataBLOBDataElement.data).objectDataBLOB;
        }
        return null;
    }
}
