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
package org.apache.tika.langdetect;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.io.IOUtils;

public abstract class LanguageDetectorTest {

    protected String[] getTestLanguages() throws IOException {
    	List<String> result = new ArrayList<>();
    	
    	List<String> lines = IOUtils.readLines(
    	        this.getClass().getResourceAsStream("language-codes.txt"),
                UTF_8);
    	for (String line : lines) {
    		line = line.trim();
    		if (line.isEmpty() || line.startsWith("#")) {
    			continue;
    		}
    		
    		String[] parsed = line.split("\t");
    		String language = parsed[0];
    		if (hasTestLanguage(language)) {
    			result.add(language);
    		}
    	}
    	
    	return result.toArray(new String[result.size()]);
    }
    

    protected boolean hasTestLanguage(String language) {
        InputStream stream = LanguageDetectorTest.class.getResourceAsStream("/language-tests/" + language + ".test");
        if (stream != null) {
        	IOUtils.closeQuietly(stream);
        	return true;
        } else {
        	return false;
        }
    }
    
    protected void writeTo(String language, Writer writer) throws IOException {
    	writeTo(language, writer, Integer.MAX_VALUE);
    }

    protected void writeTo(String language, Writer writer, int limit) throws IOException {
        try (InputStream stream = LanguageDetectorTest.class
                .getResourceAsStream("/language-tests/" + language + ".test")) {
        	copyAtMost(new InputStreamReader(stream, UTF_8), writer, limit);
        }
    }

    protected int copyAtMost(Reader input, Writer output, int limit) throws IOException {
        char[] buffer = new char[4096];
        int count = 0;
        int n = 0;
        
        while ((-1 != (n = input.read(buffer))) && (count < limit)) {
        	int bytesToCopy = Math.min(limit - count, n);
            output.write(buffer, 0, bytesToCopy);
            count += bytesToCopy;
        }
        
        return count;
    }

}
