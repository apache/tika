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
package org.apache.tika.parser.rtf;

import java.nio.charset.Charset;

/* Holds all state associated with current RTF group, ie {
 * ... }. */

class GroupState {
    public int depth;
    public boolean bold;
    public boolean italic;
    // True if we are skipping all text in current group,
    // eg if group leads with a \*:
    public boolean ignore;
    // Default is 1 if no uc control has been seen yet:
    public int ucSkip = 1;
    public int list;
    public int listLevel;
    public Charset fontCharset;
    //in objdata
    public boolean objdata;
    //depth in pict, 1 = at pict level
    public int pictDepth;
    //in picprop key/value pair
    public boolean sp;
    //in picprop's name 
    public boolean sn;
    //in picprop's value
    public boolean sv;
    //in embedded object or not
    public boolean object;
    public boolean annotation;

    // Create default (root) GroupState
    public GroupState() {
    }

    // Create new GroupState, inheriting all properties from current one, adding 1 to the depth
    public GroupState(GroupState other) {
        bold = other.bold;
        italic = other.italic;
        ignore = other.ignore;
        ucSkip = other.ucSkip;
        list = other.list;
        listLevel = other.listLevel;
        fontCharset = other.fontCharset;
        depth = 1 + other.depth;
        pictDepth = other.pictDepth > 0 ? other.pictDepth + 1 : 0;
        //do not inherit object, sn, sv, sp or annotation

    }
}
