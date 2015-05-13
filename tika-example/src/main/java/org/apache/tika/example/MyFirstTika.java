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

import org.apache.commons.io.FileUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Demonstrates how to call the different components within Tika: its
 * {@link Detector} framework (aka MIME identification and repository), its
 * {@link Parser} interface, its {@link LanguageIdentifier} and other goodies.
 */

@SuppressWarnings("deprecation")
public class MyFirstTika {

	public static void main(String[] args) throws Exception {
		String filename = args[0];
		MimeTypes mimeRegistry = TikaConfig.getDefaultConfig()
				.getMimeRepository();

		System.out.println("Examining: [" + filename + "]");

		System.out.println("The MIME type (based on filename) is: ["
				+ mimeRegistry.getMimeType(filename) + "]");

		System.out.println("The MIME type (based on MAGIC) is: ["
				+ mimeRegistry.getMimeType(new File(filename)) + "]");

		Detector mimeDetector = (Detector) mimeRegistry;
		System.out
				.println("The MIME type (based on the Detector interface) is: ["
						+ mimeDetector.detect(new File(filename).toURI().toURL()
								.openStream(), new Metadata()) + "]");

		LanguageIdentifier lang = new LanguageIdentifier(new LanguageProfile(
				FileUtils.readFileToString(new File(filename))));

		System.out.println("The language of this content is: ["
				+ lang.getLanguage() + "]");

		Parser parser = TikaConfig.getDefaultConfig().getParser(
				MediaType.parse(mimeRegistry.getMimeType(filename).getName()));
		Metadata parsedMet = new Metadata();
		ContentHandler handler = new BodyContentHandler();
		parser.parse(new File(filename).toURI().toURL().openStream(), handler,
				parsedMet, new ParseContext());

		System.out.println("Parsed Metadata: ");
		System.out.println(parsedMet);
		System.out.println("Parsed Text: ");
		System.out.println(handler.toString());

	}
}
