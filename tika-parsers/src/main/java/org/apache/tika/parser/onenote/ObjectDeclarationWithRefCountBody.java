package org.apache.tika.parser.onenote;

public class ObjectDeclarationWithRefCountBody {
    CompactID oid;
    JCID jcid = new JCID(); // if this is a ObjectDeclarationWithRefCountBody, jci = 0x01
    boolean fHasOidReferences;
    boolean hasOsidReferences;
    // the obj is a GUID in the file_data_store_reference
    // for a ObjectDeclarationFileData3RefCountFND
    boolean file_data_store_reference;

    public CompactID getOid() {
        return oid;
    }

    public ObjectDeclarationWithRefCountBody setOid(CompactID oid) {
        this.oid = oid;
        return this;
    }

    public JCID getJcid() {
        return jcid;
    }

    public ObjectDeclarationWithRefCountBody setJcid(JCID jcid) {
        this.jcid = jcid;
        return this;
    }

    public boolean isfHasOidReferences() {
        return fHasOidReferences;
    }

    public ObjectDeclarationWithRefCountBody setfHasOidReferences(boolean fHasOidReferences) {
        this.fHasOidReferences = fHasOidReferences;
        return this;
    }

    public boolean isHasOsidReferences() {
        return hasOsidReferences;
    }

    public ObjectDeclarationWithRefCountBody setHasOsidReferences(boolean hasOsidReferences) {
        this.hasOsidReferences = hasOsidReferences;
        return this;
    }

    public boolean isFile_data_store_reference() {
        return file_data_store_reference;
    }

    public ObjectDeclarationWithRefCountBody setFile_data_store_reference(boolean file_data_store_reference) {
        this.file_data_store_reference = file_data_store_reference;
        return this;
    }
}
