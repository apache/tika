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

package org.apache.tika.sax;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Class to demonstrate how to use the {@link StandardsExtractingContentHandler}
 * to get a list of the standard references from every file in a directory.
 * 
 * <p>
 * You can run this main method by running 
 * <code>
 *   mvn exec:java -Dexec.mainClass="org.apache.tika.example.StandardsExtractionExample" -Dexec.args="/path/to/input"
 * </code>
 * from the tika-example directory.
 * </p>
 */
public class StandardsExtractionExample {
	private static HashSet<String> standardReferences = new HashSet<>();
	private static int failedFiles = 0;
	private static int successfulFiles = 0;

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: " + StandardsExtractionExample.class.getName() + " /path/to/input");
			System.exit(1);
		}
		String pathname = args[0];

		Path folder = Paths.get(pathname);
		System.out.println("Searching " + folder.toAbsolutePath() + "...");
		processFolder(folder);
		System.out.println(standardReferences.toString());
		System.out.println("Parsed " + successfulFiles + "/" + (successfulFiles + failedFiles));
	}

	public static void processFolder(Path folder) {
		try {
			Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					try {
						process(file);
						successfulFiles++;
					} catch (Exception e) {
						failedFiles++;
						// ignore this file
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					failedFiles++;
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			// ignore failure
		}
	}

	public static void process(Path path) throws Exception {
		Parser parser = new AutoDetectParser();
		Metadata metadata = new Metadata();
		// The StandardsExtractingContentHandler will examine any characters for
		// standard references before passing them
		// to the underlying Handler.
		StandardsExtractingContentHandler handler = new StandardsExtractingContentHandler(new BodyContentHandler(-1),
				metadata);
		handler.setThreshold(0.75);
		try (InputStream stream = new BufferedInputStream(Files.newInputStream(path))) {
			parser.parse(stream, handler, metadata, new ParseContext());
		}
		String[] references = metadata.getValues(StandardsExtractingContentHandler.STANDARD_REFERENCES);
		Collections.addAll(standardReferences, references);
	}
}