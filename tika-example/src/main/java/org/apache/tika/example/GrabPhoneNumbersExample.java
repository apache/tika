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
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.PhoneExtractingContentHandler;

/**
 * Class to demonstrate how to use the {@link org.apache.tika.sax.PhoneExtractingContentHandler}
 * to get a list of all of the phone numbers from every file in a directory.
 * <p>
 * You can run this main method by running
 * <code>
 * mvn exec:java -Dexec.mainClass="org.apache.tika.example.GrabPhoneNumbersExample" -Dexec.args="/path/to/directory"
 * </code>
 * from the tika-example directory.
 */
public class GrabPhoneNumbersExample {
    private static HashSet<String> phoneNumbers = new HashSet<>();
    private static int failedFiles, successfulFiles = 0;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage `java GrabPhoneNumbers [corpus]");
            return;
        }
        Path folder = Paths.get(args[0]);
        System.out.println("Searching " + folder.toAbsolutePath() + "...");
        processFolder(folder);
        System.out.println(phoneNumbers.toString());
        System.out.println("Parsed " + successfulFiles + "/" + (successfulFiles + failedFiles));
    }

    public static void processFolder(Path folder) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
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
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
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
        // The PhoneExtractingContentHandler will examine any characters for phone numbers before passing them
        // to the underlying Handler.
        PhoneExtractingContentHandler handler =
                new PhoneExtractingContentHandler(new BodyContentHandler(), metadata);
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(path))) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }
        String[] numbers = metadata.getValues("phonenumbers");
        Collections.addAll(phoneNumbers, numbers);
    }
}
