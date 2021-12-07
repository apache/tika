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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic;

import java.io.IOException;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupObjectData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupObjectDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectPropSet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class HeaderCell {
    public ObjectGroupObjectDeclare objectDeclaration;
    public ObjectSpaceObjectPropSet objectData;

    /**
     * Create the instance of Header Cell.
     *
     * @param objectElement The instance of ObjectGroupDataElementData.
     * @return Returns the instacne of HeaderCell.
     */
    public static HeaderCell createInstance(ObjectGroupDataElementData objectElement)
            throws IOException {
        HeaderCell instance = new HeaderCell();

        for (int i = 0; i < objectElement.objectGroupDeclarations.objectDeclarationList.size();
                i++) {
            if (objectElement.objectGroupDeclarations.objectDeclarationList.get(
                    i).objectPartitionID != null &&
                    objectElement.objectGroupDeclarations.objectDeclarationList.get(
                            i).objectPartitionID.getDecodedValue() == 1) {
                instance.objectDeclaration =
                        objectElement.objectGroupDeclarations.objectDeclarationList.get(i);
                ObjectGroupObjectData objectData =
                        objectElement.objectGroupData.objectGroupObjectDataList.get(i);
                instance.objectData = new ObjectSpaceObjectPropSet();
                instance.objectData.doDeserializeFromByteArray(
                        ByteUtil.toByteArray(objectData.data.content), 0);
                break;
            }
        }

        return instance;
    }
}
