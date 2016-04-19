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

package org.apache.tika.example;

import java.io.File;
import java.io.Reader;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.Tika;

@SuppressWarnings("deprecation")
public class LuceneIndexerExtended {
    private final Tika tika;

    private final IndexWriter writer;

    public LuceneIndexerExtended(IndexWriter writer, Tika tika) {
        this.writer = writer;
        this.tika = tika;
    }

    public static void main(String[] args) throws Exception {
        try (IndexWriter writer = new IndexWriter(
                new SimpleFSDirectory(new File(args[0])),
                new StandardAnalyzer(Version.LUCENE_30),
                MaxFieldLength.UNLIMITED)) {
            LuceneIndexer indexer = new LuceneIndexer(new Tika(), writer);
            for (int i = 1; i < args.length; i++) {
                indexer.indexDocument(new File(args[i]));
            }
        }
    }

    public void indexDocument(File file) throws Exception {
        try (Reader fulltext = tika.parse(file)) {
            Document document = new Document();
            document.add(new Field("filename", file.getName(), Store.YES, Index.ANALYZED));
            document.add(new Field("fulltext", fulltext));
            writer.addDocument(document);
        }
    }
}
