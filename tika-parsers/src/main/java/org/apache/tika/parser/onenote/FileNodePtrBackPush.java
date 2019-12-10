package org.apache.tika.parser.onenote;

public class FileNodePtrBackPush {
    FileNodePtr parent;
    public static int numAdds = 0;
    public static int numDescs = 0;

    public FileNodePtrBackPush(FileNodePtr parent) {
        this.parent = parent;
        this.parent.nodeListPositions.add(0);
        ++numAdds;
    }

    public void dec() {
        parent.nodeListPositions.remove(parent.nodeListPositions.size() - 1);
        numDescs++;
    }
}
