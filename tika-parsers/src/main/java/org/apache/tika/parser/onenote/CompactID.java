package org.apache.tika.parser.onenote;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class CompactID {
    char n;
    long guidIndex; //only occupies 24 bits
    ExtendedGUID guid;

    @JsonIgnore
    public char getN() {
        return n;
    }

    public CompactID setN(char n) {
        this.n = n;
        return this;
    }

    @JsonIgnore
    public long getGuidIndex() {
        return guidIndex;
    }

    public CompactID setGuidIndex(long guidIndex) {
        this.guidIndex = guidIndex;
        return this;
    }

    @JsonIgnore
    public ExtendedGUID getGuid() {
        return guid;
    }

    public CompactID setGuid(ExtendedGUID guid) {
        this.guid = guid;
        return this;
    }

    public String getCompactIDString() {
        return new StringBuilder()
          .append(guid)
          .append(", index=")
          .append(guidIndex)
          .append(", n=")
          .append((int) n)
          .toString();
    }
}
