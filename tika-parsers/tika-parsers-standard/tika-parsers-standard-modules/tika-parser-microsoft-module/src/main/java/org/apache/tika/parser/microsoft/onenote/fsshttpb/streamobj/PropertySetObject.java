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

import java.io.IOException;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectPropSet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;


/**
 * This class is used to represent the property set.
 */
public class PropertySetObject {
    public ObjectGroupObjectDeclare objectDeclaration;
    public ObjectSpaceObjectPropSet objectSpaceObjectPropSet;

    /**
     * Construct the PropertySetObject instance.
     *
     * @param objectDeclaration The Object Declaration structure.
     * @param objectData        The Object Data structure.
     */
    public PropertySetObject(ObjectGroupObjectDeclare objectDeclaration,
                             ObjectGroupObjectData objectData) throws IOException {
        this.objectDeclaration = objectDeclaration;
        this.objectSpaceObjectPropSet = new ObjectSpaceObjectPropSet();
        this.objectSpaceObjectPropSet.doDeserializeFromByteArray(
                ByteUtil.toByteArray(objectData.data.content), 0);
    }
}
