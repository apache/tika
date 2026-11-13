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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.apache.tika.exception.TikaException;


class OneNoteDocument {
    OneNoteHeader header;
    List<ExtendedGUID> revisionListOrder = new ArrayList<>();
    Map<ExtendedGUID, Revision> revisionMap = new HashMap<>();
    Map<ExtendedGUID, FileNodePtr> revisionManifestLists = new HashMap<>();
    Map<ExtendedGUID, FileNodePtr> guidToObject = new HashMap<>();
    // each revision's own declarations; guidToObject holds the last one in the file
    Map<ExtendedGUID, Map<ExtendedGUID, FileNodePtr>> revisionObjects = new HashMap<>();
    // declared JCIDs, for object revisions that do not repeat them
    Map<ExtendedGUID, JCID> objectJcids = new HashMap<>();

    ExtendedGUID currentRevision = ExtendedGUID.nil();
    FileNodeList root = new FileNodeList();
    // set when the transaction log could not be fully read; counts committed before it stay
    String transactionLogWarning;
    // file nodes committed while parsing; sizes the tree walker's visit budget
    long fileNodeCount;
    // committed FileNode count per FileNodeListID from the transaction log; nodes past it
    // are uncommitted leftovers. Partial or empty when the log could not be read.
    Map<Long, Long> committedFileNodeCounts = new HashMap<>();

    // reads deferred property sets; set by the pointer that parses the document
    OneNotePtr propertySetReader;

    public OneNoteDocument() {

    }

    /** @return the node's property set, read from the file on first use */
    PropertySet propertySet(FileNode node) throws IOException, TikaException {
        if (node.propertySetStreams != null) {
            node.propertySet = propertySetReader.readPropertySet(node);
            node.propertySetStreams = null;
        }
        return node.propertySet;
    }

    void putObject(ExtendedGUID oid, FileNodePtr ptr) {
        guidToObject.put(oid, ptr);
        revisionObjects.computeIfAbsent(currentRevision, k -> new HashMap<>()).put(oid, ptr);
    }

    /** The object as revision {@code rid} has it: declared there or in a dependency. */
    FileNodePtr objectIn(ExtendedGUID rid, ExtendedGUID oid) {
        return objectIn(rid, oid, () -> true);
    }

    /** As above; each revision checked takes a {@code step}, and none left gives null. */
    FileNodePtr objectIn(ExtendedGUID rid, ExtendedGUID oid, BooleanSupplier step) {
        ExtendedGUID r = rid;
        for (int i = 0; r != null && i <= revisionMap.size(); i++) {
            if (!step.getAsBoolean()) {
                return null;
            }
            Map<ExtendedGUID, FileNodePtr> objects = revisionObjects.get(r);
            FileNodePtr ptr = objects == null ? null : objects.get(oid);
            if (ptr != null) {
                return ptr;
            }
            Revision revision = revisionMap.get(r);
            r = revision == null || r.equals(revision.dependent) ? null : revision.dependent;
        }
        // outside the dependency chain, e.g. in Aspose-written sections: the last declaration
        return guidToObject.get(oid);
    }

    void registerRevisionManifestList(ExtendedGUID guid, FileNodePtr ptr) {
        revisionManifestLists.put(guid, ptr);
        revisionListOrder.add(guid);
    }

    void registerRevisionManifest(FileNode fn) {
        revisionMap.putIfAbsent(fn.gosid, new Revision());
        Revision toModify = revisionMap.get(fn.gosid);
        toModify.gosid = fn.gosid;
        // a revision cannot depend on itself
        toModify.dependent = fn.gosid.equals(fn.subType.revisionManifest.ridDependent) ?
                ExtendedGUID.nil() : fn.subType.revisionManifest.ridDependent;
        currentRevision = fn.gosid;
    }

    public ExtendedGUID getCurrentRevision() {
        return currentRevision;
    }

    public FileNodeList getRoot() {
        return root;
    }
}
