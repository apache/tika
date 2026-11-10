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

import org.apache.tika.exception.TikaException;

/** Path of child indexes from document.root to a FileNode. */
class FileNodePtr {
    List<Integer> nodeListPositions = new ArrayList<>();

    public FileNodePtr() {

    }

    public FileNodePtr(FileNodePtr copyFrom) {
        nodeListPositions.addAll(copyFrom.nodeListPositions);
    }

    /**
     * E.g. 0, 4, 15 is root.children[0].childFileNodeList.children[4]
     * .childFileNodeList.children[15].
     */
    public FileNode dereference(OneNoteDocument document) throws TikaException {
        if (nodeListPositions.isEmpty()) {
            return null;
        }
        int rootPosition = nodeListPositions.get(0);
        if (rootPosition < 0 || rootPosition >= document.root.children.size()) {
            throw new TikaException("Exceeded root child size");
        }
        FileNode cur = document.root.children.get(rootPosition);
        for (int i = 1, ie = nodeListPositions.size(); i < ie; ++i) {
            if (cur == null || cur.childFileNodeList == null ||
                    cur.childFileNodeList.children == null) {
                throw new TikaException("Missing child file node list");
            }
            int childPosition = nodeListPositions.get(i);
            if (childPosition < 0 || childPosition >= cur.childFileNodeList.children.size()) {
                throw new TikaException("Exceeded child file node size");
            }
            cur = cur.childFileNodeList.children.get(childPosition);
        }
        return cur;
    }
}
