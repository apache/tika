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

class OneNotePropertyId {
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
        return "{" + propertyEnum + ", pid=0x" + Long.toHexString(pid) + ", type=0x" +
                Long.toHexString(type) + ", inlineBool=" + inlineBool + '}';
    }
}
