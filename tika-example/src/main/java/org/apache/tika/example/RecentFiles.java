/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.example;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.jackrabbit.util.ISO8601;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;

/**
 *
 * Builds on top of the LuceneIndexer and the Metadata discussions in Chapter 6
 * to output an RSS (or RDF) feed of files crawled by the LuceneIndexer within
 * the last N minutes.
 */
@SuppressWarnings("deprecation")
public class RecentFiles {

	private IndexReader reader;

	private SimpleDateFormat rssDateFormat = new SimpleDateFormat(
			"E, dd MMM yyyy HH:mm:ss z", Locale.getDefault());

	public String generateRSS(File indexFile) throws CorruptIndexException,
			IOException {
		StringBuffer output = new StringBuffer();
		output.append(getRSSHeaders());
		IndexSearcher searcher = null;
		try {
			reader = IndexReader.open(new SimpleFSDirectory(indexFile));
			searcher = new IndexSearcher(reader);
			GregorianCalendar gc = new java.util.GregorianCalendar(TimeZone.getDefault(), Locale.getDefault());
			gc.setTime(new Date());
			String nowDateTime = ISO8601.format(gc);
			gc.add(java.util.GregorianCalendar.MINUTE, -5);
			String fiveMinsAgo = ISO8601.format(gc);
			TermRangeQuery query = new TermRangeQuery(Metadata.DATE.toString(),
					fiveMinsAgo, nowDateTime, true, true);
			TopScoreDocCollector collector = TopScoreDocCollector.create(20,
					true);
			searcher.search(query, collector);
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			for (int i = 0; i < hits.length; i++) {
				Document doc = searcher.doc(hits[i].doc);
				output.append(getRSSItem(doc));
			}

		} finally {
			if (reader != null) reader.close();
			if (searcher != null) searcher.close();
		}

		output.append(getRSSFooters());
		return output.toString();
	}

	public String getRSSItem(Document doc) {
		StringBuffer output = new StringBuffer();
		output.append("<item>");
		output.append(emitTag("guid", doc.get(DublinCore.SOURCE.getName()),
				"isPermalink", "true"));
		output.append(emitTag("title", doc.get(Metadata.TITLE), null, null));
		output.append(emitTag("link", doc.get(DublinCore.SOURCE.getName()),
				null, null));
		output.append(emitTag("author", doc.get(Metadata.CREATOR), null, null));
		for (String topic : doc.getValues(Metadata.SUBJECT)) {
			output.append(emitTag("category", topic, null, null));
		}
		output.append(emitTag("pubDate", rssDateFormat.format(ISO8601.parse(doc
				.get(Metadata.DATE.toString()))), null, null));
		output.append(emitTag("description", doc.get(Metadata.TITLE), null,
				null));
		output.append("</item>");
		return output.toString();
	}

	public String getRSSHeaders() {
		StringBuffer output = new StringBuffer();
		output.append("<?xml version=\"1.0\" encoding=\"utf-8\">");
		output.append("<rss version=\"2.0\">");
		output.append("  <channel>");
		output.append("     <title>Tika in Action: Recent Files Feed."
				+ "</title>");
		output.append("     <description>Chapter 6 Examples demonstrating "
				+ "use of Tika Metadata for RSS.</description>");
		output.append("     <link>tikainaction.rss</link>");
		output.append("     <lastBuildDate>" + rssDateFormat.format(new Date())
				+ "</lastBuildDate>");
		output.append("     <generator>Manning Publications: Tika in Action"
				+ "</generator>");
		output.append("     <copyright>All Rights Reserved</copyright>");
		return output.toString();
	}

	public String getRSSFooters() {
		StringBuffer output = new StringBuffer();
		output.append("   </channel>");
		return output.toString();
	}

	private String emitTag(String tagName, String value, String attributeName,
			String attributeValue) {
		StringBuffer output = new StringBuffer();
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
