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

import org.apache.commons.lang3.tuple.Pair;

class OneNoteDocument {
    OneNoteHeader header;
    List<ExtendedGUID> revisionListOrder = new ArrayList<>();
    Map<ExtendedGUID, Revision> revisionMap = new HashMap<>();
    Map<ExtendedGUID, FileNodePtr> revisionManifestLists = new HashMap<>();
    Map<ExtendedGUID, FileChunkReference> guidToRef = new HashMap<>();
    Map<ExtendedGUID, FileNodePtr> guidToObject = new HashMap<>();

    Map<ExtendedGUID, Pair<Long, ExtendedGUID>> revisionRoleMap = new HashMap<>();
    ExtendedGUID currentRevision = ExtendedGUID.nil();
    FileNodeList root = new FileNodeList();

    public OneNoteDocument() {

    }

    FileChunkReference getAssocGuidToRef(ExtendedGUID guid) {
        return guidToRef.get(guid);
    }

    void setAssocGuidToRef(ExtendedGUID guid, FileChunkReference ref) {
        guidToRef.put(guid, ref);
    }

    void registerRevisionManifestList(ExtendedGUID guid, FileNodePtr ptr) {
        revisionManifestLists.put(guid, ptr);
        revisionListOrder.add(guid);
    }

    void registerRevisionManifest(FileNode fn) {
        revisionMap.putIfAbsent(fn.gosid, new Revision());
        Revision toModify = revisionMap.get(fn.gosid);
        toModify.gosid = fn.gosid;
        toModify.dependent = fn.subType.revisionManifest.ridDependent;
        currentRevision = fn.gosid;
    }

    public void registerAdditionalRevisionRole(ExtendedGUID gosid, long revisionRole,
                                               ExtendedGUID gctxid) {
        revisionRoleMap.put(gosid, Pair.of(revisionRole, gctxid));
    }

    public List<ExtendedGUID> getRevisionListOrder() {
        return revisionListOrder;
    }

    public OneNoteDocument setRevisionListOrder(List<ExtendedGUID> revisionListOrder) {
        this.revisionListOrder = revisionListOrder;
        return this;
    }

    public Map<ExtendedGUID, Revision> getRevisionMap() {
        return revisionMap;
    }

    public OneNoteDocument setRevisionMap(Map<ExtendedGUID, Revision> revisionMap) {
        this.revisionMap = revisionMap;
        return this;
    }

    public Map<ExtendedGUID, FileNodePtr> getRevisionManifestLists() {
        return revisionManifestLists;
    }

    public OneNoteDocument setRevisionManifestLists(
            Map<ExtendedGUID, FileNodePtr> revisionManifestLists) {
        this.revisionManifestLists = revisionManifestLists;
        return this;
    }

    public Map<ExtendedGUID, FileChunkReference> getGuidToRef() {
        return guidToRef;
    }

    public OneNoteDocument setGuidToRef(Map<ExtendedGUID, FileChunkReference> guidToRef) {
        this.guidToRef = guidToRef;
        return this;
    }

    public Map<ExtendedGUID, FileNodePtr> getGuidToObject() {
        return guidToObject;
    }

    public OneNoteDocument setGuidToObject(Map<ExtendedGUID, FileNodePtr> guidToObject) {
        this.guidToObject = guidToObject;
        return this;
    }

    public Map<ExtendedGUID, Pair<Long, ExtendedGUID>> getRevisionRoleMap() {
        return revisionRoleMap;
    }

    public OneNoteDocument setRevisionRoleMap(
            Map<ExtendedGUID, Pair<Long, ExtendedGUID>> revisionRoleMap) {
        this.revisionRoleMap = revisionRoleMap;
        return this;
    }

    public ExtendedGUID getCurrentRevision() {
        return currentRevision;
    }

    public OneNoteDocument setCurrentRevision(ExtendedGUID currentRevision) {
        this.currentRevision = currentRevision;
        return this;
    }

    public FileNodeList getRoot() {
        return root;
    }

    public OneNoteDocument setRoot(FileNodeList root) {
        this.root = root;
        return this;
    }
}
