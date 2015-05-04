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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

/**
 * 
 * Generates document summaries for corpus analysis in the Open Relevance
 * project.
 * 
 */
@SuppressWarnings("deprecation")
public class TrecDocumentGenerator {

	public TrecDocument summarize(File file) throws FileNotFoundException,
			IOException, TikaException {
		Tika tika = new Tika(); 
		Metadata met = new Metadata();

		String contents = tika.parseToString(new FileInputStream(file), met);
		return new TrecDocument(met.get(Metadata.RESOURCE_NAME_KEY), contents,
				met.getDate(Metadata.DATE)); 

	}

	// copied from
	// http://svn.apache.org/repos/asf/lucene/openrelevance/trunk/src/java/org/
	// apache/orp/util/TrecDocument.java
	// since the ORP jars aren't published anywhere
	class TrecDocument {
		private CharSequence docname;
		private CharSequence body;
		private Date date;

		public TrecDocument(CharSequence docname, CharSequence body, Date date) {
			this.docname = docname;
			this.body = body;
			this.date = date;
		}

		public TrecDocument() {
		}

		/**
		 * @return the docname
		 */
		public CharSequence getDocname() {
			return docname;
		}

		/**
		 * @param docname
		 *            the docname to set
		 */
		public void setDocname(CharSequence docname) {
			this.docname = docname;
		}

		/**
		 * @return the body
		 */
		public CharSequence getBody() {
			return body;
		}

		/**
		 * @param body
		 *            the body to set
		 */
		public void setBody(CharSequence body) {
			this.body = body;
		}

		/**
		 * @return the date
		 */
		public Date getDate() {
			return date;
		}

		/**
		 * @param date
		 *            the date to set
		 */
		public void setDate(Date date) {
			this.date = date;
		}
	}

}
