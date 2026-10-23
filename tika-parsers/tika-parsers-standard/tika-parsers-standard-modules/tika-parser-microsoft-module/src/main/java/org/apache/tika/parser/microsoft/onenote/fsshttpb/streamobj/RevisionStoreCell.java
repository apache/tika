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

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;

/**
 * The revision store content of a single cell (object space), together with the root object
 * declarations of its current revision. The object groups are ordered from the oldest revision
 * to the newest.
 */
public class RevisionStoreCell {
    public CellID cellID;
    public List<RevisionStoreObjectGroup> objectGroups = new ArrayList<>();
    /**
     * The effective root object declarations of the cell's current revision, i.e. for each
     * root role the declaration made by the most recent revision in the base revision chain.
     */
    public List<RevisionManifestRootDeclare> rootDeclares = new ArrayList<>();
}
