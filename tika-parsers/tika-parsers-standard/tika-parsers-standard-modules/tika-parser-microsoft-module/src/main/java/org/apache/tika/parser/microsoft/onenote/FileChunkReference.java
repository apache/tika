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

import java.util.Objects;

/**
 * Byte offset ({@code stp}) and size ({@code cb}) of data in the file.
 * Special values: fcrNil (stp all 1s, cb 0) and fcrZero (both 0).
 */
class FileChunkReference {

    long stp;
    long cb;

    public FileChunkReference() {

    }

    public FileChunkReference(long stp, long cb) {
        this.stp = stp;
        this.cb = cb;
    }

    public static FileChunkReference nil() {
        return new FileChunkReference(-1L, 0L);
    }

    @Override
    public String toString() {
        return "FileChunkReference{" + "stp=" + stp + ", cb=" + cb + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileChunkReference that = (FileChunkReference) o;
        return stp == that.stp && cb == that.cb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stp, cb);
    }

    public FileChunkReference setStp(long stp) {
        this.stp = stp;
        return this;
    }

    public FileChunkReference setCb(long cb) {
        this.cb = cb;
        return this;
    }
}
