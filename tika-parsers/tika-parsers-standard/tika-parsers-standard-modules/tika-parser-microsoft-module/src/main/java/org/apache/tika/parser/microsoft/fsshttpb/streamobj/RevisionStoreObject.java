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

package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.CellIDArray;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGUIDArray;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;


/**
 * The class is used to represent the revision store object.
 */
public class RevisionStoreObject {

    /**
     * Initialize the class.
     */
    public RevisionStoreObject() {

    }

    public ExGuid ObjectID;
    public ExGuid ObjectGroupID;
    public JCIDObject JCID;
    public PropertySetObject PropertySet;
    public org.apache.tika.parser.microsoft.fsshttpb.streamobj.FileDataObject FileDataObject;
    public ExGUIDArray ReferencedObjectID;
    public CellIDArray ReferencedObjectSpacesID;
}