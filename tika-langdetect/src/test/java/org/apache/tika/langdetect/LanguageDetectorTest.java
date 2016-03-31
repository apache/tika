package org.apache.tika.langdetect;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

public abstract class LanguageDetectorTest {

    protected String[] getTestLanguages() throws IOException {
    	List<String> result = new ArrayList<>();
    	
    	List<String> lines = IOUtils.readLines(LanguageDetectorTest.class.getResourceAsStream("language-codes.txt"),
    	        Charset.forName("UTF-8"));
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
        InputStream stream = LanguageDetectorTest.class.getResourceAsStream("/language-tests/" + language + ".test");
        
        try {
        	copyAtMost(new InputStreamReader(stream, UTF_8), writer, limit);
        } finally {
            stream.close();
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
