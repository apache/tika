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

class FileNodeList {
    FileNodeListHeader fileNodeListHeader;
    List<FileNode> children = new ArrayList<>();

    public FileNodeListHeader getFileNodeListHeader() {
        return fileNodeListHeader;
    }

    public FileNodeList setFileNodeListHeader(FileNodeListHeader fileNodeListHeader) {
        this.fileNodeListHeader = fileNodeListHeader;
        return this;
    }

    public List<FileNode> getChildren() {
        return children;
    }

    public FileNodeList setChildren(List<FileNode> children) {
        this.children = children;
        return this;
    }
}
