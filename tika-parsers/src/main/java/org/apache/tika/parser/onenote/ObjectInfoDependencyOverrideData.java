package org.apache.tika.parser.onenote;

import com.google.common.collect.Lists;

import java.util.List;

public class ObjectInfoDependencyOverrideData {
    long c8bitOverrides;
    long c32bitOverrides;
    long crc;
    List<Integer> overrides1 = Lists.newArrayList();
    List<Long> overrides2 = Lists.newArrayList();

    public long getC8bitOverrides() {
        return c8bitOverrides;
    }

    public ObjectInfoDependencyOverrideData setC8bitOverrides(long c8bitOverrides) {
        this.c8bitOverrides = c8bitOverrides;
        return this;
    }

    public long getC32bitOverrides() {
        return c32bitOverrides;
    }

    public ObjectInfoDependencyOverrideData setC32bitOverrides(long c32bitOverrides) {
        this.c32bitOverrides = c32bitOverrides;
        return this;
    }

    public long getCrc() {
        return crc;
    }

    public ObjectInfoDependencyOverrideData setCrc(long crc) {
        this.crc = crc;
        return this;
    }

    public List<Integer> getOverrides1() {
        return overrides1;
    }

    public ObjectInfoDependencyOverrideData setOverrides1(List<Integer> overrides1) {
        this.overrides1 = overrides1;
        return this;
    }

    public List<Long> getOverrides2() {
        return overrides2;
    }

    public ObjectInfoDependencyOverrideData setOverrides2(List<Long> overrides2) {
        this.overrides2 = overrides2;
        return this;
    }
}
