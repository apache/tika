package org.apache.tika.parser.onenote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Revision {
    Map<Long, GUID> globalId = new HashMap<>();
    List<FileNodePtr> manifestList = new ArrayList<>();
    ExtendedGUID gosid = ExtendedGUID.nil();
    ExtendedGUID dependent = ExtendedGUID.nil();

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

    public Revision() {

    }

    public Revision(Map<Long, GUID> globalId, List<FileNodePtr> manifestList, ExtendedGUID gosid, ExtendedGUID dependent) {
        this.globalId = globalId;
        this.manifestList = manifestList;
        this.gosid = gosid;
        this.dependent = dependent;
    }
}
