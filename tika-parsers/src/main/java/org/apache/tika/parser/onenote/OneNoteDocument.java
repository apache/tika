package org.apache.tika.parser.onenote;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OneNoteDocument {
    OneNoteHeader header;
    List<ExtendedGUID> revisionListOrder = new ArrayList<>();
    Map<ExtendedGUID, Revision> revisionMap = new HashMap<>();
    Map<ExtendedGUID, FileNodePtr> revisionManifestLists = new HashMap<>();
    Map<ExtendedGUID, FileChunkReference> guidToRef = new HashMap<>();
    Map<ExtendedGUID, FileNodePtr> guidToObject = new HashMap<>();

    Multimap<ExtendedGUID, Pair<Long, ExtendedGUID>> revisionRoleMap = MultimapBuilder.linkedHashKeys().arrayListValues().build();
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

    public void registerAdditionalRevisionRole(ExtendedGUID gosid, long revisionRole, ExtendedGUID gctxid) {
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

    public OneNoteDocument setRevisionManifestLists(Map<ExtendedGUID, FileNodePtr> revisionManifestLists) {
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

    public Multimap<ExtendedGUID, Pair<Long, ExtendedGUID>> getRevisionRoleMap() {
        return revisionRoleMap;
    }

    public OneNoteDocument setRevisionRoleMap(Multimap<ExtendedGUID, Pair<Long, ExtendedGUID>> revisionRoleMap) {
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
