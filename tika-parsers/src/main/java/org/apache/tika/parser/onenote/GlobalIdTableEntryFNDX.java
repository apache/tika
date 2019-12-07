package org.apache.tika.parser.onenote;

public class GlobalIdTableEntryFNDX {
    long index;
    GUID guid;

    public long getIndex() {
        return index;
    }

    public GlobalIdTableEntryFNDX setIndex(long index) {
        this.index = index;
        return this;
    }

    public GUID getGuid() {
        return guid;
    }

    public GlobalIdTableEntryFNDX setGuid(GUID guid) {
        this.guid = guid;
        return this;
    }
}
