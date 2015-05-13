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

import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;
import org.apache.tika.language.ProfilingHandler;
import org.apache.tika.language.ProfilingWriter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;

public class Language {

	public static void languageDetection() throws IOException {
		LanguageProfile profile = new LanguageProfile(
				"Alla människor är födda fria och"
						+ " lika i värde och rättigheter.");

		LanguageIdentifier identifier = new LanguageIdentifier(profile);
		System.out.println(identifier.getLanguage());
	}

	public static void languageDetectionWithWriter() throws IOException {
		ProfilingWriter writer = new ProfilingWriter();
		writer.append("Minden emberi lény");
		writer.append(" szabadon születik és");
		writer.append(" egyenlő méltósága és");
		writer.append(" joga van.");

		LanguageIdentifier identifier = writer.getLanguage();
		System.out.println(identifier.getLanguage());
		writer.close();

	}

	public static void languageDetectionWithHandler() throws Exception {
		ProfilingHandler handler = new ProfilingHandler();
		new AutoDetectParser().parse(System.in, handler, new Metadata(),
				new ParseContext());

		LanguageIdentifier identifier = handler.getLanguage();
		System.out.println(identifier.getLanguage());
	}
}
