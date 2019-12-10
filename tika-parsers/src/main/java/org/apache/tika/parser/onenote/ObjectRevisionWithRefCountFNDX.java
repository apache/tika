package org.apache.tika.parser.onenote;

public class ObjectRevisionWithRefCountFNDX {
    ObjectSpaceObjectPropSet ref;
    CompactID oid;
    long hasOidReferences;
    long hasOsidReferences;
    long cRef;

    public ObjectSpaceObjectPropSet getRef() {
        return ref;
    }

    public ObjectRevisionWithRefCountFNDX setRef(ObjectSpaceObjectPropSet ref) {
        this.ref = ref;
        return this;
    }

    public CompactID getOid() {
        return oid;
    }

    public ObjectRevisionWithRefCountFNDX setOid(CompactID oid) {
        this.oid = oid;
        return this;
    }

    public long getHasOidReferences() {
        return hasOidReferences;
    }

    public ObjectRevisionWithRefCountFNDX setHasOidReferences(long hasOidReferences) {
        this.hasOidReferences = hasOidReferences;
        return this;
    }

    public long getHasOsidReferences() {
        return hasOsidReferences;
    }

    public ObjectRevisionWithRefCountFNDX setHasOsidReferences(long hasOsidReferences) {
        this.hasOsidReferences = hasOsidReferences;
        return this;
    }

    public long getcRef() {
        return cRef;
    }

    public ObjectRevisionWithRefCountFNDX setcRef(long cRef) {
        this.cRef = cRef;
        return this;
    }
}
