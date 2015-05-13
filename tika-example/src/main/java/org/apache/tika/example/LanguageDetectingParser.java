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

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.ProfilingHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

@SuppressWarnings("deprecation")
public class LanguageDetectingParser extends DelegatingParser {

	private static final long serialVersionUID = 4291320409396502774L;

	public void parse(InputStream stream, ContentHandler handler,
			final Metadata metadata, ParseContext context) throws SAXException,
			IOException, TikaException {
		ProfilingHandler profiler = new ProfilingHandler();
		ContentHandler tee = new TeeContentHandler(handler, profiler);

		super.parse(stream, tee, metadata, context);

		LanguageIdentifier identifier = profiler.getLanguage();
		if (identifier.isReasonablyCertain()) {
			metadata.set(Metadata.LANGUAGE, identifier.getLanguage());
		}
	}

}
