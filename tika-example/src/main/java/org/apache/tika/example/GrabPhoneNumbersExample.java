package org.apache.tika.example;
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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.PhoneExtractingContentHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;

/**
 * Class to demonstrate how to use the {@link org.apache.tika.sax.PhoneExtractingContentHandler}
 * to get a list of all of the phone numbers from every file in a directory.
 *
 * You can run this main method by running
 * <code>
 *     mvn exec:java -Dexec.mainClass="org.apache.tika.example.GrabPhoneNumbersExample" -Dexec.args="/path/to/directory"
 * </code>
 * from the tika-example directory.
 */
public class GrabPhoneNumbersExample {
    private static HashSet<String> phoneNumbers = new HashSet<String>();
    private static int failedFiles, successfulFiles = 0;

    public static void main(String[] args){
        if (args.length != 1) {
            System.err.println("Usage `java GrabPhoneNumbers [corpus]");
            return;
        }
        final File folder = new File(args[0]);
        System.out.println("Searching " + folder.getAbsolutePath() + "...");
        processFolder(folder);
        System.out.println(phoneNumbers.toString());
        System.out.println("Parsed " + successfulFiles + "/" + (successfulFiles + failedFiles));
    }

    public static void processFolder(final File folder) {
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                processFolder(fileEntry);
            } else {
                try {
                    process(fileEntry);
                    successfulFiles++;
                } catch (Exception e) {
                    failedFiles++;
                    // Ignore this file...
                }
            }
        }
    }

    public static void process(File file) throws Exception {
        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        // The PhoneExtractingContentHandler will examine any characters for phone numbers before passing them
        // to the underlying Handler.
        PhoneExtractingContentHandler handler = new PhoneExtractingContentHandler(new BodyContentHandler(), metadata);
        InputStream stream = new FileInputStream(file);
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        }
        finally {
            stream.close();
        }
        String[] numbers = metadata.getValues("phonenumbers");
        for (String number : numbers) {
            phoneNumbers.add(number);
        }
    }
}
