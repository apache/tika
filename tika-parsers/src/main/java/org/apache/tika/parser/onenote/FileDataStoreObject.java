package org.apache.tika.parser.onenote;

public class FileDataStoreObject {
    // uint64_t cbLength;implicit in the fileData FileChunkReference
    FileChunkReference fileData = new FileChunkReference(); //points to raw data

    public FileChunkReference getFileData() {
        return fileData;
    }

    public FileDataStoreObject setFileData(FileChunkReference fileData) {
        this.fileData = fileData;
        return this;
    }
}
