package org.apache.tika.parser.onenote;

public class RootObjectReference {
    CompactID oidRoot = new CompactID();
    RootObjectReferenceBase rootObjectReferenceBase = new RootObjectReferenceBase();

    public CompactID getOidRoot() {
        return oidRoot;
    }

    public RootObjectReference setOidRoot(CompactID oidRoot) {
        this.oidRoot = oidRoot;
        return this;
    }

    public RootObjectReferenceBase getRootObjectReferenceBase() {
        return rootObjectReferenceBase;
    }

    public RootObjectReference setRootObjectReferenceBase(RootObjectReferenceBase rootObjectReferenceBase) {
        this.rootObjectReferenceBase = rootObjectReferenceBase;
        return this;
    }
}
