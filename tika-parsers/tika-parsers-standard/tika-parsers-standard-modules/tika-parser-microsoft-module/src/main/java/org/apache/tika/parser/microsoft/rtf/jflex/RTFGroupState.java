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
package org.apache.tika.parser.microsoft.rtf.jflex;

import java.nio.charset.Charset;

/**
 * State associated with a single RTF group ({@code \{ ... \}}).
 * <p>
 * When a new group opens, the current state is pushed onto the stack and a
 * child state is created that inherits the parent's properties. When the group
 * closes, the state is popped.
 */
public class RTFGroupState {

    /** Nesting depth (0 = root). */
    int depth;

    /** Current font charset, set by {@code \fN} if the font table maps it. May be null. */
    Charset fontCharset;

    /** Current font ID, set by {@code \fN}. -1 if unset. */
    int fontId = -1;

    /** Number of ANSI chars to skip after a unicode escape (ucN control word). Default 1. */
    int ucSkip = 1;

    /** True if this group's content should be ignored (e.g. {@code \*} destination). */
    boolean ignore;

    /** True if bold. */
    boolean bold;

    /** True if italic. */
    boolean italic;

    // Embedded object / picture state
    boolean objdata;
    int pictDepth;
    boolean sp;
    boolean sn;
    boolean sv;
    boolean object;
    boolean annotation;

    /** Create a root group state with defaults. */
    public RTFGroupState() {
    }

    /** Create a child group state inheriting from the parent. */
    public RTFGroupState(RTFGroupState parent) {
        this.depth = parent.depth + 1;
        this.fontCharset = parent.fontCharset;
        this.fontId = parent.fontId;
        this.ucSkip = parent.ucSkip;
        this.ignore = parent.ignore;
        this.bold = parent.bold;
        this.italic = parent.italic;
        this.pictDepth = parent.pictDepth > 0 ? parent.pictDepth + 1 : 0;
        // objdata, sp, sn, sv, object, annotation are NOT inherited
    }
}
