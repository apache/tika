package org.apache.tika.parser.microsoft.onenote;

public class GlobalIdTableEntry3FNDX {
    long indexCopyFromStart;
    long entriesToCopy;
    long indexCopyToStart;

    public long getIndexCopyFromStart() {
        return indexCopyFromStart;
    }

    public GlobalIdTableEntry3FNDX setIndexCopyFromStart(long indexCopyFromStart) {
        this.indexCopyFromStart = indexCopyFromStart;
        return this;
    }

    public long getEntriesToCopy() {
        return entriesToCopy;
    }

    public GlobalIdTableEntry3FNDX setEntriesToCopy(long entriesToCopy) {
        this.entriesToCopy = entriesToCopy;
        return this;
    }

    public long getIndexCopyToStart() {
        return indexCopyToStart;
    }

    public GlobalIdTableEntry3FNDX setIndexCopyToStart(long indexCopyToStart) {
        this.indexCopyToStart = indexCopyToStart;
        return this;
    }
}
