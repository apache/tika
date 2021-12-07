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

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellIDArray;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGUIDArray;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;


/**
 * The class is used to represent the revision store object.
 */
public class RevisionStoreObject {

    public ExGuid objectID;
    public ExGuid objectGroupID;
    public JCIDObject jcid;
    public PropertySetObject propertySet;
    public org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.FileDataObject
            fileDataObject;
    public ExGUIDArray referencedObjectID;
    public CellIDArray referencedObjectSpacesID;

    /**
     * Initialize the class.
     */
    public RevisionStoreObject() {

    }
}
