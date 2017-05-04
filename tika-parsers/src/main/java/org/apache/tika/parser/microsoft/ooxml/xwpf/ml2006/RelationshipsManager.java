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

package org.apache.tika.parser.microsoft.ooxml.xwpf.ml2006;


import java.util.HashMap;
import java.util.Map;

import org.apache.poi.openxml4j.opc.TargetMode;

class RelationshipsManager {

    Map<String, Map<String, Relationship>> map = new HashMap<>();

    public void addRelationship(String relsFileName, String id, String type, String target, TargetMode targetMode) {
        String packageName = convertRelsFileNameToPackageName(relsFileName);
        Map<String, Relationship> thisPackageRels = map.get(packageName);
        if (thisPackageRels == null) {
            thisPackageRels = new HashMap<>();
        }
        thisPackageRels.put(id, new Relationship(type, target, targetMode));
        map.put(packageName, thisPackageRels);
    }

    public Relationship getRelationship(String packageName, String id) {
        Map<String, Relationship> thisPackageRels = map.get(packageName);
        if (thisPackageRels != null) {
            return thisPackageRels.get(id);
        }
        return null;
    }

    private String convertRelsFileNameToPackageName(String relsFileName) {
        if ("/_rels/.rels".equals(relsFileName)) {
            return "/";
        }

        String tmp = relsFileName;
        tmp = tmp.replaceFirst("\\/_rels\\/", "/");
        tmp = tmp.replaceFirst(".rels\\Z", "");
        return tmp;
    }
}
