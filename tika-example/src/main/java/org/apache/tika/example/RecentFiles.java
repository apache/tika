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

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds on top of the LuceneIndexer and the Metadata discussions in Chapter 6
 * to output an RSS (or RDF) feed of files crawled by the LuceneIndexer within
 * the last N minutes.
 */
@SuppressWarnings("deprecation")
public class RecentFiles {
    private IndexReader reader;

    private SimpleDateFormat rssDateFormat =
            new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.getDefault());

    public String generateRSS(Path indexFile) throws CorruptIndexException, IOException {
        StringBuffer output = new StringBuffer();
        output.append(getRSSHeaders());
        IndexSearcher searcher = null;
        try {
            reader = DirectoryReader.open(FSDirectory.open(indexFile));
            searcher = new IndexSearcher(reader);
            GregorianCalendar gc =
                    new java.util.GregorianCalendar(TimeZone.getDefault(), Locale.getDefault());
            gc.setTime(new Date());
            String nowDateTime = ISO8601.format(gc);
            gc.add(java.util.GregorianCalendar.MINUTE, -5);
            String fiveMinsAgo = ISO8601.format(gc);
            TermRangeQuery query = new TermRangeQuery(TikaCoreProperties.CREATED.getName(),
                    new BytesRef(fiveMinsAgo), new BytesRef(nowDateTime), true, true);
            TopScoreDocCollector collector = TopScoreDocCollector.create(20, 10000);
            searcher.search(query, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;
            for (ScoreDoc hit : hits) {
                Document doc = searcher.doc(hit.doc);
                output.append(getRSSItem(doc));
            }

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        output.append(getRSSFooters());
        return output.toString();
    }

    public String getRSSItem(Document doc) {
        StringBuilder output = new StringBuilder();
        output.append("<item>");
        output.append(emitTag("guid", doc.get(DublinCore.SOURCE.getName()), "isPermalink", "true"));
        output.append(emitTag("title", doc.get(TikaCoreProperties.TITLE.getName()), null, null));
        output.append(emitTag("link", doc.get(DublinCore.SOURCE.getName()), null, null));
        output.append(emitTag("author", doc.get(TikaCoreProperties.CREATOR.getName()), null, null));
        for (String topic : doc.getValues(TikaCoreProperties.SUBJECT.getName())) {
            output.append(emitTag("category", topic, null, null));
        }
        output.append(emitTag("pubDate",
                rssDateFormat.format(ISO8601.parse(doc.get(TikaCoreProperties.CREATED.getName()))),
                null, null));
        output.append(
                emitTag("description", doc.get(TikaCoreProperties.TITLE.getName()), null, null));
        output.append("</item>");
        return output.toString();
    }

    public String getRSSHeaders() {
        StringBuilder output = new StringBuilder();
        output.append("<?xml version=\"1.0\" encoding=\"utf-8\">");
        output.append("<rss version=\"2.0\">");
        output.append("  <channel>");
        output.append("     <title>Tika in Action: Recent Files Feed.</title>");
        output.append("     <description>Chapter 6 Examples demonstrating " +
                "use of Tika Metadata for RSS.</description>");
        output.append("     <link>tikainaction.rss</link>");
        output.append("     <lastBuildDate>");
        output.append(rssDateFormat.format(new Date()));
        output.append("</lastBuildDate>");
        output.append("     <generator>Manning Publications: Tika in Action</generator>");
        output.append("     <copyright>All Rights Reserved</copyright>");
        return output.toString();
    }

    public String getRSSFooters() {
        return "   </channel>";
    }

    private String emitTag(String tagName, String value, String attributeName,
                           String attributeValue) {
        StringBuilder output = new StringBuilder();
        output.append("<");
        output.append(tagName);
        if (attributeName != null) {
            output.append(" ");
            output.append(attributeName);
            output.append("=\"");
            output.append(attributeValue);
            output.append("\"");
        }
        output.append(">");
        output.append(value);
        output.append("</");
        output.append(tagName);
        output.append(">");
        return output.toString();
    }
}
