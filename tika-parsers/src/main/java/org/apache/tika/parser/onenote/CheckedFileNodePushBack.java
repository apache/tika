package org.apache.tika.parser.onenote;

/**
 * Provides a way to add a new element on the fileNode list, but remove it from the list if
 * we end up not committing it.
 */
public class CheckedFileNodePushBack {
    FileNodeList fileNodeList;
    boolean committed;

    public CheckedFileNodePushBack(FileNodeList fileNodeList) {
        committed = true;
        this.fileNodeList = fileNodeList;
        fileNodeList.children.add(new FileNode());
        committed = false;
    }

    public void commit() {
        committed = true;
    }

    public void popBackIfNotCommitted() {
        if (!committed) {
            fileNodeList.children.remove(fileNodeList.children.size() - 1);
        }
    }
}
