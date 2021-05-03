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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;

import org.apache.tika.Tika;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds on the LuceneIndexer from Chapter 5 and adds indexing of Metadata.
 */
@SuppressWarnings("deprecation")
public class MetadataAwareLuceneIndexer {
    private Tika tika;

    private IndexWriter writer;

    public MetadataAwareLuceneIndexer(IndexWriter writer, Tika tika) {
        this.writer = writer;
        this.tika = tika;
    }

    public void indexContentSpecificMet(File file) throws Exception {
        Metadata met = new Metadata();
        try (InputStream is = new FileInputStream(file)) {
            tika.parse(is, met);
            Document document = new Document();
            for (String key : met.names()) {
                String[] values = met.getValues(key);
                for (String val : values) {
                    document.add(new TextField(key, val, Store.YES));
                }
                writer.addDocument(document);
            }
        }
    }

    public void indexWithDublinCore(File file) throws Exception {
        Metadata met = new Metadata();
        met.add(TikaCoreProperties.CREATOR, "Manning");
        met.add(TikaCoreProperties.CREATOR, "Tika in Action");
        met.set(TikaCoreProperties.CREATED, new Date());
        met.set(TikaCoreProperties.FORMAT, tika.detect(file));
        met.set(DublinCore.SOURCE, file.toURI().toURL().toString());
        met.add(TikaCoreProperties.SUBJECT, "File");
        met.add(TikaCoreProperties.SUBJECT, "Indexing");
        met.add(TikaCoreProperties.SUBJECT, "Metadata");
        met.set(Property
                        .externalClosedChoise(TikaCoreProperties.RIGHTS.getName(), "public", "private"),
                "public");
        try (InputStream is = new FileInputStream(file)) {
            tika.parse(is, met);
            Document document = new Document();
            for (String key : met.names()) {
                String[] values = met.getValues(key);
                for (String val : values) {
                    document.add(new TextField(key, val, Store.YES));
                }
                writer.addDocument(document);
            }
        }
    }
}
