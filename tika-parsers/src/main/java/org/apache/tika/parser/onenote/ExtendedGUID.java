package org.apache.tika.parser.onenote;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public class ExtendedGUID implements Comparable<ExtendedGUID> {
    GUID guid;
    long n;

    public ExtendedGUID(GUID guid, long n) {
        this.guid = guid;
        this.n = n;
    }

    @Override
    public int compareTo(ExtendedGUID other) {
        if (other.guid.equals(guid)) {
            new Long(n).compareTo(other.n);
        }
        return guid.compareTo(other.guid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtendedGUID that = (ExtendedGUID) o;
        return n == that.n &&
          Objects.equals(guid, that.guid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, n);
    }

    public static ExtendedGUID nil() {
        return new ExtendedGUID(GUID.nil(), 0);
    }

    @Override
    public String toString() {
        return String.format("%s [%d]", guid, n);
    }

    @JsonIgnore
    public GUID getGuid() {
        return guid;
    }

    public ExtendedGUID setGuid(GUID guid) {
        this.guid = guid;
        return this;
    }

    public String getExtendedGuidString() {
        return guid.toString() + " [" + n + "]";
    }

    @JsonIgnore
    public long getN() {
        return n;
    }

    public ExtendedGUID setN(long n) {
        this.n = n;
        return this;
    }
}
