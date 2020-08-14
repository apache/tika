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

class ObjectInfoDependencyOverrideData {
    long c8bitOverrides;
    long c32bitOverrides;
    long crc;
    List<Integer> overrides1 = new ArrayList<>();
    List<Long> overrides2 = new ArrayList<>();

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
