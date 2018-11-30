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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.queryparser.classic.QueryParser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Document index in RAM.
 */
public class DocumentIndexer {
    private final Directory dir;
    private final Analyzer analyzer;

    public DocumentIndexer() throws IOException {
        this.dir = new RAMDirectory();
        this.analyzer = new StandardAnalyzer();
    }

    public void addDocument(String filename, String contents) throws IOException {
        Document doc = new Document();
        doc.add(new StringField("filename", filename, Store.YES));
        doc.add(new TextField("contents", contents, Store.NO));
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(dir, iwc);
        writer.addDocument(doc);
        writer.close();
    }


    public List<FoundItem> searchDocuments(String queryString) throws ParseException, IOException {
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        // Build a Query object
        QueryParser parser = new QueryParser("contents", analyzer);
        Query query = parser.parse(queryString);
        TopDocs topDocs = searcher.search(query, 10);
        List<FoundItem> fi = new ArrayList<>(topDocs.scoreDocs.length);
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            fi.add(new FoundItem(scoreDoc,reader.document(scoreDoc.doc)));
        }
        reader.close();
        return fi;
    }

}
