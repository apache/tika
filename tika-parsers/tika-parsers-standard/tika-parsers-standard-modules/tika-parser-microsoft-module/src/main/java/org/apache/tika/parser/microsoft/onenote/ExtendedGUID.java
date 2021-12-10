/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.microsoft.onenote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;

public class ExtendedGUID implements Comparable<ExtendedGUID> {
    GUID guid;
    long n;

    public ExtendedGUID() {

    }

    public ExtendedGUID(GUID guid, long n) {
        this.guid = guid;
        this.n = n;
    }

    public static ExtendedGUID nil() {
        return new ExtendedGUID(GUID.nil(), 0);
    }

    @Override
    public int compareTo(ExtendedGUID other) {
        if (other.guid.equals(guid)) {
            return Long.compare(n, other.n);
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
        return n == that.n && Objects.equals(guid, that.guid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, n);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s [%d]", guid, n);
    }

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

    public long getN() {
        return n;
    }

    public ExtendedGUID setN(long n) {
        this.n = n;
        return this;
    }

    /**
     * This method is used to convert the element of ExtendedGUID object into a byte List.
     *
     * @return Return the byte list which store the byte information of ExtendedGUID
     */
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<>(guid.toByteArray());
        for (byte b : BitConverter.getBytes(n)) {
            byteList.add(b);
        }
        return byteList;
    }
}
