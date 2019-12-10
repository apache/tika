package org.apache.tika.parser.onenote;

import java.util.ArrayList;
import java.util.List;

public class FileNodeList {
    FileNodeListHeader fileNodeListHeader;
    List<FileNode> children = new ArrayList<>();

    public FileNodeListHeader getFileNodeListHeader() {
        return fileNodeListHeader;
    }

    public FileNodeList setFileNodeListHeader(FileNodeListHeader fileNodeListHeader) {
        this.fileNodeListHeader = fileNodeListHeader;
        return this;
    }

    public List<FileNode> getChildren() {
        return children;
    }

    public FileNodeList setChildren(List<FileNode> children) {
        this.children = children;
        return this;
    }
}
