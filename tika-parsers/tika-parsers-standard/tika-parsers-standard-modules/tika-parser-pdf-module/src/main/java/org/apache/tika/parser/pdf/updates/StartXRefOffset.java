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
package org.apache.tika.parser.pdf.updates;

public class StartXRefOffset {
    private final long startxref;
    private final long startXrefOffset;
    private final long endEofOffset;

    private final boolean hasEof;
    public StartXRefOffset(long startxref, long startXrefOffset, long endEofOffset,
                           boolean hasEof) {
        this.startxref = startxref;
        this.startXrefOffset = startXrefOffset;
        this.endEofOffset = endEofOffset;
        this.hasEof = hasEof;
    }

    public long getStartxref() {
        return startxref;
    }

    public long getStartXrefOffset() {
        return startXrefOffset;
    }

    public long getEndEofOffset() {
        return endEofOffset;
    }

    public boolean isHasEof() {
        return hasEof;
    }

    @Override
    public String toString() {
        return "StartXRefOffset{" + "startxref=" + startxref + ", startXrefOffset=" +
                startXrefOffset + ", endEofOffset=" + endEofOffset + ", hasEof=" + hasEof + '}';
    }
}
