package org.apache.tika.parser.onenote;

public class OneNotePropertyId {
    OneNotePropertyEnum propertyEnum;
    long pid;
    long type;
    boolean inlineBool;

    public OneNotePropertyId() {
    }

    public OneNotePropertyId(long pid) {
        this.pid = pid;
        propertyEnum = OneNotePropertyEnum.of(pid);
        type = pid >> 26 & 0x1f;
        inlineBool = false;
        if (type == 0x2) {
            inlineBool = ((pid >> 31) & 0x1) > 0; // set the bool value from header
        } else {
            if (((pid >> 31) & 0x1) > 0) {
                throw new RuntimeException("Reserved non-zero");
            }
        }
    }

    public OneNotePropertyEnum getPropertyEnum() {
        return propertyEnum;
    }

    public OneNotePropertyId setPropertyEnum(OneNotePropertyEnum propertyEnum) {
        this.propertyEnum = propertyEnum;
        return this;
    }

    public long getPid() {
        return pid;
    }

    public OneNotePropertyId setPid(long pid) {
        this.pid = pid;
        return this;
    }

    public long getType() {
        return type;
    }

    public OneNotePropertyId setType(long type) {
        this.type = type;
        return this;
    }

    public boolean isInlineBool() {
        return inlineBool;
    }

    public OneNotePropertyId setInlineBool(boolean inlineBool) {
        this.inlineBool = inlineBool;
        return this;
    }

    @Override
    public String toString() {
        return "{" + propertyEnum +
          ", pid=0x" + Long.toHexString(pid) +
          ", type=0x" + Long.toHexString(type) +
          ", inlineBool=" + inlineBool +
          '}';
    }
}
