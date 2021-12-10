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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Revision {
    Map<Long, GUID> globalId = new HashMap<>();
    List<FileNodePtr> manifestList = new ArrayList<>();
    ExtendedGUID gosid = ExtendedGUID.nil();
    ExtendedGUID dependent = ExtendedGUID.nil();

    public Revision() {

    }

    public Revision(Map<Long, GUID> globalId, List<FileNodePtr> manifestList, ExtendedGUID gosid,
                    ExtendedGUID dependent) {
        this.globalId = globalId;
        this.manifestList = manifestList;
        this.gosid = gosid;
        this.dependent = dependent;
    }

    public Map<Long, GUID> getGlobalId() {
        return globalId;
    }

    public void setGlobalId(Map<Long, GUID> globalId) {
        this.globalId = globalId;
    }

    public List<FileNodePtr> getManifestList() {
        return manifestList;
    }

    public void setManifestList(List<FileNodePtr> manifestList) {
        this.manifestList = manifestList;
    }

    public ExtendedGUID getGosid() {
        return gosid;
    }

    public void setGosid(ExtendedGUID gosid) {
        this.gosid = gosid;
    }

    public ExtendedGUID getDependent() {
        return dependent;
    }

    public void setDependent(ExtendedGUID dependent) {
        this.dependent = dependent;
    }
}
