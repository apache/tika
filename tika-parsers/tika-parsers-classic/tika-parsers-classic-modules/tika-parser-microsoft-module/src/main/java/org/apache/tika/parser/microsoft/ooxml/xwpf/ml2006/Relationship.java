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

package org.apache.tika.parser.microsoft.ooxml.xwpf.ml2006;


import org.apache.poi.openxml4j.opc.TargetMode;

class Relationship {

    private final String contentType;

    private final String target;

    private final TargetMode targetMode;

    public Relationship(String contentType, String target) {
        this(contentType, target, null);
    }

    public Relationship(String contentType, String target, TargetMode targetMode) {
        this.contentType = contentType;
        this.target = target;
        this.targetMode = targetMode;
    }

    public String getContentType() {
        return contentType;
    }

    public String getTarget() {
        return target;
    }

    public TargetMode getTargetMode() {
        return targetMode;
    }
}
