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
package org.apache.tika.parser.pkg;

import java.io.Serializable;

/**
 * Configuration for {@link ComicBookTarParser}.
 */
public class ComicBookTarParserConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether to mark the cover, the first page in name order, as the THUMBNAIL. A tar gives
     * its names only as it is read, so this reads every entry header before the parse,
     * seeking over the data and holding the headers in memory; a stream that is not backed
     * by a file is first spooled to one. Default is false: the tar is read once, as a
     * stream, and no entry is marked.
     */
    private boolean markCover = false;

    public boolean isMarkCover() {
        return markCover;
    }

    public void setMarkCover(boolean markCover) {
        this.markCover = markCover;
    }
}
