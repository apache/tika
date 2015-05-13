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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.tika.Tika;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

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
		InputStream is = new FileInputStream(file);
		try {
			tika.parse(is, met);
			Document document = new Document();
			for (String key : met.names()) {
				String[] values = met.getValues(key);
				for (String val : values) {
					document.add(new Field(key, val, Store.YES, Index.ANALYZED));
				}
				writer.addDocument(document);
			}
		} finally {
			is.close();
		}
	}

	public void indexWithDublinCore(File file) throws Exception {
		Metadata met = new Metadata();
		met.add(Metadata.CREATOR, "Manning");
		met.add(Metadata.CREATOR, "Tika in Action");
		met.set(Metadata.DATE, new Date());
		met.set(Metadata.FORMAT, tika.detect(file));
		met.set(DublinCore.SOURCE, file.toURI().toURL().toString());
		met.add(Metadata.SUBJECT, "File");
		met.add(Metadata.SUBJECT, "Indexing");
		met.add(Metadata.SUBJECT, "Metadata");
		met.set(Property.externalClosedChoise(Metadata.RIGHTS, "public",
				"private"), "public");
		InputStream is = new FileInputStream(file);
		try {
			tika.parse(is, met);
			Document document = new Document();
			for (String key : met.names()) {
				String[] values = met.getValues(key);
				for (String val : values) {
					document.add(new Field(key, val, Store.YES, Index.ANALYZED));
				}
				writer.addDocument(document);
			}
		} finally {
			is.close();
		}
	}

}
