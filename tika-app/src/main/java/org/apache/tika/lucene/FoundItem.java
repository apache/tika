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
package org.apache.tika.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;

/**
 * Info about one found item.
 */
public class FoundItem {
    private final ScoreDoc scoreDoc;
    private final Document document;

    public FoundItem(ScoreDoc scoreDoc, Document document) {
        this.scoreDoc = scoreDoc;
        this.document = document;
    }

    public ScoreDoc getScoreDoc() {
        return scoreDoc;
    }

    public Document getDocument() {
        return document;
    }
}
