package org.apache.tika.parser.ttr;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.io.StringWriter;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.AbstractParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.apache.commons.io.IOUtils;
import org.apache.tika.parser.ttr.TTRExtractor;
import org.apache.tika.sax.XHTMLContentHandler;

public class TTRParser extends AbstractParser {
	
	private static final long serialVersionUID = -466834617644544275L;
	
	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("tag-ratio"));
    
    public Set<MediaType> getSupportedTypes(ParseContext context) {
            return SUPPORTED_TYPES;
    }
    
    public void parse(
                    InputStream stream, ContentHandler handler,
                    Metadata metadata, ParseContext context)
                    throws IOException, SAXException, TikaException {
    	
    	StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        String fileString = writer.toString();
        
        TTRExtractor extractor =  new TTRExtractor();
        
        String[] extracted = extractor.extractText(fileString, 2);
      
        String clean = "";
        
        for(int i=0; i < extracted.length; i++){
        	clean = clean + extractor.removeAllTags(extracted[i]);
        }
        
        metadata.set("trr-extracted", clean);
        
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
    	xhtml.startDocument();
    	xhtml.endDocument();
    }

}
