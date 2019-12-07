package org.apache.tika.parser.onenote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.StringUtils;

public class FileNodeListHeader {
    public static final long UNIT_MAGIC_CONSTANT = 0xA4567AB1F5F7F4C4L;
    long position;
    long fileNodeListId;
    long nFragmentSequence;

    /**
     * The FileNodeListHeader structure specifies the beginning of a FileNodeListFragment structure.
     *
     * @param position          Position of the file where this header starts.
     * @param uintMagic         An unsigned integer; MUST be "0xA4567AB1F5F7F4C4"
     * @param fileNodeListId    An unsigned integer that specifies the identity of the file node list
     *                          this fragment belongs to. MUST be equal to or greater than 0x00000010. The pair of
     *                          FileNodeListID and nFragmentSequence fields MUST be unique relative to other
     *                          FileNodeListFragment structures in the file.
     * @param nFragmentSequence An unsigned integer that specifies the index of the fragment in the
     *                          file node list containing the fragment. The nFragmentSequence field of the first fragment in a
     *                          given file node list MUST be 0 and the nFragmentSequence fields of all subsequent fragments in
     *                          this list MUST be sequential.
     */
    public FileNodeListHeader(long position, long uintMagic, long fileNodeListId, long nFragmentSequence) {
        if (uintMagic != UNIT_MAGIC_CONSTANT) {
            throw new RuntimeException("unitMagic must always be: 0x" + Long.toHexString(UNIT_MAGIC_CONSTANT));
        }
        this.position = position;
        this.fileNodeListId = fileNodeListId;
        if (fileNodeListId < 0x00000010) {
            throw new RuntimeException("FileNodeListHeader.fileNodeListId MUST be equal to or greater than 0x00000010");
        }
        this.nFragmentSequence = nFragmentSequence;
    }

    public long getFileNodeListId() {
        return fileNodeListId;
    }

    public FileNodeListHeader setFileNodeListId(long fileNodeListId) {
        this.fileNodeListId = fileNodeListId;
        return this;
    }

    public long getnFragmentSequence() {
        return nFragmentSequence;
    }

    public FileNodeListHeader setnFragmentSequence(long nFragmentSequence) {
        this.nFragmentSequence = nFragmentSequence;
        return this;
    }

    @JsonIgnore
    public long getPosition() {
        return position;
    }

    public FileNodeListHeader setPosition(long position) {
        this.position = position;
        return this;
    }

    public String getPositionHex() {
        return "0x" + StringUtils.leftPad(Long.toHexString(position), 8, "0");
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
          .add("position", "0x" + StringUtils.leftPad(Long.toHexString(position), 8, "0"))
          .add("fileNodeListId", fileNodeListId)
          .add("nFragmentSequence", nFragmentSequence)
          .toString();
    }
}
