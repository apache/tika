package org.apache.tika.parser.onenote;

import java.util.ArrayList;
import java.util.List;

public class ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs {
    long count; // 24 bits
    long extendedStreamsPresent;
    long osidsStreamNotPresent;
    List<CompactID> data = new ArrayList<>();

    public long getCount() {
        return count;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setCount(long count) {
        this.count = count;
        return this;
    }

    public long getExtendedStreamsPresent() {
        return extendedStreamsPresent;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setExtendedStreamsPresent(long extendedStreamsPresent) {
        this.extendedStreamsPresent = extendedStreamsPresent;
        return this;
    }

    public long getOsidsStreamNotPresent() {
        return osidsStreamNotPresent;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setOsidsStreamNotPresent(long osidsStreamNotPresent) {
        this.osidsStreamNotPresent = osidsStreamNotPresent;
        return this;
    }

    public List<CompactID> getData() {
        return data;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setData(List<CompactID> data) {
        this.data = data;
        return this;
    }
}
