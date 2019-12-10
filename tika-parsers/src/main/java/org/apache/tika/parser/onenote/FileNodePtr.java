package org.apache.tika.parser.onenote;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a list that represents how to get to the file node in the data structure.
 */
public class FileNodePtr {
    List<Integer> nodeListPositions = new ArrayList<>();

    public FileNodePtr() {

    }

    public FileNodePtr(FileNodePtr copyFrom) {
        nodeListPositions.addAll(copyFrom.nodeListPositions);
    }

    /**
     * Uses the nodeListPositions to get the FileNode from the document.root hierarchy.
     * <p>
     * It works like this:
     * <p>
     * The first element of the nodeListPositions is the index of the FileNode at the root.
     * The next element of the nodeListPosition is the index at the child of the first element.
     * And so on...
     * <p>
     * For example 0, 4, 15 would mean
     * <p>
     * document.root.children.get(0).childFileNodeList.children.get(4).childFileNodeList.children.get(15)
     *
     * @param document
     * @return
     */
    public FileNode dereference(OneNoteDocument document) {
        if (nodeListPositions.isEmpty()) {
            return null;
        }
        if (nodeListPositions.get(0) >= document.root.children.size()) {
            throw new RuntimeException("Exceeded root child size");
        }
        FileNode cur = document.root.children.get(nodeListPositions.get(0));
        for (int i = 1, ie = nodeListPositions.size(); i < ie; ++i) {
            cur = cur.childFileNodeList.children.get(nodeListPositions.get(i));
        }
        return cur;
    }
}
