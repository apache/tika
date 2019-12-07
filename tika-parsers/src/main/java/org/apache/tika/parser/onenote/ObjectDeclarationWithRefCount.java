package org.apache.tika.parser.onenote;

public class ObjectDeclarationWithRefCount {
    ObjectSpaceObjectPropSet objectRef;
    ObjectDeclarationWithRefCountBody body = new ObjectDeclarationWithRefCountBody();
    long cRef;

    public static class ReadOnly {
        byte[] md5;

        public byte[] getMd5() {
            return md5;
        }

        public ReadOnly setMd5(byte[] md5) {
            this.md5 = md5;
            return this;
        }
    }

    ReadOnly readOnly = new ReadOnly();

    public ObjectSpaceObjectPropSet getObjectRef() {
        return objectRef;
    }

    public ObjectDeclarationWithRefCount setObjectRef(ObjectSpaceObjectPropSet objectRef) {
        this.objectRef = objectRef;
        return this;
    }

    public ObjectDeclarationWithRefCountBody getBody() {
        return body;
    }

    public ObjectDeclarationWithRefCount setBody(ObjectDeclarationWithRefCountBody body) {
        this.body = body;
        return this;
    }

    public long getcRef() {
        return cRef;
    }

    public ObjectDeclarationWithRefCount setcRef(long cRef) {
        this.cRef = cRef;
        return this;
    }

    public ReadOnly getReadOnly() {
        return readOnly;
    }

    public ObjectDeclarationWithRefCount setReadOnly(ReadOnly readOnly) {
        this.readOnly = readOnly;
        return this;
    }
}
